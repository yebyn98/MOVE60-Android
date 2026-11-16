#!/usr/bin/env python3
"""Send a policy question file to the OpenAI API and append the reply to a log as a DRAFT.

Usage:
  python3 scripts/ask-gpt.py --question docs/gpt-questions/policy-q1.md [--model MODEL] [--log docs/gpt-answers.md]

Rules:
- Only the question file contents are sent. No other repository files, no secrets.
- The API key is never read here: the sandbox network proxy injects the Authorization header.
- Replies are recorded as "확정 후보 (사용자 승인 대기)". Never treat them as approved policy.
"""
import argparse
import datetime
import json
import pathlib
import sys
import urllib.error
import urllib.request

API = "https://api.openai.com/v1/chat/completions"
SYSTEM = (
    "너는 ROUTIVE(구 MOVE60) 운동 앱의 수석 서비스기획자(PO)야. "
    "확정할 수 없는 항목은 추측하지 말고 '사용자 확인 필요'로 표시하고 필요한 질문을 적어. "
    "의학적 처방이나 임의 수치를 만들지 마. 간결한 한국어 표 형식으로 답해."
)


def ask(question: str, model: str) -> str:
    body = json.dumps({
        "model": model,
        "messages": [
            {"role": "system", "content": SYSTEM},
            {"role": "user", "content": question},
        ],
    }).encode()
    req = urllib.request.Request(API, data=body, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=180) as res:
            data = json.load(res)
    except urllib.error.HTTPError as e:
        sys.exit(f"OpenAI API error {e.code}: {e.read().decode()[:500]}")
    return data["choices"][0]["message"]["content"]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--question", required=True)
    ap.add_argument("--model", required=True)
    ap.add_argument("--log", default="docs/gpt-answers.md")
    args = ap.parse_args()

    question = pathlib.Path(args.question).read_text(encoding="utf-8")
    answer = ask(question, args.model)
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    entry = (
        f"\n## {stamp} · {args.question} · 모델 {args.model}\n"
        "상태: **확정 후보 (사용자 승인 대기)** — 구현에 반영하기 전 사용자 승인 필요\n\n"
        f"{answer}\n"
    )
    log = pathlib.Path(args.log)
    log.parent.mkdir(parents=True, exist_ok=True)
    with log.open("a", encoding="utf-8") as f:
        f.write(entry)
    print(f"appended reply to {log}")


if __name__ == "__main__":
    main()
