from datetime import date, datetime
from typing import Annotated, Literal

from pydantic import BaseModel, Field, model_validator

TimeStr = Annotated[str, Field(pattern=r"^(?:[01]\d|2[0-3]):[0-5]\d$")]
Weekday = Annotated[int, Field(ge=0, le=6)]
TaskType = Literal["recurring", "single", "birthday", "weekly_goal"]


class TargetSegment(BaseModel):
    """A weekday-bucketed segment of a weekly goal.

    Example: meditar 2x Mon-Thu + 1x Fri-Sun → two segments.
    """

    weekdays: list[Weekday] = Field(min_length=1)
    target: int = Field(ge=1, le=99)

    @model_validator(mode="after")
    def _unique(self):
        if len(set(self.weekdays)) != len(self.weekdays):
            raise ValueError("segment weekdays must be unique")
        return self


def _validate(model):
    """Shared validation: type coherence + time range."""
    if model.start_time and model.end_time and model.end_time <= model.start_time:
        raise ValueError("end_time must be later than start_time")
    if model.end_time and not model.start_time:
        raise ValueError("end_time requires start_time")
    sd = getattr(model, "start_date", None)
    ed = getattr(model, "end_date", None)
    if sd and ed and ed < sd:
        raise ValueError("end_date must be on or after start_date")

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
        segs = getattr(model, "target_segments", None) or []
        flat = getattr(model, "target_per_week", None)
        if not flat and not segs:
            raise ValueError("weekly_goal tasks need target_per_week or target_segments")
        if segs:
            # Disallow overlapping weekdays between segments — every weekday
            # can belong to at most one segment so completion counting is
            # unambiguous.
            seen: set[int] = set()
            for s in segs:
                wds = s.weekdays if hasattr(s, "weekdays") else s["weekdays"]
                for wd in wds:
                    if wd in seen:
                        raise ValueError(f"weekday {wd} appears in multiple target_segments")
                    seen.add(wd)
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
    description: str | None = None
    task_type: TaskType = "recurring"
    weekdays: list[Weekday] | None = None
    fixed_date: date | None = None
    start_time: TimeStr | None = None
    end_time: TimeStr | None = None
    is_todo: bool = False
    target_per_week: int | None = Field(default=None, ge=1, le=99)
    target_segments: list[TargetSegment] | None = None
    show_in_upcoming: bool = True
    notify_enabled: bool = False
    notify_minutes_before: int = Field(default=0, ge=0, le=1440)
    notify_at: TimeStr | None = None
    start_date: date | None = None
    end_date: date | None = None

    @model_validator(mode="after")
    def _check(self):
        return _validate(self)


class TaskUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=200)
    description: str | None = None
    task_type: TaskType | None = None
    weekdays: list[Weekday] | None = None
    fixed_date: date | None = None
    start_time: TimeStr | None = None
    end_time: TimeStr | None = None
    is_todo: bool | None = None
    target_per_week: int | None = Field(default=None, ge=1, le=99)
    target_segments: list[TargetSegment] | None = None
    show_in_upcoming: bool | None = None
    notify_enabled: bool | None = None
    notify_minutes_before: int | None = Field(default=None, ge=0, le=1440)
    notify_at: TimeStr | None = None
    start_date: date | None = None
    end_date: date | None = None

    @model_validator(mode="after")
    def _check_range(self):
        if self.start_date and self.end_date and self.end_date < self.start_date:
            raise ValueError("end_date must be on or after start_date")
        return self


class TaskRead(BaseModel):
    id: int
    title: str
    description: str | None
    task_type: TaskType
    weekdays: list[int] | None
    fixed_date: date | None
    start_time: str | None
    end_time: str | None
    is_todo: bool
    target_per_week: int | None
    target_segments: list[TargetSegment] | None
    show_in_upcoming: bool
    notify_enabled: bool
    notify_minutes_before: int
    notify_at: str | None
    start_date: date | None
    end_date: date | None
    created_at: datetime
    completed_dates: list[date]

    model_config = {"from_attributes": True}


class CompletionToggle(BaseModel):
    completed_on: date
    # "toggle" (default) → flip current state.
    # "add"   → ensure there is a completion for this date (idempotent).
    # "remove"→ ensure there is no completion for this date (idempotent).
    action: Literal["toggle", "add", "remove"] = "toggle"


IdeaKind = Literal["task", "feature", "unknown"]
IdeaStatus = Literal["new", "needs_info", "in_progress", "done", "rejected"]
MessageRole = Literal["user", "assistant"]


class IdeaMessageRead(BaseModel):
    id: int
    role: MessageRole
    text: str
    created_at: datetime

    model_config = {"from_attributes": True}


class IdeaRead(BaseModel):
    id: int
    kind: IdeaKind
    title: str
    transcript: str
    status: IdeaStatus
    linked_task_id: int | None
    created_at: datetime
    updated_at: datetime
    messages: list[IdeaMessageRead]

    model_config = {"from_attributes": True}


class IdeaCreate(BaseModel):
    transcript: str = Field(min_length=1)
    kind: IdeaKind = "unknown"
    title: str = Field(default="", max_length=200)


class IdeaUpdate(BaseModel):
    kind: IdeaKind | None = None
    title: str | None = Field(default=None, max_length=200)
    status: IdeaStatus | None = None
    linked_task_id: int | None = None


class IdeaMessageCreate(BaseModel):
    role: MessageRole
    text: str = Field(min_length=1)


class TranscribeResponse(BaseModel):
    text: str
