"""Chat bridge functions (Module 1)."""

from __future__ import annotations

import json

from lumo.bridge._serialization import to_json


_chat_session = None


def _maybe_update_title(text: str) -> None:
    """Auto-set session title from the first user message using LLM."""
    if _chat_session is None:
        return
    from lumo.bridge import _ensure_store
    store = _ensure_store()
    session = store.get_session(_chat_session._session_id)
    if session is None:
        return
    title = (session.get("title") or "").strip()
    if title and title != "新对话":
        return
    history = store.get_messages(_chat_session._session_id)
    if len(history) > 0:
        return
    try:
        from lumo.agent import generate_title
        from lumo.config import get_provider_config
        config = get_provider_config(store)
        if config is None:
            return
        new_title = generate_title(config, text)
        store.update_session_title(_chat_session._session_id, new_title)
    except Exception:
        snippet = text.strip().replace("\n", " ")[:20]
        store.update_session_title(_chat_session._session_id, snippet)


def start_chat(session_id: str) -> None:
    """Start a chat session. Creates a ChatSession with the current provider config."""
    global _chat_session
    from lumo.bridge import _ensure_store
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
    from lumo.bridge import _ensure_store
    from lumo.agent import ChatSession
    from lumo.config import get_provider_config

    store = _ensure_store()
    config = get_provider_config(store)
    if config is None:
        raise RuntimeError("Provider not configured. Call save_provider_config first.")

    task = store.get_task(task_id)
    if task is None:
        raise RuntimeError("Task not found")
    plan = store.get_plan(task.get("plan_id", ""))

    task_title = task.get("title", "")
    task_desc = task.get("description", "")
    task_kps = task.get("knowledge_points", "[]")
    plan_goal = plan.get("goal", "") if plan else ""

    _chat_session = ChatSession(store, config, session_id)
    store.update_session_title(session_id, task_title[:20])

    kp_list = []
    try:
        kp_list = json.loads(task_kps) if task_kps else []
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
    # Persist the user message before sending (after_turn_hook may only
    # capture assistant replies depending on the Senza harness version).
    from lumo.bridge import _ensure_store
    _ensure_store().add_message(_chat_session._session_id, "user", text)
    return _chat_session.send_message(text)


def stream_chat(text: str, callback) -> str:
    """Stream a chat response. callback.onToken(text) is called for each token."""
    if _chat_session is None:
        raise RuntimeError("Chat not started. Call start_chat first.")
    _maybe_update_title(text)
    # Persist the user message before sending.
    from lumo.bridge import _ensure_store
    _ensure_store().add_message(_chat_session._session_id, "user", text)
    return _chat_session.stream_message(text, on_token=callback.onToken)


def get_chat_history() -> str:
    """Return persisted chat messages for the current session as JSON."""
    if _chat_session is None:
        raise RuntimeError("Chat not started. Call start_chat first.")
    return to_json(_chat_session.get_history())


def get_quick_prompts() -> str:
    """Return quick prompt buttons for the chat UI as JSON."""
    from lumo.prompts import QUICK_PROMPTS
    return to_json(QUICK_PROMPTS)


def abort_chat() -> None:
    """Abort the current streaming response."""
    if _chat_session is not None:
        _chat_session.abort()


# ── Session CRUD (also used by chat list UI) ──

def create_session(title: str = "") -> str:
    """Create a new session, return its ID."""
    from lumo.bridge import _ensure_store
    return _ensure_store().create_session(title)


def list_sessions() -> str:
    """List all sessions as JSON."""
    from lumo.bridge import _ensure_store
    return to_json(_ensure_store().list_sessions())


def get_messages(session_id: str) -> str:
    """Get messages for a session as JSON."""
    from lumo.bridge import _ensure_store
    return to_json(_ensure_store().get_messages(session_id))


def delete_session(session_id: str) -> None:
    """Delete a session."""
    from lumo.bridge import _ensure_store
    _ensure_store().delete_session(session_id)


def update_session_title(session_id: str, title: str) -> None:
    """Update session title."""
    from lumo.bridge import _ensure_store
    _ensure_store().update_session_title(session_id, title)


def search_messages(query: str) -> str:
    """Search messages, return results as JSON."""
    from lumo.bridge import _ensure_store
    return to_json(_ensure_store().search_messages(query))


def save_conversation_as_note(session_id: str, title: str = "") -> str:
    """Summarize a conversation and save/update it as a note.

    One note per session: creates on first save, updates on subsequent saves.
    Returns the note ID.
    """
    from lumo.bridge import _ensure_store
    from lumo.agent import summarize_conversation
    from lumo.config import get_provider_config

    store = _ensure_store()
    config = get_provider_config(store)
    if config is None:
        raise RuntimeError("Provider not configured")

    summary = summarize_conversation(store, config, session_id)

    session = store.get_session(session_id)
    note_title = title or (session["title"] if session else "对话总结")

    mem_key = f"note_for_session:{session_id}"
    existing_note_id = store.read_memory("global", mem_key)

    if existing_note_id:
        store.update_note(existing_note_id, title=note_title, content=summary)
        return existing_note_id
    else:
        note_id = store.create_note(note_title, summary, source="conversation")
        store.write_memory("global", mem_key, note_id)
        return note_id
