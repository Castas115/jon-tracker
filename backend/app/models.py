from datetime import UTC, date, datetime

from sqlalchemy import JSON, Date, DateTime, ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .db import Base


def _utcnow() -> datetime:
    return datetime.now(UTC)


class Task(Base):
    __tablename__ = "tasks"

    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(200))
    task_type: Mapped[str] = mapped_column(String(16), default="recurring")
    # Recurring tasks: list of weekdays 0..6 (Mon..Sun); fixed tasks: NULL.
    weekdays: Mapped[list[int] | None] = mapped_column(JSON, nullable=True)
    # Fixed-date tasks: the single calendar date; recurring: NULL.
    fixed_date: Mapped[date | None] = mapped_column(Date, nullable=True)
    start_time: Mapped[str | None] = mapped_column(String(5), nullable=True)  # "HH:MM"
    end_time: Mapped[str | None] = mapped_column(String(5), nullable=True)
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
