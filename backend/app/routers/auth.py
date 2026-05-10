from fastapi import APIRouter, HTTPException, Query, status
from fastapi.responses import RedirectResponse

from .. import google as gc

router = APIRouter(prefix="/auth/google", tags=["auth"])


@router.get("/status")
def status_endpoint() -> dict:
    return {"connected": gc.is_connected()}


@router.get("/connect")
def connect():
    try:
        flow = gc.make_flow()
    except FileNotFoundError as exc:
        raise HTTPException(
            status.HTTP_412_PRECONDITION_FAILED,
            f"missing OAuth client secrets file ({exc.filename}). "
            "Place credentials.json from Google Cloud Console under ./secrets/.",
        )
    auth_url, _ = flow.authorization_url(
        prompt="consent",
        access_type="offline",
        include_granted_scopes="true",
    )
    return RedirectResponse(auth_url, status_code=status.HTTP_302_FOUND)


@router.get("/callback")
def callback(code: str = Query(...)):
    try:
        flow = gc.make_flow()
        flow.fetch_token(code=code)
        gc.save_credentials(flow.credentials)
    except Exception as exc:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, f"oauth callback failed: {exc}")
    return RedirectResponse("/", status_code=status.HTTP_302_FOUND)


@router.delete("", status_code=status.HTTP_204_NO_CONTENT)
def disconnect():
    gc.clear_credentials()
