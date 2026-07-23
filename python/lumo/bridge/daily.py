"""Daily tasks, pomodoro, and checkin bridge functions (Module 3)."""

from __future__ import annotations

import json

from lumo.bridge._serialization import to_json


def get_today_tasks() -> str:
    """Get today's tasks across all active plans as JSON.

    Returns tasks with plan_title field added.
    """
    from lumo.bridge import _ensure_store
    store = _ensure_store()
    active_plans = store.list_plans(status="active")
    all_tasks = []
    for plan in active_plans:
        tasks = store.get_tasks(plan["id"])
        for task in tasks:
            task["plan_title"] = plan["title"]
            all_tasks.append(task)
    return to_json(all_tasks)


def record_pomodoro(task_id: str, plan_id: str, duration_seconds: int,
                    started_at: str) -> str:
    """Record a completed pomodoro session. Returns the session ID."""
    from lumo.bridge import _ensure_store
    from datetime import datetime, timezone
    ts = started_at or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    return _ensure_store().record_study_session(
        ts, duration_seconds, task_id=task_id, plan_id=plan_id, pomodoro_count=1,
    )


def checkin_today(task_ids=None) -> str:
    """Check in for today with completed task IDs.

    Accepts a JSON string or None.
    Returns the checkin ID.
    """
    from lumo.bridge import _ensure_store
    from datetime import date
    today = date.today().isoformat()
    if task_ids is None:
        task_ids_json = "[]"
    elif isinstance(task_ids, str):
        task_ids_json = task_ids
    else:
        task_ids_json = json.dumps(list(task_ids))
    return _ensure_store().checkin(today, task_ids_json)
