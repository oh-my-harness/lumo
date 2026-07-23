"""Plan and task bridge functions (Module 2)."""

from __future__ import annotations

import json
import os

from lumo.bridge._serialization import to_json


def list_plans(status: str = "") -> str:
    """List plans as JSON. Optional status filter."""
    from lumo.bridge import _ensure_store
    store = _ensure_store()
    return to_json(store.list_plans(status if status else None))


def create_plan(
    title: str, goal: str, daily_minutes: int = 60,
    start_date: str = "", end_date: str = "",
) -> str:
    """Create a plan, return its ID."""
    from lumo.bridge import _ensure_store
    return _ensure_store().create_plan(
        title, goal, daily_minutes, start_date, end_date
    )


def get_plan(plan_id: str) -> str:
    """Get a plan as JSON, or "null"."""
    from lumo.bridge import _ensure_store
    plan = _ensure_store().get_plan(plan_id)
    return to_json(plan) if plan else "null"


def update_plan(plan_id: str, **fields) -> None:
    """Update plan fields."""
    from lumo.bridge import _ensure_store
    _ensure_store().update_plan(plan_id, **fields)


def delete_plan(plan_id: str) -> None:
    """Delete a plan and all its tasks."""
    from lumo.bridge import _ensure_store
    _ensure_store().delete_plan(plan_id)


def get_tasks(plan_id: str, week_num: int = 0) -> str:
    """Get tasks for a plan as JSON. Optional week filter."""
    from lumo.bridge import _ensure_store
    store = _ensure_store()
    return to_json(store.get_tasks(plan_id, week_num if week_num else None))


def update_task(task_id: str, **fields) -> None:
    """Update task fields."""
    from lumo.bridge import _ensure_store
    _ensure_store().update_task(task_id, **fields)


def delete_task(task_id: str) -> None:
    """Delete a task."""
    from lumo.bridge import _ensure_store
    _ensure_store().delete_task(task_id)


def generate_plan(goal: str, daily_minutes: int, week_num: int = 1) -> str:
    """Run plan generation workflow. Returns JSON with weeks, tasks, verified, cost."""
    from lumo.bridge import _ensure_store, _get_data_dir
    from lumo.workflows import run_plan_workflow
    from lumo.config import get_provider_config

    store = _ensure_store()
    config = get_provider_config(store)
    if config is None:
        raise RuntimeError("Provider not configured")

    session_dir = os.path.join(_get_data_dir(), "workflows")
    os.makedirs(session_dir, exist_ok=True)

    result = run_plan_workflow(store, config, session_dir, goal, daily_minutes, week_num)

    plan_id = store.create_plan(
        title=goal[:50],
        goal=goal,
        daily_minutes=daily_minutes,
    )
    result["plan_id"] = plan_id

    for task in result.get("tasks", []):
        store.create_task(
            plan_id=plan_id,
            week_num=week_num,
            title=task.get("title", ""),
            description=task.get("description", ""),
            knowledge_points=json.dumps(task.get("knowledge_points", []), ensure_ascii=False),
            day_of_week=task.get("day"),
        )

    return to_json(result)


def get_plan_tasks(plan_id: str, week_num: int = 0) -> str:
    """Get tasks for a plan as JSON. Optional week filter."""
    from lumo.bridge import _ensure_store
    store = _ensure_store()
    return to_json(store.get_tasks(plan_id, week_num if week_num else None))


def update_task_status(task_id: str, status: str) -> None:
    """Update a task's status."""
    from lumo.bridge import _ensure_store
    _ensure_store().update_task(task_id, status=status)


def reorder_plan_tasks(plan_id: str, task_ids_json: str) -> None:
    """Reorder tasks within a plan. task_ids_json is a JSON array string."""
    from lumo.bridge import _ensure_store
    task_ids = json.loads(task_ids_json)
    _ensure_store().reorder_tasks(plan_id, task_ids)



def update_plan_status(plan_id: str, status: str) -> None:
    """Update a plan's status."""
    from lumo.bridge import _ensure_store
    _ensure_store().update_plan(plan_id, status=status)
