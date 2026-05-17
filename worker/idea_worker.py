"""Stub idea worker.

Polls the backend for ideas with status='new', posts a single assistant
reply that just acknowledges receipt and asks for clarification, then sets
status to 'needs_info'. This is the placeholder that the Claude Code MCP
worker will replace later — same interface so we can swap implementations
without touching the API.

Run on the pi alongside the backend:

    BACKEND_URL=http://localhost:8000 \
    POLL_INTERVAL=10 \
    python worker/idea_worker.py
"""

from __future__ import annotations

import os
import sys
import time
from typing import Any

import httpx

BACKEND_URL = os.environ.get("BACKEND_URL", "http://localhost:8000").rstrip("/")
POLL_INTERVAL = float(os.environ.get("POLL_INTERVAL", "10"))


def fetch_new_ideas(client: httpx.Client) -> list[dict[str, Any]]:
    res = client.get(f"{BACKEND_URL}/ideas", params={"status": "new"})
    res.raise_for_status()
    return res.json()


def reply_and_mark(client: httpx.Client, idea: dict[str, Any]) -> None:
    transcript = idea.get("transcript", "")
    kind_guess = idea.get("kind", "unknown")
    text = _stub_reply(transcript, kind_guess)

    msg = client.post(
        f"{BACKEND_URL}/ideas/{idea['id']}/messages",
        json={"role": "assistant", "text": text},
        timeout=15,
    )
    msg.raise_for_status()

    client.patch(
        f"{BACKEND_URL}/ideas/{idea['id']}",
        json={"status": "needs_info"},
        timeout=15,
    )


def _stub_reply(transcript: str, kind: str) -> str:
    """Deterministic placeholder. Says we got it + asks one clarifying question."""
    head = transcript.strip().splitlines()[0][:80] if transcript.strip() else "(empty)"
    questions = []
    if kind == "unknown":
        questions.append(
            "Is this a task you want me to add, or a change you want in the app itself?"
        )
    if "task" in kind or kind == "unknown":
        questions.append("When does it apply — one-off date, certain weekdays, or a weekly goal?")
    if not questions:
        questions.append("Anything specific to confirm before I act on this?")
    return (
        f'Got it: "{head}". This is a stub reply (worker not yet wired to Claude).\n\n'
        + "\n".join(f"- {q}" for q in questions)
    )


def loop() -> None:
    with httpx.Client(timeout=15) as client:
        while True:
            try:
                ideas = fetch_new_ideas(client)
                for idea in ideas:
                    try:
                        reply_and_mark(client, idea)
                        print(f"replied to idea {idea['id']}", flush=True)
                    except Exception as e:
                        print(f"failed idea {idea['id']}: {e}", file=sys.stderr, flush=True)
            except Exception as e:
                print(f"poll failed: {e}", file=sys.stderr, flush=True)
            time.sleep(POLL_INTERVAL)


if __name__ == "__main__":
    loop()
