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
        task_type=task.task_type,  # type: ignore[arg-type]
        weekdays=task.weekdays,
        fixed_date=task.fixed_date,
        start_time=task.start_time,
        end_time=task.end_time,
        is_todo=task.is_todo,
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
        task_type=payload.task_type,
        weekdays=payload.weekdays,
        fixed_date=payload.fixed_date,
        start_time=payload.start_time,
        end_time=payload.end_time,
        is_todo=payload.is_todo,
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
    if existing:
        db.delete(existing)
    else:
        db.add(TaskCompletion(task_id=task_id, completed_on=payload.completed_on))
    db.commit()
    db.refresh(task)
    return _to_read(task)
