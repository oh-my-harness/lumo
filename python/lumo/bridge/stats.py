"""Stats bridge functions (Module 6)."""

from __future__ import annotations

from collections import defaultdict
from datetime import datetime

from lumo.bridge._serialization import to_json


def get_total_study_time() -> int:
    """Get total study time in seconds."""
    from lumo.bridge import _ensure_store
    return _ensure_store().get_total_study_time()


def get_streak() -> int:
    """Get current streak in days."""
    from lumo.bridge import _ensure_store
    return _ensure_store().get_streak()


def get_checkin_heatmap(month: str) -> str:
    """Get checkin heatmap for a month as JSON."""
    from lumo.bridge import _ensure_store
    return to_json(_ensure_store().get_checkin_heatmap(month))


def get_study_sessions(plan_id: str = "") -> str:
    """Get study sessions as JSON. Optional plan_id filter."""
    from lumo.bridge import _ensure_store
    store = _ensure_store()
    return to_json(store.get_study_sessions(plan_id if plan_id else None))


def list_kps(plan_id: str) -> str:
    """List knowledge points for a plan as JSON."""
    from lumo.bridge import _ensure_store
    return to_json(_ensure_store().list_kps(plan_id))


def get_stats() -> str:
    """Get aggregated study statistics as JSON."""
    from lumo.bridge import _ensure_store
    store = _ensure_store()
    return to_json({
        "total_study_time": store.get_total_study_time(),
        "streak": store.get_streak(),
        "study_sessions": store.get_study_sessions(),
    })


def get_study_trend(period: str = "week") -> str:
    """Get study time trend. period: 'day', 'week', 'month', or 'all'.

    Returns JSON with period, data (date→seconds), and total.
    """
    from lumo.bridge import _ensure_store
    store = _ensure_store()
    sessions = store.get_study_sessions()

    totals = defaultdict(int)
    for s in sessions:
        started = s.get("started_at", "")[:10]
        if started:
            totals[started] += s.get("duration_seconds", 0)

    return to_json({
        "period": period,
        "data": dict(sorted(totals.items())),
        "total": sum(totals.values()),
    })


def get_knowledge_mastery(plan_id: str) -> str:
    """Get knowledge point mastery for a plan as JSON."""
    from lumo.bridge import _ensure_store
    return to_json(_ensure_store().list_kps(plan_id))
