"""Chaquopy bridge layer for Lumo.

This is the single entry point for Kotlin code. All functions accept and
return JSON-serializable Python native types (str, int, float, bool, list,
dict, None). Senza and Store objects are never exposed.

Kotlin calls: Python.getInstance().getModule("lumo.bridge").callAttr(...)
"""

from __future__ import annotations

import json
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
    return f"{'✅ ' if success else '❌ '}{message}"


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

# ── Chat (Module 1) ──

_chat_session = None



def _maybe_update_title(text: str) -> None:
    """Auto-set session title from the first user message using LLM."""
    if _chat_session is None:
        return
    store = _ensure_store()
    session = store.get_session(_chat_session._session_id)
    if session is None:
        return
    title = (session.get("title") or "").strip()
    if title and title != "新对话":
        return
    # Check if this is the first message (no existing messages)
    history = store.get_messages(_chat_session._session_id)
    if len(history) > 0:
        return
    # Generate title via LLM
    try:
        from lumo.agent import generate_title
        from lumo.config import get_provider_config
        config = get_provider_config(store)
        if config is None:
            return
        new_title = generate_title(config, text)
        store.update_session_title(_chat_session._session_id, new_title)
    except Exception:
        # Fallback: use first 20 chars
        snippet = text.strip().replace("\n", " ")[:20]
        store.update_session_title(_chat_session._session_id, snippet)

def start_chat(session_id: str) -> None:
    """Start a chat session. Creates a ChatSession with the current provider config."""
    global _chat_session
    from lumo.agent import ChatSession
    from lumo.config import get_provider_config

    store = _ensure_store()
    config = get_provider_config(store)
    if config is None:
        raise RuntimeError("Provider not configured. Call save_provider_config first.")
    _chat_session = ChatSession(store, config, session_id)


def start_chat_with_task(session_id: str, task_id: str) -> str:
    """Start a chat session with a learning task context injected.

    Sends an initial AI message introducing the task so the user can
    start learning immediately.
    """
    global _chat_session
    from lumo.agent import ChatSession
    from lumo.config import get_provider_config

    store = _ensure_store()
    config = get_provider_config(store)
    if config is None:
        raise RuntimeError("Provider not configured. Call save_provider_config first.")

    # Get task + plan info
    task = store.get_task(task_id)
    if task is None:
        raise RuntimeError("Task not found")
    plan = store.get_plan(task.get("plan_id", ""))

    # Build task context for system prompt
    task_title = task.get("title", "")
    task_desc = task.get("description", "")
    task_kps = task.get("knowledge_points", "[]")
    plan_goal = plan.get("goal", "") if plan else ""

    # Inject into ChatSession via extra system prompt
    _chat_session = ChatSession(store, config, session_id)

    # Set session title to task title
    store.update_session_title(session_id, task_title[:20])

    # Send initial context message as user (so AI responds with a lesson intro)
    import json as _json
    kp_list = []
    try:
        kp_list = _json.loads(task_kps) if task_kps else []
    except Exception:
        pass

    kp_str = "、".join(kp_list) if kp_list else "相关知识点"
    intro = (
        f"我正在学习计划「{plan_goal}」中的任务：{task_title}。\n"
        f"任务描述：{task_desc}\n"
        f"涉及知识点：{kp_str}\n\n"
        "请作为我的学习教练，帮我开始学习这个任务。先简要介绍这个知识点，然后引导我逐步深入。"
    )
    return _chat_session.send_message(intro)


def send_message(text: str) -> str:
    """Send a message and return the complete response."""
    if _chat_session is None:
        raise RuntimeError("Chat not started. Call start_chat first.")
    _maybe_update_title(text)
    return _chat_session.send_message(text)

def stream_chat(text: str, callback) -> str:
    """Stream a chat response. callback.onToken(text) is called for each token."""
    if _chat_session is None:
        raise RuntimeError("Chat not started. Call start_chat first.")
    _maybe_update_title(text)
    return _chat_session.stream_message(text, on_token=callback.onToken)


def get_chat_history() -> list[dict]:
    """Return persisted chat messages for the current session."""
    if _chat_session is None:
        raise RuntimeError("Chat not started. Call start_chat first.")
    return _chat_session.get_history()


def get_quick_prompts() -> list[dict]:
    """Return quick prompt buttons for the chat UI."""
    from lumo.prompts import QUICK_PROMPTS
    return QUICK_PROMPTS


def abort_chat() -> None:
    """Abort the current streaming response."""
    if _chat_session is not None:
        _chat_session.abort()

# ── Plan Generation (Module 2) ──

def generate_plan(goal: str, daily_minutes: int, week_num: int = 1) -> dict:
    """Run plan generation workflow. Returns dict with weeks, tasks, verified, cost."""
    from lumo.workflows import run_plan_workflow
    from lumo.config import get_provider_config

    store = _ensure_store()
    config = get_provider_config(store)
    if config is None:
        raise RuntimeError("Provider not configured")

    session_dir = os.path.join(_data_dir, "workflows")
    os.makedirs(session_dir, exist_ok=True)

    result = run_plan_workflow(store, config, session_dir, goal, daily_minutes, week_num)

    # Persist the plan and tasks to SQLite
    plan_id = store.create_plan(
        title=goal[:50],
        goal=goal,
        daily_minutes=daily_minutes,
    )
    result["plan_id"] = plan_id

    # Persist tasks from the workflow result
    for task in result.get("tasks", []):
        store.create_task(
            plan_id=plan_id,
            week_num=week_num,
            title=task.get("title", ""),
            description=task.get("description", ""),
            knowledge_points=json.dumps(task.get("knowledge_points", []), ensure_ascii=False),
            day_of_week=task.get("day"),
        )

    return result


def get_plan_tasks(plan_id: str, week_num: int = 0) -> list[dict]:
    """Get tasks for a plan, optionally filtered by week."""
    store = _ensure_store()
    return store.get_tasks(plan_id, week_num if week_num else None)


def update_task_status(task_id: str, status: str) -> None:
    """Update a task's status."""
    _ensure_store().update_task(task_id, status=status)


def reorder_plan_tasks(plan_id: str, task_ids_json: str) -> None:
    """Reorder tasks within a plan. task_ids_json is a JSON array string."""
    task_ids = json.loads(task_ids_json)
    _ensure_store().reorder_tasks(plan_id, task_ids)


def delete_plan(plan_id: str) -> None:
    """Delete a plan and all its tasks."""
    _ensure_store().delete_plan(plan_id)


def update_plan_status(plan_id: str, status: str) -> None:
    """Update a plan's status."""
    _ensure_store().update_plan(plan_id, status=status)


# ── Quiz (Module 4) ──

def generate_quiz(knowledge_points: str, num_questions: int = 3,
                  plan_id: str = "", task_id: str = "") -> dict:
    """Run quiz generation workflow. Returns dict with questions, verified, cost."""
    from lumo.workflows import run_quiz_workflow
    from lumo.config import get_provider_config

    store = _ensure_store()
    config = get_provider_config(store)
    if config is None:
        raise RuntimeError("Provider not configured")

    session_dir = os.path.join(_data_dir, "workflows")
    os.makedirs(session_dir, exist_ok=True)

    result = run_quiz_workflow(
        store, config, session_dir, knowledge_points, num_questions,
        plan_id, task_id,
    )

    # Persist questions to SQLite
    _TYPE_MAP = {
        "single_choice": "single_choice",
        "multi_choice": "multi_choice",
        "true_false": "true_false",
        "short_answer": "short_answer",
        "single": "single_choice",
        "multiple": "multi_choice",
        "multi": "multi_choice",
        "truefalse": "true_false",
        "boolean": "true_false",
        "判断": "true_false",
        "单选": "single_choice",
        "多选": "multi_choice",
        "简答": "short_answer",
        "填空": "short_answer",
    }
    question_ids = []
    for q in result.get("questions", []):
        raw_type = q.get("type", "single_choice")
        normalized_type = _TYPE_MAP.get(raw_type, "single_choice")
        qid = store.create_question(
            question_type=normalized_type,
            question=q.get("question", ""),
            options=json.dumps(q.get("options", []), ensure_ascii=False),
            answer=q.get("answer", ""),
            explanation=q.get("explanation", ""),
            knowledge_points=json.dumps(q.get("knowledge_points", []), ensure_ascii=False),
            plan_id=plan_id,
            task_id=task_id,
        )
        question_ids.append(qid)
    result["question_ids"] = question_ids

    return result


def grade_answer(question_id: str, user_answer: str) -> dict:
    """Grade an answer. Objective questions are graded directly;
    short_answer questions use AI grading."""
    from lumo.agent import grade_short_answer
    from lumo.config import get_provider_config

    store = _ensure_store()
    question = store.get_question(question_id)
    if not question:
        return {"is_correct": False, "explanation": "Question not found"}

    q_type = question.get("question_type", "")
    correct_answer = question.get("answer", "")

    if q_type == "short_answer":
        config = get_provider_config(store)
        if config is None:
            return {"is_correct": False, "explanation": "Provider not configured"}
        result = grade_short_answer(store, config, question_id, user_answer)
        store.record_answer(
            question_id, user_answer,
            is_correct=result["is_correct"],
            error_reason=result.get("explanation", ""),
        )
        _update_mastery_after_answer(store, question, result["is_correct"])
        return result
    else:
        # Objective grading
        is_correct = user_answer.strip().upper() == correct_answer.strip().upper()
        error_reason = "" if is_correct else f"Correct answer: {correct_answer}"
        store.record_answer(question_id, user_answer, is_correct, error_reason)
        _update_mastery_after_answer(store, question, is_correct)
        return {
            "is_correct": is_correct,
            "explanation": question.get("explanation", ""),
        }


def _update_mastery_after_answer(store, question: dict, is_correct: bool) -> None:
    """Update knowledge point mastery and record weak points in memory.

    - Correct answer: +10 mastery (cap 100)
    - Wrong answer: -15 mastery (floor 0), and record to memory as weak point
    """
    import json as _json
    kp_json = question.get("knowledge_points", "[]")
    try:
        kps = _json.loads(kp_json) if isinstance(kp_json, str) else kp_json
    except Exception:
        kps = []
    if not kps:
        return

    plan_id = question.get("plan_id", "")
    if not plan_id:
        return

    for kp_name in kps:
        if not isinstance(kp_name, str) or not kp_name.strip():
            continue
        existing = store.get_kp(plan_id, kp_name)
        if existing:
            current = existing.get("mastery_level", 0)
            if is_correct:
                new_level = min(100, current + 10)
            else:
                new_level = max(0, current - 15)
            store.upsert_kp(plan_id, kp_name, new_level)
        else:
            store.upsert_kp(plan_id, kp_name, 0 if not is_correct else 10)

    # Record weak points to global memory
    if not is_correct:
        try:
            existing_weak = store.read_memory("global", "weak_points") or ""
            weak_set = set(
                w.strip() for w in existing_weak.split(",") if w.strip()
            )
            for kp_name in kps:
                if isinstance(kp_name, str) and kp_name.strip():
                    weak_set.add(kp_name.strip())
            store.write_memory("global", "weak_points", ", ".join(sorted(weak_set)))
        except Exception:
            pass


def get_quiz_questions(plan_id: str = "") -> list[dict]:
    """List quiz questions, optionally filtered by plan."""
    store = _ensure_store()
    return store.list_questions(plan_id if plan_id else None)


def get_quiz_errors() -> list[dict]:
    """Get all wrong answers with question details."""
    return _ensure_store().get_wrong_answers()


# ── Notes AI (Module 5) ──

def ai_summarize_note(note_id: str) -> str:
    """Generate AI summary for a note."""
    from lumo.agent import summarize_note
    from lumo.config import get_provider_config

    store = _ensure_store()
    config = get_provider_config(store)
    if config is None:
        raise RuntimeError("Provider not configured")
    return summarize_note(store, config, note_id)


def summarize_notes(note_ids_json: str, title: str = "") -> str:
    """Summarize multiple notes into one consolidated note.

    Args:
        note_ids_json: JSON string of note IDs, e.g. '["id1", "id2"]'
        title: Optional title for the new note
    """
    import json as _json
    from lumo.agent import summarize_notes as _summarize_notes
    from lumo.config import get_provider_config

    store = _ensure_store()
    config = get_provider_config(store)
    if config is None:
        raise RuntimeError("Provider not configured")

    note_ids = _json.loads(note_ids_json)
    if not note_ids:
        raise ValueError("No note IDs provided")

    summary = _summarize_notes(store, config, note_ids)

    note_title = title or f"笔记汇总 ({len(note_ids)} 篇)"
    note_id = store.create_note(note_title, summary, source="ai_summary")
    return note_id


def save_conversation_as_note(session_id: str, title: str = "") -> str:
    """Summarize a conversation and save/update it as a note.

    One note per session: creates on first save, updates on subsequent saves.
    """
    from lumo.agent import summarize_conversation
    from lumo.config import get_provider_config

    store = _ensure_store()
    config = get_provider_config(store)
    if config is None:
        raise RuntimeError("Provider not configured")

    summary = summarize_conversation(store, config, session_id)

    session = store.get_session(session_id)
    note_title = title or (session["title"] if session else "对话总结")

    # Check if a note already exists for this session
    mem_key = f"note_for_session:{session_id}"
    existing_note_id = store.read_memory("global", mem_key)

    if existing_note_id:
        # Update existing note
        store.update_note(existing_note_id, title=note_title, content=summary)
        return existing_note_id
    else:
        # Create new note
        note_id = store.create_note(note_title, summary, source="conversation")
        store.write_memory("global", mem_key, note_id)
        return note_id


# ── Daily Tasks & Pomodoro (Module 3) ──

def get_today_tasks() -> list[dict]:
    """Get today's tasks across all active plans.

    Returns tasks grouped by plan.
    """
    store = _ensure_store()
    active_plans = store.list_plans(status="active")
    all_tasks = []
    for plan in active_plans:
        tasks = store.get_tasks(plan["id"])
        for task in tasks:
            task["plan_title"] = plan["title"]
            all_tasks.append(task)
    return all_tasks


def record_pomodoro(task_id: str, plan_id: str, duration_seconds: int,
                    started_at: str) -> str:
    """Record a completed pomodoro session."""
    from datetime import datetime, timezone
    ts = started_at or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    return _ensure_store().record_study_session(
        ts, duration_seconds, task_id=task_id, plan_id=plan_id, pomodoro_count=1,
    )


def checkin_today(task_ids=None) -> str:
    """Check in for today with completed task IDs.
    Accepts a JSON string or None.
    """
    from datetime import date
    today = date.today().isoformat()
    if task_ids is None:
        task_ids_json = "[]"
    elif isinstance(task_ids, str):
        task_ids_json = task_ids
    else:
        # Fallback: try to serialize any iterable
        task_ids_json = json.dumps(list(task_ids))
    return _ensure_store().checkin(today, task_ids_json)


# ── Stats (Module 6) ──

def get_stats() -> dict:
    """Get aggregated study statistics."""
    store = _ensure_store()
    return {
        "total_study_time": store.get_total_study_time(),
        "streak": store.get_streak(),
        "study_sessions": store.get_study_sessions(),
    }


def get_study_trend(period: str = "week") -> dict:
    """Get study time trend. period: 'day', 'week', 'month', or 'all'."""
    store = _ensure_store()
    sessions = store.get_study_sessions()

    # Aggregate by period
    from collections import defaultdict
    from datetime import datetime

    totals = defaultdict(int)
    for s in sessions:
        started = s.get("started_at", "")[:10]  # YYYY-MM-DD
        if started:
            totals[started] += s.get("duration_seconds", 0)

    return {
        "period": period,
        "data": dict(sorted(totals.items())),
        "total": sum(totals.values()),
    }


def get_knowledge_mastery(plan_id: str) -> list[dict]:
    """Get knowledge point mastery for a plan."""
    return _ensure_store().list_kps(plan_id)
