from datetime import UTC, date, datetime

from sqlalchemy import JSON, Boolean, Date, DateTime, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .db import Base


def _utcnow() -> datetime:
    return datetime.now(UTC)


class Task(Base):
    __tablename__ = "tasks"

    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(200))
    task_type: Mapped[str] = mapped_column(String(16), default="recurring")
    # Recurring tasks: list of weekdays 0..6 (Mon..Sun); single/birthday: NULL.
    weekdays: Mapped[list[int] | None] = mapped_column(JSON, nullable=True)
    # Single & birthday tasks: the calendar date (birthday matches month+day yearly).
    fixed_date: Mapped[date | None] = mapped_column(Date, nullable=True)
    start_time: Mapped[str | None] = mapped_column(String(5), nullable=True)  # "HH:MM"
    end_time: Mapped[str | None] = mapped_column(String(5), nullable=True)
    # When true the task is actionable (renders a checkbox). Always false for birthdays.
    is_todo: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    # When true the task appears in the day-view "Upcoming" panel. Default
    # true so existing rows keep showing up.
    show_in_upcoming: Mapped[bool] = mapped_column(
        Boolean, default=True, nullable=False, server_default="1"
    )
    # Notifications. Master switch + how to schedule the alarm:
    #   - if start_time set: fire at (start_time - notify_minutes_before).
    #   - else if notify_at set: fire at notify_at on the matching day.
    #   - else: nothing fires.
    notify_enabled: Mapped[bool] = mapped_column(
        Boolean, default=False, nullable=False, server_default="0"
    )
    notify_minutes_before: Mapped[int] = mapped_column(
        Integer, default=0, nullable=False, server_default="0"
    )
    notify_at: Mapped[str | None] = mapped_column(String(5), nullable=True)  # "HH:MM"
    # Weekly-goal tasks: how many completions per ISO week count as the goal.
    target_per_week: Mapped[int | None] = mapped_column(Integer, nullable=True)
    # Weekly-goal tasks with weekday-specific targets. List of
    # {"weekdays": [0..6], "target": int>=1}. When set, overrides
    # target_per_week — total target = sum(target across segments).
    target_segments: Mapped[list[dict] | None] = mapped_column(JSON, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_utcnow)

    completions: Mapped[list["TaskCompletion"]] = relationship(
        back_populates="task", cascade="all, delete-orphan"
    )


class TaskCompletion(Base):
    __tablename__ = "task_completions"

    id: Mapped[int] = mapped_column(primary_key=True)
    task_id: Mapped[int] = mapped_column(ForeignKey("tasks.id", ondelete="CASCADE"))
    completed_on: Mapped[date] = mapped_column(Date)

    task: Mapped[Task] = relationship(back_populates="completions")
