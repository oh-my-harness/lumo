"""JSON serialization utilities for the bridge layer."""

from __future__ import annotations

import json


def to_json(obj) -> str:
    """Serialize any JSON-serializable object to a string."""
    return json.dumps(obj, ensure_ascii=False, default=str)


def to_json_or_error(obj) -> str:
    """Serialize to JSON, wrapping exceptions as {"_error": ...}."""
    try:
        return json.dumps(obj, ensure_ascii=False, default=str)
    except Exception as e:
        return json.dumps({"_error": str(e)}, ensure_ascii=False)
