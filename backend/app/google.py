"""Google Calendar OAuth + read-only event fetching.

One-way sync: the app reads events from the user's primary calendar but never
writes back. The OAuth flow is a single-user setup: the refresh token is
persisted to a JSON file on disk so the app survives restarts without
re-authorizing.
"""

from __future__ import annotations

from datetime import date, datetime, timedelta
from pathlib import Path

from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import Flow
from googleapiclient.discovery import build

from .config import settings

SCOPES = ["https://www.googleapis.com/auth/calendar.readonly"]


def make_flow() -> Flow:
    return Flow.from_client_secrets_file(
        settings.google_client_secrets_file,
        scopes=SCOPES,
        redirect_uri=settings.google_redirect_uri,
    )


def load_credentials() -> Credentials | None:
    p = Path(settings.google_token_file)
    if not p.exists():
        return None
    creds = Credentials.from_authorized_user_file(str(p), SCOPES)
    if creds.expired and creds.refresh_token:
        creds.refresh(Request())
        save_credentials(creds)
    return creds


def save_credentials(creds: Credentials) -> None:
    p = Path(settings.google_token_file)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(creds.to_json())


def clear_credentials() -> None:
    p = Path(settings.google_token_file)
    if p.exists():
        p.unlink()


def is_connected() -> bool:
    return Path(settings.google_token_file).exists()


def list_events(from_date: date, to_date: date) -> list[dict]:
    creds = load_credentials()
    if creds is None:
        return []

    service = build("calendar", "v3", credentials=creds, cache_discovery=False)

    time_min = datetime.combine(from_date, datetime.min.time()).isoformat() + "Z"
    time_max = datetime.combine(to_date + timedelta(days=1), datetime.min.time()).isoformat() + "Z"

    resp = (
        service.events()
        .list(
            calendarId=settings.google_calendar_id,
            timeMin=time_min,
            timeMax=time_max,
            singleEvents=True,
            orderBy="startTime",
            maxResults=250,
        )
        .execute()
    )
    return resp.get("items", [])
