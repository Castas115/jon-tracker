from fastapi import APIRouter, Depends, File, HTTPException, UploadFile, status
from sqlalchemy import select
from sqlalchemy.orm import Session, selectinload

from ..config import settings
from ..db import get_db
from ..models import Idea, IdeaMessage
from ..schemas import IdeaCreate, IdeaMessageCreate, IdeaRead, IdeaUpdate, TranscribeResponse

import httpx

router = APIRouter(prefix="/ideas", tags=["ideas"])


@router.get("", response_model=list[IdeaRead])
def list_ideas(
    kind: str | None = None,
    status: str | None = None,
    db: Session = Depends(get_db),
) -> list[Idea]:
    stmt = select(Idea).options(selectinload(Idea.messages)).order_by(Idea.updated_at.desc())
    if kind:
        stmt = stmt.where(Idea.kind == kind)
    if status:
        stmt = stmt.where(Idea.status == status)
    return list(db.scalars(stmt).all())


@router.post("", response_model=IdeaRead, status_code=201)
def create_idea(payload: IdeaCreate, db: Session = Depends(get_db)) -> Idea:
    idea = Idea(
        kind=payload.kind,
        title=payload.title or _summary(payload.transcript),
        transcript=payload.transcript,
    )
    db.add(idea)
    db.flush()
    # The transcript also lives as the first user message so the thread
    # reads naturally top-to-bottom.
    db.add(IdeaMessage(idea_id=idea.id, role="user", text=payload.transcript))
    db.commit()
    db.refresh(idea)
    return idea


@router.get("/{idea_id}", response_model=IdeaRead)
def get_idea(idea_id: int, db: Session = Depends(get_db)) -> Idea:
    idea = db.get(Idea, idea_id)
    if idea is None:
        raise HTTPException(404, "idea not found")
    return idea


@router.patch("/{idea_id}", response_model=IdeaRead)
def update_idea(idea_id: int, payload: IdeaUpdate, db: Session = Depends(get_db)) -> Idea:
    idea = db.get(Idea, idea_id)
    if idea is None:
        raise HTTPException(404, "idea not found")
    data = payload.model_dump(exclude_unset=True)
    for k, v in data.items():
        setattr(idea, k, v)
    db.commit()
    db.refresh(idea)
    return idea


@router.delete("/{idea_id}", status_code=204)
def delete_idea(idea_id: int, db: Session = Depends(get_db)) -> None:
    idea = db.get(Idea, idea_id)
    if idea is None:
        raise HTTPException(404, "idea not found")
    db.delete(idea)
    db.commit()


@router.post("/{idea_id}/messages", response_model=IdeaRead, status_code=201)
def post_message(idea_id: int, payload: IdeaMessageCreate, db: Session = Depends(get_db)) -> Idea:
    idea = db.get(Idea, idea_id)
    if idea is None:
        raise HTTPException(404, "idea not found")
    db.add(IdeaMessage(idea_id=idea.id, role=payload.role, text=payload.text))
    # Posting a message bumps updated_at so the inbox sorts correctly.
    idea.updated_at = idea.updated_at  # noqa: PLW0127 — touch field for onupdate
    db.commit()
    db.refresh(idea)
    return idea


@router.post("/transcribe", response_model=TranscribeResponse)
async def transcribe(audio: UploadFile = File(...)) -> TranscribeResponse:
    """Stream the uploaded audio to OpenAI/Azure Whisper. Never persists."""
    data = await audio.read()
    if not data:
        raise HTTPException(400, "empty audio upload")
    filename = audio.filename or "audio.m4a"
    content_type = audio.content_type or "application/octet-stream"

    groq_ready = bool(settings.groq_api_key)
    azure_ready = bool(
        settings.azure_openai_endpoint
        and settings.azure_openai_api_key
        and settings.azure_openai_deployment_name
    )
    openai_ready = bool(settings.openai_api_key)
    if not (groq_ready or azure_ready or openai_ready):
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE,
            "transcription not configured (need GROQ_API_KEY, AZURE_OPENAI_*, or OPENAI_API_KEY)",
        )

    if groq_ready:
        url = "https://api.groq.com/openai/v1/audio/transcriptions"
        headers = {"Authorization": f"Bearer {settings.groq_api_key}"}
        form_data: dict[str, str] = {"model": settings.groq_model}
    elif azure_ready:
        endpoint = settings.azure_openai_endpoint.rstrip("/")
        url = (
            f"{endpoint}/openai/deployments/"
            f"{settings.azure_openai_deployment_name}/audio/transcriptions"
            f"?api-version={settings.azure_openai_api_version}"
        )
        headers = {"api-key": settings.azure_openai_api_key}
        form_data = {}
    else:
        url = "https://api.openai.com/v1/audio/transcriptions"
        headers = {"Authorization": f"Bearer {settings.openai_api_key}"}
        form_data = {"model": settings.whisper_model}

    async with httpx.AsyncClient(timeout=60.0) as client:
        res = await client.post(
            url,
            headers=headers,
            files={"file": (filename, data, content_type)},
            data=form_data,
        )
    if res.status_code != 200:
        raise HTTPException(
            502,
            f"transcribe failed ({res.status_code}): {res.text[:400]}",
        )
    body = res.json()
    return TranscribeResponse(text=body.get("text", ""))


def _summary(text: str) -> str:
    """Cheap, deterministic title — first 80 chars of the first line."""
    line = text.strip().splitlines()[0] if text.strip() else ""
    return line[:80]
