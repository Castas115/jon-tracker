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

_CACHE_TTL_SECONDS = 300  # 5 minutes
_raw_cache: tuple[float, bytes] | None = None
_cal_cache: tuple[float, tuple[Calendar, set[str]]] | None = None
_events_cache: dict[tuple[date, date], tuple[float, list[dict]]] = {}


def is_configured() -> bool:
    return bool(settings.ics_url)


def _fetch_raw() -> bytes:
    global _raw_cache
    now = time.time()
    if _raw_cache and now - _raw_cache[0] < _CACHE_TTL_SECONDS:
        return _raw_cache[1]
    resp = httpx.get(
        settings.ics_url,
        timeout=10.0,
        headers={"User-Agent": "jon-tracker/0.1"},
        follow_redirects=True,
    )
    resp.raise_for_status()
    _raw_cache = (now, resp.content)
    return resp.content


def _collect_birthday_uids(cal: Calendar) -> set[str]:
    """A VEVENT looks like a birthday when it recurs yearly and DTSTART is a
    plain DATE (not DATETIME). That's exactly the shape Google uses for the
    "every year on this day" entries, so it catches cumpleaños without false
    positives on regular yearly meetings (which have a time of day).
    """
    out: set[str] = set()
    for ev in cal.walk("VEVENT"):
        rrule = ev.get("RRULE")
        if not rrule:
            continue
        freq = rrule.get("FREQ") or []
        if not freq or freq[0] != "YEARLY":
            continue
        dtstart = ev.get("DTSTART")
        if dtstart is None:
            continue
        if not isinstance(dtstart.dt, date) or isinstance(dtstart.dt, datetime):
            continue
        uid = str(ev.get("UID", ""))
        if uid:
            out.add(uid)
    return out


def _get_calendar() -> tuple[Calendar, set[str]]:
    """Return parsed + RRULE-patched calendar plus the set of UIDs we
    consider birthdays. Cached so repeated requests don't re-parse the
    entire .ics blob.
    """
    global _cal_cache
    now = time.time()
    if _cal_cache and now - _cal_cache[0] < _CACHE_TTL_SECONDS:
        return _cal_cache[1]
    raw = _fetch_raw().decode("utf-8", errors="replace")
    cal = Calendar.from_ical(raw)
    _patch_yearly_rrules(cal)
    birthdays = _collect_birthday_uids(cal)
    _cal_cache = (now, (cal, birthdays))
    return cal, birthdays


def _to_iso(value) -> tuple[str, bool]:
    """Return ISO string + all_day flag for a DTSTART/DTEND value."""
    if isinstance(value, datetime):
        if value.tzinfo is None:
            value = value.replace(tzinfo=timezone.utc)
        return value.astimezone(timezone.utc).isoformat(), False
    if isinstance(value, date):
        return value.isoformat(), True
    return str(value), False


def _patch_yearly_rrules(cal: Calendar) -> None:
    """Google emits annual birthdays as `FREQ=YEARLY;BYMONTHDAY=N` with no
    `BYMONTH`. RFC 5545 says BYMONTHDAY MUST come with BYMONTH for YEARLY
    rules, so libraries fall back to "Nth of every month" — completely wrong.
    Backfill BYMONTH from DTSTART so the rule expands once per year as
    intended.
    """
    for ev in cal.walk("VEVENT"):
        rrule = ev.get("RRULE")
        if not rrule:
            continue
        freq = rrule.get("FREQ") or []
        if freq and freq[0] != "YEARLY":
            continue
        if rrule.get("BYMONTH"):
            continue
        if not rrule.get("BYMONTHDAY"):
            continue
        dtstart = ev.get("DTSTART")
        if dtstart is None:
            continue
        month = getattr(dtstart.dt, "month", None)
        if month is None:
            continue
        rrule["BYMONTH"] = [month]


def list_events(from_date: date, to_date: date) -> list[dict]:
    if not is_configured():
        return []

    key = (from_date, to_date)
    now = time.time()
    cached = _events_cache.get(key)
    if cached and now - cached[0] < _CACHE_TTL_SECONDS:
        return cached[1]

    cal, birthday_uids = _get_calendar()

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

        uid = str(ev.get("UID", ""))
        kind = "birthday" if uid in birthday_uids else "event"
        out.append(
            {
                "id": uid + "@" + start_iso,
                "title": str(ev.get("SUMMARY", "(untitled)")),
                "start": start_iso,
                "end": end_iso,
                "all_day": all_day,
                "kind": kind,
                "location": str(ev.get("LOCATION")) if ev.get("LOCATION") else None,
                "description": str(ev.get("DESCRIPTION")) if ev.get("DESCRIPTION") else None,
            }
        )

    _events_cache[key] = (now, out)
    return out
