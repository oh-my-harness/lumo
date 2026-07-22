"""Chaquopy bridge layer for Lumo.

This is the single entry point for Kotlin code. All functions accept and
return JSON-serializable Python native types (str, int, float, bool, list,
dict, None). Senza and Store objects are never exposed.

Kotlin calls: Python.getInstance().getModule("lumo.bridge").callAttr(...)
"""

from __future__ import annotations

import os
from typing import Optional

from lumo.store import Store
from lumo.config import (
    ProviderConfig,
    get_provider_config as _get_provider_config,
    save_provider_config as _save_provider_config,
    create_provider as _create_provider,
    test_connection as _test_connection,
)

_store: Optional[Store] = None
_data_dir: str = ""


def init(data_dir: str) -> None:
    """Initialize the bridge with a writable data directory.

    Creates the directory and opens/creates the SQLite database.
    Must be called once at app startup before any other bridge function.
    """
    global _store, _data_dir
    _data_dir = data_dir
    os.makedirs(data_dir, exist_ok=True)
    db_path = os.path.join(data_dir, "lumo.db")
    _store = Store(db_path)


def get_store() -> Store:
    """Get the singleton Store instance. Raises if init() not called."""
    if _store is None:
        raise RuntimeError("bridge.init() must be called first")
    return _store


def _ensure_store() -> Store:
    if _store is None:
        raise RuntimeError("bridge.init() must be called first")
    return _store


# ── Provider Config (Module 7) ──

def save_provider_config(
    provider_type: str, api_key: str, base_url: str, model: str,
) -> None:
    """Save LLM provider configuration."""
    config = ProviderConfig(
        provider_type=provider_type,
        api_key=api_key,
        base_url=base_url,
        model=model,
    )
    _save_provider_config(_ensure_store(), config)


def get_provider_config() -> dict | None:
    """Load provider config as dict, or None if not configured."""
    config = _get_provider_config(_ensure_store())
    return config.to_dict() if config else None


def test_provider_connection(
    provider_type: str, api_key: str, base_url: str, model: str,
) -> str:
    """Test LLM connection. Returns a status message."""
    config = ProviderConfig(
        provider_type=provider_type,
        api_key=api_key,
        base_url=base_url,
        model=model,
    )
    success, message = _test_connection(config)
    return f"{'OK' if success else 'FAIL'}: {message}"


# ── Sessions (Module 1) ──

def create_session(title: str = "") -> str:
    return _ensure_store().create_session(title)


def list_sessions() -> list[dict]:
    return _ensure_store().list_sessions()


def get_messages(session_id: str) -> list[dict]:
    return _ensure_store().get_messages(session_id)


def delete_session(session_id: str) -> None:
    _ensure_store().delete_session(session_id)


def update_session_title(session_id: str, title: str) -> None:
    _ensure_store().update_session_title(session_id, title)


def search_messages(query: str) -> list[dict]:
    return _ensure_store().search_messages(query)


# ── Plans (Module 2) ──

def list_plans(status: str = "") -> list[dict]:
    store = _ensure_store()
    return store.list_plans(status if status else None)


def create_plan(
    title: str, goal: str, daily_minutes: int = 60,
    start_date: str = "", end_date: str = "",
) -> str:
    return _ensure_store().create_plan(
        title, goal, daily_minutes, start_date, end_date
    )


def get_plan(plan_id: str) -> dict | None:
    return _ensure_store().get_plan(plan_id)


def update_plan(plan_id: str, **fields) -> None:
    _ensure_store().update_plan(plan_id, **fields)


def delete_plan(plan_id: str) -> None:
    _ensure_store().delete_plan(plan_id)


# ── Tasks (Module 2/3) ──

def get_tasks(plan_id: str, week_num: int = 0) -> list[dict]:
    store = _ensure_store()
    return store.get_tasks(plan_id, week_num if week_num else None)


def update_task(task_id: str, **fields) -> None:
    _ensure_store().update_task(task_id, **fields)


def delete_task(task_id: str) -> None:
    _ensure_store().delete_task(task_id)


# ── Notes (Module 5) ──

def list_notes(folder_id: str = "") -> list[dict]:
    store = _ensure_store()
    return store.list_notes(folder_id if folder_id else None)


def search_notes(query: str) -> list[dict]:
    return _ensure_store().search_notes(query)


def create_note(
    title: str, content: str = "", folder_id: str = "",
    source: str = "manual", linked_kp: str = "",
) -> str:
    return _ensure_store().create_note(
        title, content, folder_id, source, linked_kp
    )


def update_note(note_id: str, title: str = "", content: str = "") -> None:
    fields = {}
    if title:
        fields["title"] = title
    if content:
        fields["content"] = content
    if fields:
        _ensure_store().update_note(note_id, **fields)


def delete_note(note_id: str) -> None:
    _ensure_store().delete_note(note_id)


# ── Folders (Module 5) ──

def list_folders() -> list[dict]:
    return _ensure_store().list_folders()


def create_folder(name: str, parent_id: str = "") -> str:
    return _ensure_store().create_folder(name, parent_id)


# ── Quiz (Module 4) ──

def list_questions(plan_id: str = "") -> list[dict]:
    store = _ensure_store()
    return store.list_questions(plan_id if plan_id else None)


def get_wrong_answers() -> list[dict]:
    return _ensure_store().get_wrong_answers()


def record_answer(
    question_id: str, user_answer: str, is_correct: bool,
    error_reason: str = "",
) -> str:
    return _ensure_store().record_answer(
        question_id, user_answer, is_correct, error_reason
    )


# ── Study & Stats (Module 3/6) ──

def get_total_study_time() -> int:
    return _ensure_store().get_total_study_time()


def get_streak() -> int:
    return _ensure_store().get_streak()


def get_checkin_heatmap(month: str) -> list[dict]:
    return _ensure_store().get_checkin_heatmap(month)


def get_study_sessions(plan_id: str = "") -> list[dict]:
    store = _ensure_store()
    return store.get_study_sessions(plan_id if plan_id else None)


# ── Knowledge Points (Module 2/6) ──

def list_kps(plan_id: str) -> list[dict]:
    return _ensure_store().list_kps(plan_id)
