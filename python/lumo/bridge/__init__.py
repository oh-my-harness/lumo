"""Chaquopy bridge layer for Lumo.

This package is the entry point for all Kotlin → Python calls.
The Kotlin side accesses it via `py.getModule("lumo.bridge")`.

All public functions are re-exported here for backward compatibility.
Functions that return data now return JSON strings instead of raw dicts,
enabling type-safe deserialization on the Kotlin side.
"""

from __future__ import annotations

import os
from typing import Optional

from lumo.store import Store

# ── Singleton state ──

_store: Optional[Store] = None
_data_dir: str = ""


def _get_data_dir() -> str:
    """Get the current data directory. Raises if init() not called."""
    if not _data_dir:
        raise RuntimeError("bridge.init() must be called first")
    return _data_dir


def init(data_dir: str) -> None:
    """Initialize the bridge with a writable data directory.

    Creates the directory if needed and opens/creates the SQLite database.
    """
    global _store, _data_dir
    os.makedirs(data_dir, exist_ok=True)
    db_path = os.path.join(data_dir, "lumo.db")
    _data_dir = data_dir
    _store = Store(db_path)


def get_store() -> Store:
    """Get the singleton Store instance. Raises if init() not called."""
    if _store is None:
        raise RuntimeError("bridge.init() must be called first")
    return _store


def _ensure_store() -> Store:
    """Get the singleton Store instance. Raises if init() not called."""
    if _store is None:
        raise RuntimeError("bridge.init() must be called first")
    return _store


# ── Re-export all public bridge functions ──

from lumo.bridge.provider import (  # noqa: E402, F401
    save_provider_config,
    get_provider_config,
    test_provider_connection,
)

from lumo.bridge.chat import (  # noqa: E402, F401
    create_session,
    list_sessions,
    get_messages,
    delete_session,
    update_session_title,
    search_messages,
    start_chat,
    start_chat_with_task,
    send_message,
    stream_chat,
    get_chat_history,
    get_quick_prompts,
    abort_chat,
    save_conversation_as_note,
)

from lumo.bridge.plans import (  # noqa: E402, F401
    list_plans,
    create_plan,
    get_plan,
    update_plan,
    delete_plan,
    get_tasks,
    update_task,
    delete_task,
    generate_plan,
    get_plan_tasks,
    update_task_status,
    reorder_plan_tasks,
    update_plan_status,
)

from lumo.bridge.quiz import (  # noqa: E402, F401
    list_questions,
    get_wrong_answers,
    record_answer,
    generate_quiz,
    grade_answer,
    get_quiz_questions,
    get_quiz_errors,
)

from lumo.bridge.notes import (  # noqa: E402, F401
    list_notes,
    search_notes,
    create_note,
    update_note,
    delete_note,
    list_folders,
    create_folder,
    ai_summarize_note,
    summarize_notes,
)

from lumo.bridge.daily import (  # noqa: E402, F401
    get_today_tasks,
    record_pomodoro,
    checkin_today,
)

from lumo.bridge.stats import (  # noqa: E402, F401
    get_total_study_time,
    get_streak,
    get_checkin_heatmap,
    get_study_sessions,
    list_kps,
    get_stats,
    get_study_trend,
    get_knowledge_mastery,
)
