from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..db import get_db
from ..models import FeatureRequest
from ..schemas import FeatureRequestCreate, FeatureRequestRead, FeatureRequestUpdate

router = APIRouter(prefix="/features", tags=["features"])


@router.get("", response_model=list[FeatureRequestRead])
def list_features(
    status: str | None = None,
    db: Session = Depends(get_db),
) -> list[FeatureRequest]:
    stmt = select(FeatureRequest).order_by(FeatureRequest.updated_at.desc())
    if status:
        stmt = stmt.where(FeatureRequest.status == status)
    return list(db.scalars(stmt).all())


@router.post("", response_model=FeatureRequestRead, status_code=201)
def create_feature(payload: FeatureRequestCreate, db: Session = Depends(get_db)) -> FeatureRequest:
    fr = FeatureRequest(
        title=payload.title,
        description=payload.description,
        status=payload.status,
        source_idea_id=payload.source_idea_id,
    )
    db.add(fr)
    db.commit()
    db.refresh(fr)
    return fr


@router.get("/{feature_id}", response_model=FeatureRequestRead)
def get_feature(feature_id: int, db: Session = Depends(get_db)) -> FeatureRequest:
    fr = db.get(FeatureRequest, feature_id)
    if fr is None:
        raise HTTPException(404, "feature request not found")
    return fr


@router.patch("/{feature_id}", response_model=FeatureRequestRead)
def update_feature(
    feature_id: int, payload: FeatureRequestUpdate, db: Session = Depends(get_db)
) -> FeatureRequest:
    fr = db.get(FeatureRequest, feature_id)
    if fr is None:
        raise HTTPException(404, "feature request not found")
    for k, v in payload.model_dump(exclude_unset=True).items():
        setattr(fr, k, v)
    db.commit()
    db.refresh(fr)
    return fr


@router.delete("/{feature_id}", status_code=204)
def delete_feature(feature_id: int, db: Session = Depends(get_db)) -> None:
    fr = db.get(FeatureRequest, feature_id)
    if fr is None:
        raise HTTPException(404, "feature request not found")
    db.delete(fr)
    db.commit()
