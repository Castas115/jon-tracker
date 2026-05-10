from datetime import UTC, date, datetime

from sqlalchemy import Date, DateTime, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .db import Base


def _utcnow() -> datetime:
    return datetime.now(UTC)


class Task(Base):
    __tablename__ = "tasks"

    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(200))
    weekday: Mapped[int] = mapped_column(Integer)  # 0=lunes, 6=domingo
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
