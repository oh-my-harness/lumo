"""Notes bridge functions (Module 5)."""

from __future__ import annotations

import json

from lumo.bridge._serialization import to_json


def list_notes(folder_id: str = "") -> str:
    """List notes as JSON. Optional folder_id filter."""
    from lumo.bridge import _ensure_store
    store = _ensure_store()
    return to_json(store.list_notes(folder_id if folder_id else None))


def search_notes(query: str) -> str:
    """Search notes, return results as JSON."""
    from lumo.bridge import _ensure_store
    return to_json(_ensure_store().search_notes(query))


def create_note(
    title: str, content: str = "", folder_id: str = "",
    source: str = "manual", linked_kp: str = "",
) -> str:
    """Create a note, return its ID."""
    from lumo.bridge import _ensure_store
    return _ensure_store().create_note(
        title, content, folder_id, source, linked_kp
    )


def update_note(note_id: str, title: str = "", content: str = "") -> None:
    """Update a note's title and/or content."""
    from lumo.bridge import _ensure_store
    fields = {}
    if title:
        fields["title"] = title
    if content:
        fields["content"] = content
    if fields:
        _ensure_store().update_note(note_id, **fields)


def delete_note(note_id: str) -> None:
    """Delete a note."""
    from lumo.bridge import _ensure_store
    _ensure_store().delete_note(note_id)


def list_folders() -> str:
    """List folders as JSON."""
    from lumo.bridge import _ensure_store
    return to_json(_ensure_store().list_folders())


def create_folder(name: str, parent_id: str = "") -> str:
    """Create a folder, return its ID."""
    from lumo.bridge import _ensure_store
    return _ensure_store().create_folder(name, parent_id)


def ai_summarize_note(note_id: str) -> str:
    """Generate AI summary for a note. Returns the summary text."""
    from lumo.bridge import _ensure_store
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

    Returns the new note ID.
    """
    from lumo.bridge import _ensure_store
    from lumo.agent import summarize_notes as _summarize_notes
    from lumo.config import get_provider_config

    store = _ensure_store()
    config = get_provider_config(store)
    if config is None:
        raise RuntimeError("Provider not configured")

    note_ids = json.loads(note_ids_json)
    if not note_ids:
        raise ValueError("No note IDs provided")

    summary = _summarize_notes(store, config, note_ids)

    note_title = title or f"笔记汇总 ({len(note_ids)} 篇)"
    note_id = store.create_note(note_title, summary, source="ai_summary")
    return note_id
