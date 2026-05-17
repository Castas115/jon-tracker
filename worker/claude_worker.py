"""Headless Claude worker.

Polls the backend for ideas that need attention and hands each one off to
the `claude` CLI with our MCP server registered. Claude reads the thread
context, classifies the idea, asks clarifying questions, or — when it has
enough information — creates the actual task via the MCP tools.

Two cases get processed:
  - status == "new"                  → first-pass triage
  - status == "needs_info" and the last message in the thread is from the
    user                             → follow-up after the user replied

Requires ANTHROPIC_API_KEY in the environment.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import time
from pathlib import Path
from typing import Any

import httpx

BACKEND_URL = os.environ.get("BACKEND_URL", "http://localhost:8000").rstrip("/")
POLL_INTERVAL = float(os.environ.get("POLL_INTERVAL", "10"))
CLAUDE_MODEL = os.environ.get("CLAUDE_MODEL", "claude-haiku-4-5-20251001")
CLAUDE_TIMEOUT = float(os.environ.get("CLAUDE_TIMEOUT", "180"))
WORKER_DIR = Path(__file__).resolve().parent
MCP_CONFIG_PATH = Path("/tmp/tracker_mcp.json")

ALLOWED_TOOLS = ",".join(
    f"mcp__tracker__{name}"
    for name in (
        "list_ideas",
        "get_idea",
        "post_idea_message",
        "set_idea",
        "delete_idea",
        "list_tasks",
        "create_task",
        "update_task",
        "delete_task",
        "toggle_task_completion",
    )
)

SYSTEM_PROMPT = """You are the assistant inside Jon's task tracker.

Each turn, you receive ONE idea (the user's captured note) and the full
message thread for it. Your job:

1. Read context with the mcp__tracker__* tools. Always call `list_tasks`
   first to see what already exists, and `get_idea` to load the thread.
2. Decide what kind it is:
   - "task"    → something Jon wants to do (one-off, recurring, weekly_goal, birthday).
   - "feature" → a change Jon wants in this app itself.
   - "unknown" → not clear yet.
3. If it's an actionable TASK and you have enough info:
     • call `create_task` with the right schema (read the schema docs in
       the tool descriptions carefully — weekly_goal needs target_per_week
       or target_segments, recurring needs weekdays, etc.)
     • call `set_idea` with status=done and linked_task_id=<new task id>
     • call `post_idea_message` with a short confirmation in the user's
       language (mirror what they used — usually Spanish).
4. If it's a FEATURE, leave it for later:
     • call `set_idea` with kind=feature and status=in_progress (so it
       stays visible in Inbox as something Jon wants).
     • call `post_idea_message` acknowledging it and asking any
       clarifying questions you need to scope it.
5. If ANYTHING is unclear (date, frequency, target count, what they meant):
     • call `post_idea_message` asking ONE focused question — not three.
     • call `set_idea` with status=needs_info.

Rules:
- NEVER delete tasks or ideas without an explicit user instruction.
- Mirror the user's language (Spanish or English) in `post_idea_message`.
- Keep replies short (1–3 sentences). Don't summarise back what they
  already said.
- Today's date is provided in the user message — use it when interpreting
  relative dates like "mañana" or "viernes".

You only get one chance per idea. Finish with the right state before
returning.
"""


def fetch_pending(client: httpx.Client) -> list[dict[str, Any]]:
    """Ideas that need the assistant's attention."""
    r = client.get(f"{BACKEND_URL}/ideas")
    r.raise_for_status()
    out: list[dict[str, Any]] = []
    for idea in r.json():
        if idea["status"] == "new":
            out.append(idea)
            continue
        if idea["status"] == "needs_info":
            msgs = idea.get("messages") or []
            if msgs and msgs[-1]["role"] == "user":
                out.append(idea)
    return out


def ensure_mcp_config() -> Path:
    """Write the MCP config Claude will load. Idempotent."""
    cfg = {
        "mcpServers": {
            "tracker": {
                "command": "python",
                "args": [str(WORKER_DIR / "mcp_server.py")],
                "env": {"BACKEND_URL": BACKEND_URL},
            }
        }
    }
    MCP_CONFIG_PATH.write_text(json.dumps(cfg))
    return MCP_CONFIG_PATH


def build_prompt(idea: dict[str, Any]) -> str:
    today = time.strftime("%Y-%m-%d")
    return (
        f"Today is {today}.\n"
        f"Process idea id={idea['id']} (current status={idea['status']}, "
        f"kind hint={idea['kind']}).\n"
        f"Start by calling get_idea({idea['id']}) to load the full thread, "
        f"then act per the system instructions."
    )


def run_claude(idea: dict[str, Any], config_path: Path) -> None:
    cmd = [
        "claude",
        "--print",
        "--mcp-config",
        str(config_path),
        "--allowed-tools",
        ALLOWED_TOOLS,
        "--append-system-prompt",
        SYSTEM_PROMPT,
        "--model",
        CLAUDE_MODEL,
        "--output-format",
        "text",
        build_prompt(idea),
    ]
    result = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        timeout=CLAUDE_TIMEOUT,
        env={**os.environ},
    )
    if result.returncode != 0:
        print(
            f"claude exited {result.returncode} for idea {idea['id']}: {result.stderr[:500]}",
            file=sys.stderr,
            flush=True,
        )
    else:
        print(
            f"idea {idea['id']} processed. summary: {result.stdout.strip()[:200]}",
            flush=True,
        )


def loop() -> None:
    if not os.environ.get("ANTHROPIC_API_KEY"):
        print("ANTHROPIC_API_KEY missing — worker will idle.", file=sys.stderr, flush=True)
    config_path = ensure_mcp_config()
    with httpx.Client(timeout=20) as client:
        while True:
            try:
                pending = fetch_pending(client)
                for idea in pending:
                    try:
                        run_claude(idea, config_path)
                    except subprocess.TimeoutExpired:
                        print(
                            f"claude timed out for idea {idea['id']}",
                            file=sys.stderr,
                            flush=True,
                        )
                    except Exception as e:  # noqa: BLE001
                        print(
                            f"unhandled error on idea {idea['id']}: {e}",
                            file=sys.stderr,
                            flush=True,
                        )
            except Exception as e:  # noqa: BLE001
                print(f"poll failed: {e}", file=sys.stderr, flush=True)
            time.sleep(POLL_INTERVAL)


if __name__ == "__main__":
    loop()
