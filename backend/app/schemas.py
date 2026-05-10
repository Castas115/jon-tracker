from datetime import date, datetime
from typing import Annotated

from pydantic import BaseModel, Field, model_validator

TimeStr = Annotated[str, Field(pattern=r"^(?:[01]\d|2[0-3]):[0-5]\d$")]


class _TimeRangeMixin:
    start_time: str | None
    end_time: str | None

    @model_validator(mode="after")
    def _check_range(self):
        if self.start_time and self.end_time and self.end_time <= self.start_time:
            raise ValueError("end_time must be later than start_time")
        if self.end_time and not self.start_time:
            raise ValueError("end_time requires start_time")
        return self


class TaskCreate(BaseModel, _TimeRangeMixin):
    title: str = Field(min_length=1, max_length=200)
    weekday: int = Field(ge=0, le=6)
    start_time: TimeStr | None = None
    end_time: TimeStr | None = None


class TaskUpdate(BaseModel, _TimeRangeMixin):
    title: str | None = Field(default=None, min_length=1, max_length=200)
    weekday: int | None = Field(default=None, ge=0, le=6)
    start_time: TimeStr | None = None
    end_time: TimeStr | None = None


class TaskRead(BaseModel):
    id: int
    title: str
    weekday: int
    start_time: str | None
    end_time: str | None
    created_at: datetime
    completed_dates: list[date]

    model_config = {"from_attributes": True}


class CompletionToggle(BaseModel):
    completed_on: date
