"""Senza tool definitions for Lumo AI tutor.

Four tools for P0:
- read_memory: read learner profile from memory store
- write_memory: update learner profile
- search_notes: FTS5 search over notes
- search_quiz_errors: search wrong quiz answers

Each tool is created via a factory that closes over the Store instance.
The callback factories are also exported for unit testing.
"""

import json

import senza


# ── Callback factories (exported for testing) ──

def make_read_memory_callback(store):
    """Create the read_memory callback, closing over store."""
    def callback(args, ctx):
        scope = args.get("scope", "global")
        key = args.get("key", "")
        value = store.read_memory(scope, key)
        if value is not None:
            text = f"[Memory: {scope}/{key}] {value}"
        else:
            text = f"[Memory: {scope}/{key}] No memory found for this key."
        return {"content": [{"type": "text", "text": text}], "terminate": False}
    return callback


def make_write_memory_callback(store):
    """Create the write_memory callback, closing over store."""
    def callback(args, ctx):
        scope = args.get("scope", "global")
        key = args.get("key", "")
        value = args.get("value", "")
        store.write_memory(scope, key, value)
        text = f"[Memory saved: {scope}/{key}]"
        return {"content": [{"type": "text", "text": text}], "terminate": False}
    return callback


def make_search_notes_callback(store):
    """Create the search_notes callback, closing over store."""
    def callback(args, ctx):
        query = args.get("query", "")
        results = store.search_notes(query)
        if results:
            lines = [f"Found {len(results)} notes:"]
            for note in results:
                title = note.get("title", "")
                content = note.get("content", "")[:200]
                lines.append(f"- **{title}**: {content}")
            text = "\n".join(lines)
        else:
            text = "No notes found matching the query."
        return {"content": [{"type": "text", "text": text}], "terminate": False}
    return callback


def make_search_quiz_errors_callback(store):
    """Create the search_quiz_errors callback, closing over store."""
    def callback(args, ctx):
        query = args.get("query", "")
        wrong_answers = store.get_wrong_answers()
        filtered = []
        for wa in wrong_answers:
            question = wa.get("question", "").lower()
            kp = wa.get("knowledge_points", "").lower()
            if query.lower() in question or query.lower() in kp:
                filtered.append(wa)
        if filtered:
            lines = [f"Found {len(filtered)} related wrong answers:"]
            for wa in filtered:
                q = wa.get("question", "")
                ua = wa.get("user_answer", "")
                ca = wa.get("correct_answer", "")
                reason = wa.get("error_reason", "")
                lines.append(
                    f"- Q: {q}\n  Your answer: {ua} | Correct: {ca}\n  Reason: {reason}"
                )
            text = "\n".join(lines)
        else:
            text = "No related quiz errors found."
        return {"content": [{"type": "text", "text": text}], "terminate": False}
    return callback


# ── Tool factories (wrap callbacks in Senza Tool objects) ──

def _make_read_memory(store):
    return senza.create_tool(
        name="read_memory",
        description=(
            "Read a memory entry for the learner. Use scope='global' for "
            "cross-plan memory, or a plan_id for plan-specific memory."
        ),
        parameters_schema=json.dumps({
            "type": "object",
            "properties": {
                "scope": {
                    "type": "string",
                    "description": "Memory scope: 'global' or a plan_id",
                    "default": "global",
                },
                "key": {
                    "type": "string",
                    "description": "Memory key to read, e.g. 'learning_style', 'weak_points'",
                },
            },
            "required": ["key"],
        }),
        callback=make_read_memory_callback(store),
    )


def _make_write_memory(store):
    return senza.create_tool(
        name="write_memory",
        description=(
            "Write or update a memory entry for the learner. Use this to "
            "record learning style, weak points, progress, etc."
        ),
        parameters_schema=json.dumps({
            "type": "object",
            "properties": {
                "scope": {
                    "type": "string",
                    "description": "Memory scope: 'global' or a plan_id",
                    "default": "global",
                },
                "key": {
                    "type": "string",
                    "description": "Memory key, e.g. 'learning_style', 'weak_points'",
                },
                "value": {
                    "type": "string",
                    "description": "Memory value to store",
                },
            },
            "required": ["key", "value"],
        }),
        callback=make_write_memory_callback(store),
    )


def _make_search_notes(store):
    return senza.create_tool(
        name="search_notes",
        description=(
            "Search the learner's notes by keyword. Returns matching notes "
            "with title and content preview."
        ),
        parameters_schema=json.dumps({
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "Search query",
                },
            },
            "required": ["query"],
        }),
        callback=make_search_notes_callback(store),
    )


def _make_search_quiz_errors(store):
    return senza.create_tool(
        name="search_quiz_errors",
        description=(
            "Search the learner's quiz error history by keyword. Returns "
            "wrong answers with the correct answer and error reason."
        ),
        parameters_schema=json.dumps({
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "Search query to filter errors by question or knowledge point",
                },
            },
            "required": ["query"],
        }),
        callback=make_search_quiz_errors_callback(store),
    )


def create_lumo_tools(store) -> list:
    """Create all 4 P0 tools for the Lumo tutor."""
    return [
        _make_read_memory(store),
        _make_write_memory(store),
        _make_search_notes(store),
        _make_search_quiz_errors(store),
    ]
