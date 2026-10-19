import json
import os
import re
import requests
import sys
import time
import xml.etree.ElementTree as ET
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_ollama import ChatOllama
from langchain_openai import ChatOpenAI

# Doubled braces are literal JSON braces (escaped for ChatPromptTemplate)
SYSTEM_PROMPT = """You are a senior software architect and strict-but-constructive code reviewer for a Java / Spring Boot \
OAuth2 authorization server (Spring Security, Spring Authorization Server, JPA/Hibernate, microservice-oriented).

You receive part of a pull request diff. Every added or context line is prefixed with its line number in the new file \
as `L<number>:`; removed lines start with `-` and have no number. Only comment on ADDED lines (`L<n>: +...`).

Review ONLY what changed, against these criteria:
1. SOLID - SRP (classes/methods doing too much), OCP (if/switch chains that should be polymorphism/strategy), \
LSP, ISP (fat interfaces), DIP (depending on concrete classes instead of abstractions, `new` of collaborators instead of injection).
2. KISS / YAGNI - needless complexity, over-engineering, speculative generality.
3. DRY - duplicated logic, copy-pasted blocks, magic numbers/strings that should be constants or configuration.
4. Design patterns - missing or misapplied patterns (Strategy, Factory, Builder, Template Method, Adapter, Decorator, \
Observer), anti-patterns (God class, anemic domain misuse, service locator, singleton abuse).
5. Layered architecture - controller -> service -> repository separation; no business logic in controllers, no entities \
exposed directly in API responses (use DTOs/mappers), no repository access from controllers, no web types in services.
6. Microservice architecture - service boundaries, tight coupling between services, missing timeouts/retries/circuit breakers \
on remote calls, idempotency, distributed transactions, configuration externalisation, statelessness, API versioning.
7. Spring/Java correctness & performance - constructor injection over field injection, @Transactional scope, \
N+1 queries, missing indexes, blocking calls in reactive code, resource leaks, thread safety, Optional misuse, exception handling.
8. Security - injection, secrets in code, CORS/CSRF misconfiguration, missing authorization checks, weak token/PKCE/redirect-URI \
handling, logging sensitive data, missing input validation (@Valid).
9. SonarQube-style code smells and bugs - cognitive complexity above 15, duplicated string literals, unused imports/variables/private \
methods, empty catch blocks, `System.out`/`printStackTrace` instead of a logger, generic exceptions, nested ternaries, too many parameters, \
commented-out code, mutable static fields, resources not closed (try-with-resources), `equals` on possibly-null values, and unnecessary boxing.
10. Readability and testability - naming, method length, missing tests for new logic.

Rules:
- Report only real, specific, actionable problems or concrete improvements. No praise, no generic advice, no nitpicks on formatting.
- Each comment must explain the problem, which principle/pattern it violates, and give a concrete fix; \
include a short corrected code snippet when helpful.
- If the change is fine, return an empty comments list.
- Never invent file paths or line numbers; use only the ones shown.

Respond with ONLY a JSON object, no markdown fences, in this exact shape:
{{"summary": "1-3 sentence overall assessment of this part of the diff",
  "comments": [{{"path": "path/in/repo.java", "line": 42, "code": "exact added line copied without the L<n>: + prefix", "severity": "high|medium|low", "body": "markdown comment"}}]}}"""

# Files not worth reviewing: lockfiles, binaries, docs
# Short prompt for small local models, which struggle with the long one
SMALL_PROMPT = """You are a strict senior Java/Spring Boot code reviewer. Review the diff below.
Lines look like `L<number>: +code` (added) or `-code` (removed). Comment ONLY on added lines and use their exact path and line number.

Look for real problems in these areas:
- SOLID, KISS, DRY violations (god classes/methods, long if/else chains, duplicated code, magic numbers)
- Design patterns that should be used (Strategy, Factory, Builder) and layered-architecture violations \
(business logic or JDBC/repositories in controllers, entities returned from APIs, field injection)
- Security (SQL injection, hardcoded secrets, path traversal, missing validation/authorization, logging secrets)
- Bugs and performance (resource leaks, swallowed exceptions, N+1 calls, shared mutable state, unbounded caches)

Each comment needs: the problem, why it matters, and a concrete fix. If you see problems you MUST report them; \
return an empty comments list only when the code is truly clean.
Reply as JSON: summary (string) and comments (list of path, line, code, severity high|medium|low, body). `code` is the exact added line you are commenting on, copied without the L<n>: +  prefix."""

# JSON schema enforced on Ollama so small models always return a parseable structure
REVIEW_SCHEMA = {
    "type": "object",
    "properties": {
        "summary": {"type": "string"},
        "comments": {"type": "array", "items": {
            "type": "object",
            "properties": {
                "path": {"type": "string"},
                "line": {"type": "integer"},
                "code": {"type": "string"},
                "severity": {"type": "string", "enum": ["high", "medium", "low"]},
                "body": {"type": "string"},
            },
            "required": ["path", "line", "code", "severity", "body"],
        }},
    },
    "required": ["summary", "comments"],
}

SKIP_FILE = re.compile(
    r"(\.lock$|package-lock\.json$|\.(png|jpe?g|gif|svg|ico|jar|class|pdf|zip)$|(^|/)docs/|\.md$)")
# Hunk header; group 1 is the start line in the new file
HUNK = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@")


def annotate_line(raw, new_line):
    """Return (annotated_text or None, new_line_increment, is_added) for a diff body line."""
    if raw.startswith("+"):
        return f"L{new_line}: {raw}", 1, True
    if raw.startswith("-"):
        return raw, 0, False
    if raw.startswith("\\"):
        return None, 0, False
    return f"L{new_line}:  {raw[1:]}", 1, False


def annotate_block(block):
    """Return (annotated_lines, added_line_numbers) for one file's diff block."""
    out, added, new_line, in_hunk = [], {}, 0, False
    for raw in block.splitlines():
        hunk = HUNK.match(raw)
        if hunk:
            new_line, in_hunk = int(hunk.group(1)), True
            out.append(raw)
            continue
        if not in_hunk:
            continue
        text, step, is_added = annotate_line(raw, new_line)
        if text is not None:
            out.append(text)
        if is_added:
            added[new_line] = raw[1:].strip()
        new_line += step
    return out, added


def parse_block(block):
    """Return (path, annotated_text, added_lines) for a reviewable file block, else None."""
    match = re.search(r"(?m)^\+\+\+ b/(.+)$", block)
    if not match or SKIP_FILE.search(match.group(1)):
        return None
    path = match.group(1)
    out, added = annotate_block(block)
    if not added:
        return None
    return path, "### File: " + path + "\n" + "\n".join(out), added


def parse_diff(diff):
    """Return {path: (annotated_text, {added_line_numbers})} for reviewable files."""
    files = {}
    for block in re.split(r"(?m)^diff --git ", diff):
        parsed = parse_block(block) if block.strip() else None
        if parsed:
            files[parsed[0]] = (parsed[1], parsed[2])
    return files


def chunk_files(files, chunk_chars, max_chunks):
    """Group per-file diffs into chunks of at most chunk_chars; return (first max_chunks chunks, number skipped)."""
    chunks, current, size = [], [], 0
    for path, (text, _) in files.items():
        text = text[:chunk_chars]
        if current and size + len(text) > chunk_chars:
            chunks.append(current)
            current, size = [], 0
        current.append(text)
        size += len(text)
    if current:
        chunks.append(current)
    return chunks[:max_chunks], max(0, len(chunks) - max_chunks)


def parse_json(text):
    """Parse the model output as JSON, falling back to the first {...} block; None if invalid."""
    try:
        return json.loads(text)
    except ValueError:
        m = re.search(r"\{.*\}", text, re.S)
        if m:
            try:
                return json.loads(m.group(0))
            except ValueError:
                pass
    return None


def build_llm(openai_key, json_mode=True, schema=None):
    """Return the OpenAI model if a key is set, otherwise the local Ollama model; JSON mode for reviews, plain text otherwise."""
    if openai_key:
        print("Using OpenAI.")
        llm = ChatOpenAI(model=os.getenv("OPENAI_MODEL", "gpt-4o"), temperature=0.2, api_key=openai_key, timeout=120)
        return llm.bind(response_format={"type": "json_object"}) if json_mode else llm
    model = os.getenv("OLLAMA_MODEL", "qwen2.5-coder:3b")
    host = os.getenv("OLLAMA_HOST", "http://localhost:11434")
    print(f"OPENAI_API_KEY not set; using Ollama model {model} at {host}.", flush=True)
    requests.get(f"{host}/api/tags", timeout=10).raise_for_status()  # fail fast if the server is down
    return ChatOllama(model=model, temperature=0.2, num_ctx=8192, num_predict=700 if json_mode else 60,
                      format=(schema or REVIEW_SCHEMA) if json_mode else None, base_url=host,
                      client_kwargs={"timeout": 120})


def snap_line(files, path, line, code):
    """Models often miscount lines: if the quoted code matches an added line, use the nearest such line."""
    matches = [n for n, text in files.get(path, ("", {}))[1].items() if code and text == code]
    return min(matches, key=lambda n: abs(n - line)) if matches else line


def to_comment(c, files):
    """Normalise a model comment; return (inline_dict or None, unplaced_text or None, key) or None if invalid."""
    try:
        path, line, body = c["path"], int(c["line"]), str(c["body"]).strip()
    except (KeyError, TypeError, ValueError):
        return None
    if not body:
        return None
    line = snap_line(files, path, line, str(c.get("code", "")).strip())
    sev = str(c.get("severity", "")).lower()
    key = (path, line, body)
    if sev in ("high", "medium", "low"):
        body = f"**[{sev}]** {body}"
    if path in files and line in files[path][1]:
        return {"path": path, "line": line, "side": "RIGHT", "body": body}, None, key
    return None, f"- `{path}`: {body}", key


def collect_comments(data, files, seen):
    """Split a model response into (inline, unplaced) comments, skipping invalid and duplicate ones."""
    inline, unplaced = [], []
    for c in data.get("comments") or []:
        result = to_comment(c, files)
        if not result or result[2] in seen:
            continue
        seen.add(result[2])
        if result[0]:
            inline.append(result[0])
        else:
            unplaced.append(result[1])
    return inline, unplaced


def review_chunks(chain, chunks, files, seen, deadline):
    """Run the model on each chunk until the deadline; return (summaries, inline, unplaced, chunks not reviewed)."""
    summaries, inline, unplaced = [], [], []
    for index, chunk in enumerate(chunks, 1):
        if time.monotonic() > deadline:
            print("Time budget used up; remaining chunks are not reviewed by the model.")
            return summaries, inline, unplaced, len(chunks) - index + 1
        print(f"Reviewing chunk {index}/{len(chunks)}...", flush=True)
        try:
            output = chain.invoke({"diff": "\n\n".join(chunk)})
        except Exception as e:  # a slow or failing chunk must not block the whole review
            print(f"Chunk {index} failed: {e}")
            continue
        data = parse_json(output)
        print(f"Chunk {index} raw output: {str(output)[:300]!r}", flush=True)
        if not isinstance(data, dict):
            print("Model returned unparseable output for a chunk; skipping it.")
            continue
        if data.get("summary"):
            summaries.append(str(data["summary"]))
        chunk_inline, chunk_unplaced = collect_comments(data, files, seen)
        inline += chunk_inline
        unplaced += chunk_unplaced
    return summaries, inline, unplaced, 0


def fetch_sonar_comments(pr, files, seen):
    """Return inline comments for open Sonar issues on changed lines; empty if Sonar is not configured or fails."""
    token, project = os.getenv("SONAR_TOKEN"), os.getenv("SONAR_PROJECT_KEY")
    if not token or not project:
        return []
    host = (os.getenv("SONAR_HOST_URL") or "https://sonarcloud.io").rstrip("/")
    try:
        response = requests.get(f"{host}/api/issues/search", auth=(token, ""), timeout=60, params={
            "componentKeys": project, "pullRequest": pr["number"], "resolved": "false", "ps": 500})
        response.raise_for_status()
        issues = response.json().get("issues", [])
    except (requests.RequestException, ValueError) as e:
        print(f"Could not fetch Sonar issues: {e}")
        return []
    comments = []
    for issue in issues:
        path, line = issue["component"].split(":", 1)[-1], issue.get("line")
        key = (path, line, issue["message"])
        if path not in files or line not in files[path][1] or key in seen:
            continue
        seen.add(key)
        body = f"**[Sonar {issue.get('severity', 'INFO').lower()}]** {issue['message']} (`{issue.get('rule')}`)"
        comments.append({"path": path, "line": line, "side": "RIGHT", "body": body})
    print(f"Added {len(comments)} Sonar issue comment(s).")
    return comments


def report_path(env_name):
    """Return the report file path from the environment variable if the file exists, else None."""
    path = os.getenv(env_name)
    return path if path and os.path.exists(path) else None


def line_comments(findings, files, seen, label):
    """Turn (path, line, message, rule) findings on changed lines into one inline comment per line."""
    by_line = {}
    for path, line, message, rule in findings:
        if path in files and line in files[path][1]:
            by_line.setdefault((path, line), {})[message] = rule
    comments = []
    for (path, line), messages in by_line.items():
        body = f"**[{label}]**\n" + "\n".join(f"- {m} (`{r}`)" for m, r in messages.items())
        seen.add((path, line, body))
        comments.append({"path": path, "line": line, "side": "RIGHT", "body": body})
    print(f"Added {len(comments)} {label} comment(s).")
    return comments


def load_pmd_comments(files, seen):
    """Return inline comments for PMD (Sonar-like) violations on changed lines."""
    report = report_path("PMD_REPORT")
    if not report:
        return []
    with open(report, "r") as f:
        data = json.load(f)
    findings = [(os.path.relpath(fr["filename"]).replace(os.sep, "/"), v["beginline"], v["description"], v["rule"])
                for fr in data.get("files", []) for v in fr.get("violations", [])]
    return line_comments(findings, files, seen, "Static analysis")


# Constructor-injected Spring beans and endpoints are expected, not exposure bugs
NOISY_BUG_TYPES = ("SPRING_ENDPOINT", "EI_EXPOSE_REP", "EI_EXPOSE_REP2")


def load_spotbugs_comments(files, seen):
    """Return inline comments for SpotBugs/FindSecBugs findings (bytecode and security analysis)."""
    report = report_path("SPOTBUGS_REPORT")
    if not report:
        return []
    findings = []
    for bug in ET.parse(report).getroot().findall("BugInstance"):
        source = bug.find(".//SourceLine[@primary='true']")
        if source is None or bug.get("type") in NOISY_BUG_TYPES or int(bug.get("priority", 3)) > 2:
            continue
        path = next((p for p in files if p.endswith("/" + source.get("sourcepath"))), None)
        if path and source.get("start"):
            findings.append((path, int(source.get("start")), bug.findtext("LongMessage"), bug.get("type")))
    return line_comments(findings, files, seen, "Security/bug analysis")


def load_gitleaks_comments(files, seen):
    """Return inline comments for secrets detected by Gitleaks (secret values are redacted)."""
    report = report_path("GITLEAKS_REPORT")
    if not report:
        return []
    with open(report, "r") as f:
        leaks = json.load(f) or []
    findings = [(os.path.relpath(x["File"]).replace(os.sep, "/"), x["StartLine"],
                 f"Possible secret committed: {x['Description']}. Remove it, rotate it and load it from a secret store",
                 x["RuleID"]) for x in leaks]
    return line_comments(findings, files, seen, "Secret scan")


def build_summary(summaries, unplaced, skipped):
    """Build the markdown review body from summaries, unplaced notes and the skipped-chunk count."""
    summary = "## 🤖 AI Code Review\n\n" + "\n\n".join(summaries)
    if skipped:
        summary += f"\n\n_{skipped} diff chunk(s) were not reviewed by the model due to size or time limits._"
    if unplaced:
        summary += "\n\n### Additional notes\n" + "\n".join(unplaced)
    return summary


def post_review(pr, api_headers, summary, inline):
    """Post a PR review with inline comments; fall back to a plain PR comment if GitHub rejects it."""
    review = requests.post(f"{pr['url']}/reviews", headers=api_headers, timeout=60, json={
        "commit_id": pr["head"]["sha"], "body": summary, "event": "COMMENT", "comments": inline})
    if review.status_code == 200:
        print(f"Posted review with {len(inline)} inline comment(s).")
        return

    print(f"Inline review failed ({review.status_code}): {review.text}; falling back to a plain comment.")
    fallback = summary + "\n\n### Inline suggestions\n" + "\n".join(
        f"- `{c['path']}` line {c['line']}: {c['body']}" for c in inline)
    post = requests.post(pr["comments_url"], headers=api_headers, json={"body": fallback}, timeout=60)
    if post.status_code != 201:
        print(f"Failed to post comment: {post.status_code} {post.text}")
        sys.exit(1)
    print("Posted fallback PR comment.")


GRAPHQL_URL = "https://api.github.com/graphql"
BOT_LOGINS = ("github-actions", "github-actions[bot]")
THREADS_QUERY = """query($owner: String!, $name: String!, $number: Int!) {
  repository(owner: $owner, name: $name) { pullRequest(number: $number) { reviewThreads(first: 100) { nodes {
    id isResolved isOutdated path line originalLine
    comments(first: 1) { nodes { body author { login } } }
  } } } }
}"""
REPLY_MUTATION = """mutation($id: ID!, $body: String!) {
  addPullRequestReviewThreadReply(input: {pullRequestReviewThreadId: $id, body: $body}) { comment { id } }
}"""
RESOLVE_MUTATION = "mutation($id: ID!) { resolveReviewThread(input: {threadId: $id}) { thread { id } } }"
DEFAULT_REASON = "The code this comment referred to has changed, so it no longer applies."
RESOLVE_PROMPT = ("The code a review comment pointed at was changed or removed. In ONE short sentence (max 20 words) "
                  "say the comment no longer applies because that code changed, naming its topic. No preamble.")


def graphql(headers, query, variables):
    """Run a GitHub GraphQL request and return its data; raise on HTTP or GraphQL errors."""
    response = requests.post(GRAPHQL_URL, headers=headers, json={"query": query, "variables": variables}, timeout=60)
    response.raise_for_status()
    body = response.json()
    if body.get("errors"):
        raise RuntimeError(body["errors"])
    return body["data"]


def is_stale_bot_thread(thread):
    """True for open threads started by this bot whose code has since changed (GitHub marks them outdated)."""
    comments = thread["comments"]["nodes"]
    author = (comments[0]["author"] or {}).get("login") if comments else None
    return thread["isOutdated"] and not thread["isResolved"] and author in BOT_LOGINS


def rule_ids(body):
    """Rule names such as (`CloseResource`) quoted in a bot comment body."""
    return set(re.findall(r"\(`(\w+)`\)", body))


def still_reported(thread, current):
    """True if a comment in the fresh review repeats this thread's finding near the same place (the fix did not work)."""
    old = thread["comments"]["nodes"][0]["body"]
    near = thread.get("originalLine") or 0
    return any(c["path"] == thread["path"] and abs(c["line"] - near) <= 10
               and (c["body"] == old or rule_ids(old) & rule_ids(c["body"])) for c in current)


def fetch_bot_threads(pr, headers):
    """Return all review threads of the PR (raises on failure)."""
    base = pr["base"]["repo"]
    data = graphql(headers, THREADS_QUERY,
                   {"owner": base["owner"]["login"], "name": base["name"], "number": pr["number"]})
    return data["repository"]["pullRequest"]["reviewThreads"]["nodes"]


def resolve_stale_threads(pr, headers, resolve_headers, llm, notes_deadline, current):
    """Resolve this bot's outdated threads whose finding is gone and add a one-line model-written note to each.

    Threads whose finding is still reported in `current` stay open: the fresh review already comments on the new code.

    Resolving needs a personal access token: the default GITHUB_TOKEN is not allowed to call resolveReviewThread.
    """
    try:
        threads = [t for t in fetch_bot_threads(pr, headers) if
                   is_stale_bot_thread(t) and not still_reported(t, current)]
    except (requests.RequestException, RuntimeError, KeyError) as e:
        print(f"Could not list review threads: {e}")
        return
    chain = ChatPromptTemplate.from_messages(
        [("system", RESOLVE_PROMPT), ("user", "{comment}")]) | llm | StrOutputParser()
    for thread in threads:
        try:
            graphql(resolve_headers, RESOLVE_MUTATION, {"id": thread["id"]})
        except (requests.RequestException, RuntimeError) as e:
            print(
                f"Could not resolve threads (configure the APP_ID variable and APP_PRIVATE_KEY secret for an app with pull-requests write access): {e}")
            return
        try:
            reason = DEFAULT_REASON
            if time.monotonic() < notes_deadline:  # past the budget, use the generic note to stay fast
                note = chain.invoke({"comment": thread["comments"]["nodes"][0]["body"][:1000]}).strip().splitlines()
                reason = note[0][:200] if note else reason
            graphql(headers, REPLY_MUTATION, {"id": thread["id"], "body": f"✅ Resolved: {reason}"})
        except Exception as e:  # the thread is already resolved; a missing note is not fatal
            print(f"Could not add the note to thread {thread['id']}: {e}")
    print(f"Processed {len(threads)} stale thread(s).")


def load_pull_request(token):
    """Return the pull_request payload from the Actions event file, or None if unavailable."""
    event_path = os.getenv("GITHUB_EVENT_PATH")
    if not event_path or not os.path.exists(event_path):
        print("Not running inside a valid GitHub Actions PR event context.")
        return None
    if not token:
        print("GITHUB_TOKEN is missing; skipping review.")
        return None
    with open(event_path, "r") as f:
        return json.load(f)["pull_request"]


def main():
    """Review the PR diff with an LLM and post the findings back to the pull request."""
    token = os.getenv("GITHUB_TOKEN")
    openai_key = os.getenv("OPENAI_API_KEY")
    pr = load_pull_request(token)
    if not pr:
        return

    api_headers = {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json"}
    resolve_headers = {**api_headers, "Authorization": f"Bearer {os.getenv('RESOLVE_TOKEN') or token}"}
    started = time.monotonic()
    budget = float(os.getenv("REVIEW_TIME_BUDGET", "150"))  # seconds the model may work in total
    diff_response = requests.get(pr["url"], headers={**api_headers, "Accept": "application/vnd.github.v3.diff"},
                                 timeout=60)
    diff_response.raise_for_status()
    files = parse_diff(diff_response.text)
    if not files:
        print("No reviewable changes found.")
        return

    chunks, skipped = chunk_files(files, 100_000 if openai_key else 5_000, 5 if openai_key else 2)
    chain = ChatPromptTemplate.from_messages([
        ("system", SYSTEM_PROMPT if openai_key else SMALL_PROMPT),
        ("user", "Review this part of the PR diff:\n\n{diff}"),
    ]) | build_llm(openai_key) | StrOutputParser()

    seen = set()
    inline = (load_gitleaks_comments(files, seen) + load_spotbugs_comments(files, seen)
              + load_pmd_comments(files, seen) + fetch_sonar_comments(pr, files, seen))
    summaries, ai_inline, unplaced, not_reviewed = review_chunks(chain, chunks, files, seen, started + budget)
    skipped += not_reviewed
    inline += ai_inline
    if inline or unplaced:
        post_review(pr, api_headers, build_summary(summaries, unplaced, skipped), inline)
    else:
        print("No issues found by the AI reviewer; not posting a review.")
    resolve_stale_threads(pr, api_headers, resolve_headers, build_llm(openai_key, json_mode=False),
                          time.monotonic() + budget * 0.25, inline)


if __name__ == "__main__":
    main()
