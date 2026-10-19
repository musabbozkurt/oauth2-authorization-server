import json
import os
import requests
import sys
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_ollama import ChatOllama
from langchain_openai import ChatOpenAI


def main():
    # 1. Grab environment variables provided by GitHub Actions
    token = os.getenv("GITHUB_TOKEN")
    openai_key = os.getenv("OPENAI_API_KEY")
    ollama_model = os.getenv("OLLAMA_MODEL", "qwen2.5-coder:1.5b")
    event_path = os.getenv("GITHUB_EVENT_PATH")

    if not event_path or not os.path.exists(event_path):
        print("Not running inside a valid GitHub Actions PR event context.")
        return

    # 2. Parse the PR event payload to find metadata
    with open(event_path, 'r') as f:
        event_data = json.load(f)

    if not token:
        print("GITHUB_TOKEN is missing; skipping review.")
        return

    pr = event_data["pull_request"]
    # API URL works for private repos, unlike the github.com diff_url
    pr_api_url = pr["url"]
    comments_url = pr["comments_url"]  # issue-comments endpoint (general PR comment)

    # 3. Pull the PR's raw Git diff
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github.v3.diff"
    }
    diff_response = requests.get(pr_api_url, headers=headers, timeout=60)
    diff_response.raise_for_status()
    pr_diff = diff_response.text

    if not pr_diff.strip():
        print("No diff found or PR is empty.")
        return

    max_chars = 200_000 if openai_key else 30_000  # Ollama's num_ctx below limits the local model
    if len(pr_diff) > max_chars:
        pr_diff = pr_diff[:max_chars] + "\n\n[diff truncated]"

    # 4. Invoke the LangChain review workflow
    # Using low temperature for deterministic, analysis-focused code evaluation
    if openai_key:
        print("Using OpenAI.")
        llm = ChatOpenAI(model=os.getenv("OPENAI_MODEL", "gpt-4o"), temperature=0.2,
                         api_key=openai_key, timeout=120)
    else:
        print(f"OPENAI_API_KEY not set; using Ollama model {ollama_model}.")
        llm = ChatOllama(model=ollama_model, temperature=0.2, num_ctx=16384,
                         base_url=os.getenv("OLLAMA_HOST", "http://localhost:11434"),
                         client_kwargs={"timeout": 1200})

    prompt = ChatPromptTemplate.from_messages([
        ("system", (
            "You are an expert, risk-aware Senior Software Architect specializing in Spring Boot, Java, and ecosystem best practices.\n\n"
            "Review the following code diff for:\n"
            "1. Architectural flaws (e.g., proper use of @RestController, @Service, @Repository annotations, handling transactions with @Transactional, thread safety).\n"
            "2. Java/Spring Boot performance gotchas (e.g., N+1 queries in JPA/Hibernate, missing indexes, unclosed resource streams, blocking operations in WebFlux).\n"
            "3. Security holes (e.g., SQL injection, exposed secrets/tokens, improper CORS config, broken endpoint authorization via Spring Security).\n"
            "4. Code quality & styling issues (e.g., lack of proper validation like @Valid/@NotNull, hardcoded configs that should be in application.yml, poor exception handling).\n\n"
            "Provide an actionable Markdown summary with explicit line/code examples where appropriate. Focus heavily on Spring-specific patterns."
        )),
        ("user", "Review this PR diff for my Spring Boot application:\n\n{diff}")
    ])

    chain = prompt | llm | StrOutputParser()
    review_output = chain.invoke({"diff": pr_diff})

    # 5. Post the review as a comment back onto the GitHub PR
    payload = {"body": review_output}
    post_response = requests.post(comments_url, json=payload, headers={"Authorization": f"Bearer {token}",
                                                                       "Accept": "application/vnd.github+json"},
                                  timeout=60)

    if post_response.status_code == 201:
        print("Successfully posted AI Spring Boot review to the Pull Request!")
    else:
        print(f"Failed to post comment: {post_response.status_code} {post_response.text}")
        sys.exit(1)


if __name__ == "__main__":
    main()
