from datetime import date

from fastapi import APIRouter, HTTPException, Query, status
from pydantic import BaseModel

from .. import ics

router = APIRouter(prefix="/calendar", tags=["calendar"])


class CalendarEvent(BaseModel):
    id: str
    title: str
    start: str
    end: str
    all_day: bool
    kind: str = "event"  # "event" or "birthday"
    location: str | None = None
    description: str | None = None


@router.get("/status")
def status_endpoint() -> dict:
    return {"configured": ics.is_configured()}


@router.get("/events", response_model=list[CalendarEvent])
def list_events(
    from_date: date = Query(..., alias="from"),
    to_date: date = Query(..., alias="to"),
) -> list[CalendarEvent]:
    if not ics.is_configured():
        raise HTTPException(
            status.HTTP_412_PRECONDITION_FAILED,
            "ICS_URL not configured. Set the ICS_URL env var to your Google "
            "Calendar 'Secret address in iCal format' URL.",
        )
    if to_date < from_date:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "to must be >= from")
    try:
        return [CalendarEvent(**ev) for ev in ics.list_events(from_date, to_date)]
    except Exception as exc:
        raise HTTPException(
            status.HTTP_502_BAD_GATEWAY, f"failed to fetch ics: {exc}"
        )
