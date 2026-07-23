from __future__ import annotations

"""Memory and settings storage."""

import uuid
import sqlite3


class MemoryStore:
    """Manages memory and settings tables."""

    def __init__(self, conn: sqlite3.Connection, has_fts5: bool = True):
        self._conn = conn
        self._has_fts5 = has_fts5

    def read_memory(self, scope: str, key: str) -> str | None:
        with self._conn:
            row = self._conn.execute(
                "SELECT value FROM memory WHERE scope = ? AND key = ?",
                (scope, key),
            ).fetchone()
            return row["value"] if row else None

    def write_memory(self, scope: str, key: str, value: str) -> None:
        with self._conn:
            self._conn.execute(
                """INSERT INTO memory (id, scope, key, value) VALUES (?, ?, ?, ?)
                   ON CONFLICT(scope, key) DO UPDATE SET value=excluded.value,
                   updated_at=strftime('%Y-%m-%dT%H:%M:%SZ', 'now')""",
                (str(uuid.uuid4()), scope, key, value),
            )

    def list_memory(self, scope: str) -> list[dict]:
        with self._conn:
            rows = self._conn.execute(
                "SELECT key, value, updated_at FROM memory WHERE scope = ? ORDER BY key",
                (scope,),
            ).fetchall()
            return [dict(r) for r in rows]

    # ── Settings CRUD ──

    def get_setting(self, key: str) -> str | None:
        with self._conn:
            row = self._conn.execute(
                "SELECT value FROM settings WHERE key = ?", (key,)
            ).fetchone()
            return row["value"] if row else None

    def set_setting(self, key: str, value: str) -> None:
        with self._conn:
            self._conn.execute(
                """INSERT INTO settings (key, value) VALUES (?, ?)
                   ON CONFLICT(key) DO UPDATE SET value=excluded.value,
                   updated_at=strftime('%Y-%m-%dT%H:%M:%SZ', 'now')""",
                (key, value),
            )

    def get_all_settings(self) -> dict[str, str]:
        with self._conn:
            rows = self._conn.execute("SELECT key, value FROM settings").fetchall()
            return {r["key"]: r["value"] for r in rows}
