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
        weekday=task.weekday,
        created_at=task.created_at,
        completed_dates=sorted(c.completed_on for c in task.completions),
    )


@router.get("", response_model=list[TaskRead])
def list_tasks(db: Session = Depends(get_db)) -> list[TaskRead]:
    tasks = db.scalars(select(Task).options(selectinload(Task.completions))).all()
    return [_to_read(t) for t in tasks]


@router.post("", response_model=TaskRead, status_code=status.HTTP_201_CREATED)
def create_task(payload: TaskCreate, db: Session = Depends(get_db)) -> TaskRead:
    task = Task(title=payload.title, weekday=payload.weekday)
    db.add(task)
    db.commit()
    db.refresh(task)
    return _to_read(task)


@router.patch("/{task_id}", response_model=TaskRead)
def update_task(task_id: int, payload: TaskUpdate, db: Session = Depends(get_db)) -> TaskRead:
    task = db.get(Task, task_id)
    if task is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "task not found")
    if payload.title is not None:
        task.title = payload.title
    if payload.weekday is not None:
        task.weekday = payload.weekday
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
    if existing:
        db.delete(existing)
    else:
        db.add(TaskCompletion(task_id=task_id, completed_on=payload.completed_on))
    db.commit()
    db.refresh(task)
    return _to_read(task)
