"""Read-only Google Calendar sync via the secret iCal URL.

We pull the user's `.ics` feed (Google's "Secret address in iCal format")
on demand, parse VEVENTs, and expand RRULE recurrences inside the requested
window with `recurring-ical-events`. The result is cached briefly so a
typical UI render doesn't hit the network on every request.

Configuration: a single env var, `ICS_URL`, holds the secret feed URL.
"""

from __future__ import annotations

import time
from datetime import date, datetime, timedelta, timezone

import httpx
import recurring_ical_events
from icalendar import Calendar

from .config import settings

_CACHE_TTL_SECONDS = 60
_cache: tuple[float, bytes] | None = None


def is_configured() -> bool:
    return bool(settings.ics_url)


def _fetch_raw() -> bytes:
    global _cache
    now = time.time()
    if _cache and now - _cache[0] < _CACHE_TTL_SECONDS:
        return _cache[1]
    resp = httpx.get(
        settings.ics_url,
        timeout=10.0,
        headers={"User-Agent": "jon-tracker/0.1"},
        follow_redirects=True,
    )
    resp.raise_for_status()
    _cache = (now, resp.content)
    return resp.content


def _to_iso(value) -> tuple[str, bool]:
    """Return ISO string + all_day flag for a DTSTART/DTEND value."""
    if isinstance(value, datetime):
        if value.tzinfo is None:
            value = value.replace(tzinfo=timezone.utc)
        return value.astimezone(timezone.utc).isoformat(), False
    if isinstance(value, date):
        return value.isoformat(), True
    return str(value), False


def list_events(from_date: date, to_date: date) -> list[dict]:
    if not is_configured():
        return []

    raw = _fetch_raw().decode("utf-8", errors="replace")
    cal = Calendar.from_ical(raw)

    # recurring-ical-events expands RRULEs into concrete instances within range.
    expanded = recurring_ical_events.of(cal).between(
        from_date,
        to_date + timedelta(days=1),
    )

    out: list[dict] = []
    for ev in expanded:
        dtstart = ev.get("DTSTART")
        dtend = ev.get("DTEND")
        if dtstart is None:
            continue
        start_iso, all_day = _to_iso(dtstart.dt)
        if dtend is not None:
            end_iso, _ = _to_iso(dtend.dt)
        elif all_day:
            # All-day events without DTEND span a single day.
            end_iso = (dtstart.dt + timedelta(days=1)).isoformat()
        else:
            end_iso = start_iso

        out.append(
            {
                "id": str(ev.get("UID", "")) + "@" + start_iso,
                "title": str(ev.get("SUMMARY", "(untitled)")),
                "start": start_iso,
                "end": end_iso,
                "all_day": all_day,
                "location": str(ev.get("LOCATION")) if ev.get("LOCATION") else None,
                "description": str(ev.get("DESCRIPTION")) if ev.get("DESCRIPTION") else None,
            }
        )
    return out
