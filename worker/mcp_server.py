"""MCP server exposing the jon-tracker backend HTTP API as tools.

Run by the headless Claude worker as a stdio subprocess; never exposed
beyond the worker container. Every tool is a thin wrapper over the
existing FastAPI endpoints, so the worker doesn't get any extra reach
than a regular HTTP client would.
"""

from __future__ import annotations

import os
from typing import Any

import httpx
from mcp.server.fastmcp import FastMCP

BACKEND_URL = os.environ.get("BACKEND_URL", "http://backend:8000").rstrip("/")

mcp = FastMCP("tracker")


def _get(path: str, **params: Any) -> Any:
    clean = {k: v for k, v in params.items() if v is not None}
    r = httpx.get(f"{BACKEND_URL}{path}", params=clean, timeout=15)
    r.raise_for_status()
    return r.json()


def _post(path: str, payload: dict | None = None) -> Any:
    r = httpx.post(f"{BACKEND_URL}{path}", json=payload, timeout=30)
    r.raise_for_status()
    return r.json()


def _patch(path: str, payload: dict) -> Any:
    r = httpx.patch(f"{BACKEND_URL}{path}", json=payload, timeout=15)
    r.raise_for_status()
    return r.json()


def _delete(path: str) -> None:
    r = httpx.delete(f"{BACKEND_URL}{path}", timeout=15)
    r.raise_for_status()


# ---- ideas ----


@mcp.tool()
def list_ideas(status: str | None = None, kind: str | None = None) -> list[dict]:
    """List captured ideas. Filter by status (new|needs_info|in_progress|done|rejected) or kind (task|feature|unknown)."""
    return _get("/ideas", status=status, kind=kind)


@mcp.tool()
def get_idea(idea_id: int) -> dict:
    """Get a single idea with the full message thread."""
    return _get(f"/ideas/{idea_id}")


@mcp.tool()
def post_idea_message(idea_id: int, text: str) -> dict:
    """Post an assistant message to an idea's thread. Use this to reply to the user — including clarifying questions."""
    return _post(f"/ideas/{idea_id}/messages", {"role": "assistant", "text": text})


@mcp.tool()
def set_idea(
    idea_id: int,
    status: str | None = None,
    kind: str | None = None,
    title: str | None = None,
    linked_task_id: int | None = None,
) -> dict:
    """Update an idea's classification or lifecycle state."""
    payload: dict[str, Any] = {}
    if status is not None:
        payload["status"] = status
    if kind is not None:
        payload["kind"] = kind
    if title is not None:
        payload["title"] = title
    if linked_task_id is not None:
        payload["linked_task_id"] = linked_task_id
    return _patch(f"/ideas/{idea_id}", payload)


@mcp.tool()
def delete_idea(idea_id: int) -> str:
    """Permanently delete an idea and its messages. Use only when the user confirms it should be discarded."""
    _delete(f"/ideas/{idea_id}")
    return f"deleted idea {idea_id}"


# ---- tasks ----


@mcp.tool()
def list_tasks() -> list[dict]:
    """List every existing task. Read this before creating a new one to avoid duplicates."""
    return _get("/tasks")


@mcp.tool()
def create_task(
    title: str,
    task_type: str,
    description: str | None = None,
    weekdays: list[int] | None = None,
    fixed_date: str | None = None,
    start_time: str | None = None,
    end_time: str | None = None,
    is_todo: bool = False,
    target_per_week: int | None = None,
    target_segments: list[dict] | None = None,
    show_in_upcoming: bool = True,
    notify_enabled: bool = False,
    notify_minutes_before: int = 0,
    notify_at: str | None = None,
    start_date: str | None = None,
    end_date: str | None = None,
) -> dict:
    """Create a task. task_type is one of: recurring, single, birthday, weekly_goal.

    Schema rules (mirror the backend validators):
      - recurring: needs weekdays (0=Mon..6=Sun); no fixed_date.
      - single: optional fixed_date (YYYY-MM-DD). Without it the task goes to backlog and must have is_todo=true.
      - birthday: needs fixed_date; no times, no is_todo.
      - weekly_goal: needs target_per_week OR target_segments [{weekdays:[..], target:N}, ...] with non-overlapping weekdays; no fixed_date or times; is_todo forced true.
      - start_date/end_date only apply to recurring and weekly_goal."""
    payload: dict[str, Any] = {
        "title": title,
        "task_type": task_type,
        "is_todo": is_todo,
        "show_in_upcoming": show_in_upcoming,
        "notify_enabled": notify_enabled,
        "notify_minutes_before": notify_minutes_before,
    }
    if description is not None:
        payload["description"] = description
    if weekdays is not None:
        payload["weekdays"] = weekdays
    if fixed_date is not None:
        payload["fixed_date"] = fixed_date
    if start_time is not None:
        payload["start_time"] = start_time
    if end_time is not None:
        payload["end_time"] = end_time
    if target_per_week is not None:
        payload["target_per_week"] = target_per_week
    if target_segments is not None:
        payload["target_segments"] = target_segments
    if notify_at is not None:
        payload["notify_at"] = notify_at
    if start_date is not None:
        payload["start_date"] = start_date
    if end_date is not None:
        payload["end_date"] = end_date
    return _post("/tasks", payload)


@mcp.tool()
def update_task(
    task_id: int,
    title: str | None = None,
    description: str | None = None,
    task_type: str | None = None,
    weekdays: list[int] | None = None,
    fixed_date: str | None = None,
    start_time: str | None = None,
    end_time: str | None = None,
    is_todo: bool | None = None,
    target_per_week: int | None = None,
    target_segments: list[dict] | None = None,
    show_in_upcoming: bool | None = None,
    notify_enabled: bool | None = None,
    notify_minutes_before: int | None = None,
    notify_at: str | None = None,
    start_date: str | None = None,
    end_date: str | None = None,
) -> dict:
    """Patch an existing task. Same schema as create_task — only set fields you want to change."""
    payload: dict[str, Any] = {
        k: v
        for k, v in {
            "title": title,
            "description": description,
            "task_type": task_type,
            "weekdays": weekdays,
            "fixed_date": fixed_date,
            "start_time": start_time,
            "end_time": end_time,
            "is_todo": is_todo,
            "target_per_week": target_per_week,
            "target_segments": target_segments,
            "show_in_upcoming": show_in_upcoming,
            "notify_enabled": notify_enabled,
            "notify_minutes_before": notify_minutes_before,
            "notify_at": notify_at,
            "start_date": start_date,
            "end_date": end_date,
        }.items()
        if v is not None
    }
    return _patch(f"/tasks/{task_id}", payload)


@mcp.tool()
def delete_task(task_id: int) -> str:
    """Permanently delete a task. Only when the user has explicitly asked."""
    _delete(f"/tasks/{task_id}")
    return f"deleted task {task_id}"


# ---- feature requests ----


@mcp.tool()
def list_features(status: str | None = None) -> list[dict]:
    """List feature requests. Filter by status (open|in_progress|done|rejected)."""
    return _get("/features", status=status)


@mcp.tool()
def create_feature(
    title: str,
    description: str | None = None,
    source_idea_id: int | None = None,
    status: str = "open",
) -> dict:
    """Create a feature request — the definitive ticket for an app-level change.

    Use when the user's idea is a feature and the conversation has settled
    enough to capture a clean title + description. The thread of the source
    idea is preserved separately; link it via source_idea_id.
    """
    payload: dict[str, Any] = {"title": title, "status": status}
    if description is not None:
        payload["description"] = description
    if source_idea_id is not None:
        payload["source_idea_id"] = source_idea_id
    return _post("/features", payload)


@mcp.tool()
def update_feature(
    feature_id: int,
    title: str | None = None,
    description: str | None = None,
    status: str | None = None,
) -> dict:
    """Patch a feature request — refine the title/description or move status."""
    payload: dict[str, Any] = {
        k: v
        for k, v in {"title": title, "description": description, "status": status}.items()
        if v is not None
    }
    return _patch(f"/features/{feature_id}", payload)


@mcp.tool()
def delete_feature(feature_id: int) -> str:
    """Permanently delete a feature request. Only on explicit user request."""
    _delete(f"/features/{feature_id}")
    return f"deleted feature {feature_id}"


@mcp.tool()
def toggle_task_completion(task_id: int, date: str, action: str = "toggle") -> dict:
    """Mark a task complete or undo. date is YYYY-MM-DD. action is one of toggle (flip), add (idempotent insert), remove (idempotent delete). For weekly_goal use 'add' to count multiple completions on the same day."""
    return _post(f"/tasks/{task_id}/toggle", {"completed_on": date, "action": action})


if __name__ == "__main__":
    mcp.run()
