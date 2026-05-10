from datetime import date

from fastapi import APIRouter, HTTPException, Query, status
from pydantic import BaseModel

from .. import google as gc

router = APIRouter(prefix="/calendar", tags=["calendar"])


class CalendarEvent(BaseModel):
    id: str
    title: str
    start: str  # ISO datetime or date
    end: str
    all_day: bool
    location: str | None = None
    description: str | None = None


def _to_event(raw: dict) -> CalendarEvent:
    start = raw.get("start", {})
    end = raw.get("end", {})
    all_day = "date" in start
    return CalendarEvent(
        id=raw.get("id", ""),
        title=raw.get("summary", "(untitled)"),
        start=start.get("dateTime") or start.get("date") or "",
        end=end.get("dateTime") or end.get("date") or "",
        all_day=all_day,
        location=raw.get("location"),
        description=raw.get("description"),
    )


@router.get("/events", response_model=list[CalendarEvent])
def list_events(
    from_date: date = Query(..., alias="from"),
    to_date: date = Query(..., alias="to"),
) -> list[CalendarEvent]:
    if not gc.is_connected():
        raise HTTPException(
            status.HTTP_412_PRECONDITION_FAILED,
            "google calendar not connected — visit /auth/google/connect",
        )
    if to_date < from_date:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "to must be >= from")
    raw = gc.list_events(from_date, to_date)
    return [_to_event(r) for r in raw]
