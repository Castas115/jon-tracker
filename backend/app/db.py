from collections.abc import Generator
from pathlib import Path

from sqlalchemy import create_engine, inspect, text
from sqlalchemy.engine import Engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from .config import settings


class Base(DeclarativeBase):
    pass


db_path = settings.database_url.removeprefix("sqlite:///")
if db_path and db_path != ":memory:":
    Path(db_path).parent.mkdir(parents=True, exist_ok=True)

engine = create_engine(settings.database_url, connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(bind=engine, autocommit=False, autoflush=False)


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def ensure_schema(eng: Engine) -> None:
    """Create tables and apply any incremental column additions for SQLite.

    Light-weight migration that lets us add nullable columns without a full
    migration tool. Strict-typed changes still need a real tool (alembic) later.
    """
    Base.metadata.create_all(bind=eng)

    insp = inspect(eng)
    if not insp.has_table("tasks"):
        return

    with eng.begin() as conn:
        existing = {c["name"] for c in insp.get_columns("tasks")}

        if "start_time" not in existing:
            conn.execute(text("ALTER TABLE tasks ADD COLUMN start_time VARCHAR(5)"))
        if "end_time" not in existing:
            conn.execute(text("ALTER TABLE tasks ADD COLUMN end_time VARCHAR(5)"))
        if "task_type" not in existing:
            conn.execute(
                text(
                    "ALTER TABLE tasks ADD COLUMN task_type VARCHAR(16) "
                    "NOT NULL DEFAULT 'recurring'"
                )
            )
        if "weekdays" not in existing:
            conn.execute(text("ALTER TABLE tasks ADD COLUMN weekdays TEXT"))
            # Backfill from the legacy single-weekday column when present.
            if "weekday" in existing:
                conn.execute(
                    text("UPDATE tasks SET weekdays = '[' || weekday || ']' WHERE weekdays IS NULL")
                )
        if "fixed_date" not in existing:
            conn.execute(text("ALTER TABLE tasks ADD COLUMN fixed_date DATE"))
        if "is_todo" not in existing:
            conn.execute(text("ALTER TABLE tasks ADD COLUMN is_todo BOOLEAN NOT NULL DEFAULT 0"))
        if "target_per_week" not in existing:
            conn.execute(text("ALTER TABLE tasks ADD COLUMN target_per_week INTEGER"))
        if "target_segments" not in existing:
            conn.execute(text("ALTER TABLE tasks ADD COLUMN target_segments TEXT"))

        # Rename legacy task_type='fixed' to 'single' (terminology change).
        conn.execute(text("UPDATE tasks SET task_type = 'single' WHERE task_type = 'fixed'"))

        # Legacy single-weekday column. Kept around through a few migrations
        # but the NOT NULL constraint blocks single/birthday inserts. Drop it
        # if SQLite supports it (3.35+).
        if "weekday" in existing:
            try:
                conn.execute(text("ALTER TABLE tasks DROP COLUMN weekday"))
            except Exception:
                # Older SQLite: leave the column. New rows can still satisfy
                # NOT NULL because the model writes 0 by default (see models.py).
                pass
