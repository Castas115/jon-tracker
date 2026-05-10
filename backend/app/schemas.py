from datetime import date, datetime

from pydantic import BaseModel, Field


class TaskCreate(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    weekday: int = Field(ge=0, le=6)


class TaskUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=200)
    weekday: int | None = Field(default=None, ge=0, le=6)


class TaskRead(BaseModel):
    id: int
    title: str
    weekday: int
    created_at: datetime
    completed_dates: list[date]

    model_config = {"from_attributes": True}


class CompletionToggle(BaseModel):
    completed_on: date
