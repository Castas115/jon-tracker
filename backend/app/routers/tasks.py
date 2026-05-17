from datetime import UTC, datetime

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session, selectinload

from ..db import get_db
from ..models import Task, TaskCompletion
from ..schemas import CompletionToggle, TaskCreate, TaskRead, TaskUpdate

router = APIRouter(prefix="/tasks", tags=["tasks"])


def _to_read(task: Task) -> TaskRead:
    return TaskRead(
        id=task.id,
        title=task.title,
        description=task.description,
        task_type=task.task_type,  # type: ignore[arg-type]
        weekdays=task.weekdays,
        fixed_date=task.fixed_date,
        start_time=task.start_time,
        end_time=task.end_time,
        is_todo=task.is_todo,
        target_per_week=task.target_per_week,
        target_segments=task.target_segments,  # type: ignore[arg-type]
        show_in_upcoming=task.show_in_upcoming,
        notify_enabled=task.notify_enabled,
        notify_minutes_before=task.notify_minutes_before,
        notify_at=task.notify_at,
        start_date=task.start_date,
        end_date=task.end_date,
        created_at=task.created_at,
        completed_dates=sorted(c.completed_on for c in task.completions),
    )


@router.get("", response_model=list[TaskRead])
def list_tasks(db: Session = Depends(get_db)) -> list[TaskRead]:
    tasks = db.scalars(select(Task).options(selectinload(Task.completions))).all()
    return [_to_read(t) for t in tasks]


@router.post("", response_model=TaskRead, status_code=status.HTTP_201_CREATED)
def create_task(payload: TaskCreate, db: Session = Depends(get_db)) -> TaskRead:
    task = Task(
        title=payload.title,
        description=payload.description,
        task_type=payload.task_type,
        weekdays=payload.weekdays,
        fixed_date=payload.fixed_date,
        start_time=payload.start_time,
        end_time=payload.end_time,
        is_todo=payload.is_todo,
        target_per_week=payload.target_per_week,
        target_segments=(
            [s.model_dump() for s in payload.target_segments] if payload.target_segments else None
        ),
        show_in_upcoming=payload.show_in_upcoming,
        notify_enabled=payload.notify_enabled,
        notify_minutes_before=payload.notify_minutes_before,
        notify_at=payload.notify_at,
        start_date=payload.start_date or datetime.now(UTC).date(),
        end_date=payload.end_date,
    )
    db.add(task)
    db.commit()
    db.refresh(task)
    return _to_read(task)


@router.patch("/{task_id}", response_model=TaskRead)
def update_task(task_id: int, payload: TaskUpdate, db: Session = Depends(get_db)) -> TaskRead:
    task = db.get(Task, task_id)
    if task is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "task not found")
    data = payload.model_dump(exclude_unset=True)
    for field, value in data.items():
        setattr(task, field, value)
    db.commit()
    db.refresh(task)
    return _to_read(task)


@router.delete("/{task_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_task(task_id: int, db: Session = Depends(get_db)) -> None:
    task = db.get(Task, task_id)
    if task is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "task not found")
    db.delete(task)
    db.commit()


@router.post("/{task_id}/toggle", response_model=TaskRead)
def toggle_completion(
    task_id: int, payload: CompletionToggle, db: Session = Depends(get_db)
) -> TaskRead:
    task = db.get(Task, task_id)
    if task is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "task not found")

    existing = db.scalar(
        select(TaskCompletion).where(
            TaskCompletion.task_id == task_id,
            TaskCompletion.completed_on == payload.completed_on,
        )
    )
    # Weekly goals can be completed multiple times on the same day (e.g.
    # meditate twice on Monday counts as 2 toward the target). Other task
    # types stay binary per date — at most one completion row per (task, day).
    is_goal = task.task_type == "weekly_goal"
    if payload.action == "add":
        if is_goal or not existing:
            db.add(TaskCompletion(task_id=task_id, completed_on=payload.completed_on))
    elif payload.action == "remove":
        if existing:
            db.delete(existing)
    else:  # toggle
        if existing:
            db.delete(existing)
        else:
            db.add(TaskCompletion(task_id=task_id, completed_on=payload.completed_on))
    db.commit()
    db.refresh(task)
    return _to_read(task)
