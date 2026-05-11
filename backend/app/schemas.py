from datetime import date, datetime
from typing import Annotated, Literal

from pydantic import BaseModel, Field, model_validator

TimeStr = Annotated[str, Field(pattern=r"^(?:[01]\d|2[0-3]):[0-5]\d$")]
Weekday = Annotated[int, Field(ge=0, le=6)]
TaskType = Literal["recurring", "single", "birthday", "weekly_goal"]


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
    elif model.task_type == "single":
        if model.weekdays:
            raise ValueError("single tasks must not set weekdays")
        # fixed_date may be None → backlog item. Must be actionable, else it
        # can never appear or be completed.
        if model.fixed_date is None and not getattr(model, "is_todo", False):
            raise ValueError("single tasks without fixed_date must have is_todo=true")
    elif model.task_type == "birthday":
        if model.fixed_date is None:
            raise ValueError("birthday tasks need fixed_date (any year, month+day matter)")
        if model.weekdays:
            raise ValueError("birthday tasks must not set weekdays")
        if model.start_time or model.end_time:
            raise ValueError("birthday tasks must not have times")
        if getattr(model, "is_todo", False):
            raise ValueError("birthday tasks cannot be todos")
    elif model.task_type == "weekly_goal":
        if not getattr(model, "target_per_week", None) or model.target_per_week < 1:
            raise ValueError("weekly_goal tasks need target_per_week >= 1")
        if model.weekdays:
            raise ValueError("weekly_goal tasks must not set weekdays")
        if model.fixed_date is not None:
            raise ValueError("weekly_goal tasks must not set fixed_date")
        if model.start_time or model.end_time:
            raise ValueError("weekly_goal tasks must not have times")
        if not getattr(model, "is_todo", False):
            raise ValueError("weekly_goal tasks must be todos")
    return model


class TaskCreate(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    task_type: TaskType = "recurring"
    weekdays: list[Weekday] | None = None
    fixed_date: date | None = None
    start_time: TimeStr | None = None
    end_time: TimeStr | None = None
    is_todo: bool = False
    target_per_week: int | None = Field(default=None, ge=1, le=99)

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
    is_todo: bool | None = None
    target_per_week: int | None = Field(default=None, ge=1, le=99)


class TaskRead(BaseModel):
    id: int
    title: str
    task_type: TaskType
    weekdays: list[int] | None
    fixed_date: date | None
    start_time: str | None
    end_time: str | None
    is_todo: bool
    target_per_week: int | None
    created_at: datetime
    completed_dates: list[date]

    model_config = {"from_attributes": True}


class CompletionToggle(BaseModel):
    completed_on: date
