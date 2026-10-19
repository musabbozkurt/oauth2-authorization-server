"""Fix agent: applies small, compile-checked fixes for open review comments and pushes them to the PR branch.

The next review run re-checks the new code: fixed findings are resolved, remaining ones are kept or re-commented.
"""
import json
import os
import subprocess
import time
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate

from review_agent import BOT_LOGINS, build_llm, fetch_bot_threads, load_pull_request

COMMIT_PREFIX = "ai-fix:"
MAX_ROUNDS = 2  # fix commits per PR, prevents endless fix -> review -> fix loops
MAX_FIXES = 5  # fixes per run
SKIP_TOPICS = ("Secret scan", "HardcodedSecret", "Hardcoded")  # secrets must be rotated by a human
CONTEXT = 3
FIX_SCHEMA = {"type": "object", "properties": {"new": {"type": "string"}}, "required": ["new"]}
FIX_PROMPT = ("You fix ONE code review finding in Java code. You get the review comment and the numbered code around "
              "the flagged line. Reply with JSON {{\"new\": \"<replacement for the flagged line only>\"}} containing "
              "the corrected code for that single line, without line numbers or indentation. If the line cannot be "
              "fixed safely on its own, reply {{\"new\": \"\"}}.")


def git(*args, check=True):
    """Run a git command and return its stdout."""
    return subprocess.run(["git", *args], check=check, capture_output=True, text=True).stdout.strip()


def fix_rounds_used():
    """Number of ai-fix commits already on this branch compared to the base."""
    base = os.getenv("GITHUB_BASE_REF", "main")
    log = git("log", f"origin/{base}..HEAD", "--format=%s", check=False)
    return sum(1 for subject in log.splitlines() if subject.startswith(COMMIT_PREFIX))


def fixable_threads(pr, headers):
    """Open, current review threads written by this bot that are worth an automatic fix."""
    threads = []
    for t in fetch_bot_threads(pr, headers):
        comments = t["comments"]["nodes"]
        author = (comments[0]["author"] or {}).get("login") if comments else None
        body = comments[0]["body"] if comments else ""
        if (author in BOT_LOGINS and not t["isResolved"] and not t["isOutdated"] and t.get("line")
                and t["path"].endswith(".java") and not any(topic in body for topic in SKIP_TOPICS)):
            threads.append((t["path"], t["line"], body))
    unique = {(path, line): (path, line, body) for path, line, body in threads}  # one try per line
    return list(unique.values())[:MAX_FIXES]


def propose_line(chain, lines, line, body):
    """Ask the model for a replacement of one line; return it or None."""
    start = max(1, line - CONTEXT)
    window = "\n".join(f"{n}: {lines[n - 1]}" for n in range(start, min(len(lines), line + CONTEXT) + 1))
    raw = chain.invoke({"comment": body[:800], "line": line, "code": window})
    try:
        new = json.loads(raw).get("new", "").rstrip()
    except (ValueError, AttributeError):
        return None
    return new if new.strip() and new.strip() != lines[line - 1].strip() and "\n" not in new else None


def compiles():
    """True if the project still compiles."""
    return subprocess.run(["mvn", "-q", "-B", "-DskipTests", "compile"], capture_output=True).returncode == 0


def apply_fixes(chain, threads, deadline):
    """Apply each proposed fix, keeping only those that compile; return the number kept."""
    kept = 0
    for path, line, body in threads:
        if time.monotonic() > deadline:
            print("Time budget used up; remaining comments are left for a human.")
            break
        with open(path) as f:
            original = f.read()
        lines = original.split("\n")
        if line > len(lines):
            continue
        new = propose_line(chain, lines, line, body)
        if not new:
            print(f"{path}:{line}: no safe fix proposed.")
            continue
        indent = lines[line - 1][:len(lines[line - 1]) - len(lines[line - 1].lstrip())]
        lines[line - 1] = indent + new.strip()
        with open(path, "w") as f:
            f.write("\n".join(lines))
        if compiles():
            print(f"{path}:{line}: fixed.")
            kept += 1
        else:
            print(f"{path}:{line}: fix does not compile; reverted.")
            with open(path, "w") as f:
                f.write(original)
    return kept


def main():
    """Fix open review comments, commit and push so the next pipeline run can re-check them."""
    token = os.getenv("GITHUB_TOKEN")
    pr = load_pull_request(token)
    if not pr:
        return
    if fix_rounds_used() >= MAX_ROUNDS:
        print(f"Already {MAX_ROUNDS} fix rounds on this PR; leaving the rest to a human.")
        return
    headers = {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json"}
    threads = fixable_threads(pr, headers)
    if not threads:
        print("No fixable review comments.")
        return

    openai_key = os.getenv("OPENAI_API_KEY")
    chain = ChatPromptTemplate.from_messages([
        ("system", FIX_PROMPT),
        ("user", "Review comment:\n{comment}\n\nFlagged line: {line}\n{code}"),
    ]) | build_llm(openai_key, schema=FIX_SCHEMA) | StrOutputParser()
    kept = apply_fixes(chain, threads, time.monotonic() + float(os.getenv("FIX_TIME_BUDGET", "120")))
    if not kept:
        print("No fixes kept.")
        return

    git("config", "user.name", "ai-review-bot[bot]")
    git("config", "user.email", "ai-review-bot[bot]@users.noreply.github.com")
    git("commit", "-am", f"{COMMIT_PREFIX} apply {kept} automatic fix(es) for review comments")
    git("push", "origin", f"HEAD:{pr['head']['ref']}")
    print(f"Pushed {kept} fix(es); the next review run will re-check them.")


if __name__ == "__main__":
    main()
