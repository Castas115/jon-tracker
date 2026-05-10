from datetime import date, datetime
from typing import Annotated, Literal

from pydantic import BaseModel, Field, model_validator

TimeStr = Annotated[str, Field(pattern=r"^(?:[01]\d|2[0-3]):[0-5]\d$")]
Weekday = Annotated[int, Field(ge=0, le=6)]
TaskType = Literal["recurring", "fixed", "birthday"]


def _validate(model):
    """Shared validation: type coherence + time range."""
    if model.start_time and model.end_time and model.end_time <= model.start_time:
        raise ValueError("end_time must be later than start_time")
    if model.end_time and not model.start_time:
        raise ValueError("end_time requires start_time")

    if model.task_type == "recurring":
        if not model.weekdays:
            raise ValueError("recurring tasks need at least one weekday")
        if model.fixed_date is not None:
            raise ValueError("recurring tasks must not set fixed_date")
    elif model.task_type == "fixed":
        if model.fixed_date is None:
            raise ValueError("fixed tasks need fixed_date")
        if model.weekdays:
            raise ValueError("fixed tasks must not set weekdays")
    elif model.task_type == "birthday":
        if model.fixed_date is None:
            raise ValueError("birthday tasks need fixed_date (any year, month+day matter)")
        if model.weekdays:
            raise ValueError("birthday tasks must not set weekdays")
        if model.start_time or model.end_time:
            raise ValueError("birthday tasks must not have times")
    return model


class TaskCreate(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    task_type: TaskType = "recurring"
    weekdays: list[Weekday] | None = None
    fixed_date: date | None = None
    start_time: TimeStr | None = None
    end_time: TimeStr | None = None

    @model_validator(mode="after")
    def _check(self):
        return _validate(self)


class TaskUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=200)
    task_type: TaskType | None = None
    weekdays: list[Weekday] | None = None
    fixed_date: date | None = None
    start_time: TimeStr | None = None
    end_time: TimeStr | None = None


class TaskRead(BaseModel):
    id: int
    title: str
    task_type: TaskType
    weekdays: list[int] | None
    fixed_date: date | None
    start_time: str | None
    end_time: str | None
    created_at: datetime
    completed_dates: list[date]

    model_config = {"from_attributes": True}


class CompletionToggle(BaseModel):
    completed_on: date
