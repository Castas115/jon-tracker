from datetime import UTC, date, datetime

from sqlalchemy import JSON, Boolean, Date, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .db import Base


def _utcnow() -> datetime:
    return datetime.now(UTC)


class Task(Base):
    __tablename__ = "tasks"

    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(200))
    # Free-form notes Jon can attach to the task — recipe, link, context.
    # Optional and unbounded.
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
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
    # Active range. start_date defaults to creation day; end_date (inclusive)
    # ends the task so it stops appearing in views/streaks without being
    # destroyed. Only enforced for recurring + weekly_goal.
    start_date: Mapped[date | None] = mapped_column(Date, nullable=True)
    end_date: Mapped[date | None] = mapped_column(Date, nullable=True)
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


class Idea(Base):
    """A captured idea — either a request to create/modify a task ("task")
    or a desired change to the app itself ("feature"). The transcript is
    the raw text the user submitted (often from voice transcription); the
    title is a short summary either the user or the assistant sets.
    """

    __tablename__ = "ideas"

    id: Mapped[int] = mapped_column(primary_key=True)
    kind: Mapped[str] = mapped_column(String(16), default="unknown")  # task|feature|unknown
    title: Mapped[str] = mapped_column(String(200), default="")
    transcript: Mapped[str] = mapped_column(Text, default="")
    # new | needs_info | in_progress | done | rejected
    status: Mapped[str] = mapped_column(String(16), default="new")
    linked_task_id: Mapped[int | None] = mapped_column(
        ForeignKey("tasks.id", ondelete="SET NULL"), nullable=True
    )
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=_utcnow, onupdate=_utcnow)

    messages: Mapped[list["IdeaMessage"]] = relationship(
        back_populates="idea",
        cascade="all, delete-orphan",
        order_by="IdeaMessage.created_at",
    )


class FeatureRequest(Base):
    """A captured feature/improvement to make to this app itself.

    Promoted from an Idea (or created standalone) once it has a clean
    title + description. Lifecycle is independent of the source idea —
    deleting the idea leaves the FR intact.
    """

    __tablename__ = "feature_requests"

    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(200))
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    # open | in_progress | done | rejected
    status: Mapped[str] = mapped_column(String(16), default="open")
    source_idea_id: Mapped[int | None] = mapped_column(
        ForeignKey("ideas.id", ondelete="SET NULL"), nullable=True
    )
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=_utcnow, onupdate=_utcnow)


class IdeaMessage(Base):
    __tablename__ = "idea_messages"

    id: Mapped[int] = mapped_column(primary_key=True)
    idea_id: Mapped[int] = mapped_column(ForeignKey("ideas.id", ondelete="CASCADE"))
    role: Mapped[str] = mapped_column(String(16))  # user | assistant
    text: Mapped[str] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_utcnow)

    idea: Mapped[Idea] = relationship(back_populates="messages")
