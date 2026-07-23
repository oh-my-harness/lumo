from __future__ import annotations

"""Memory and settings storage."""

import uuid
import sqlite3


class MemoryStore:
    """Manages memory and settings tables."""

    def __init__(self, db_path: str, has_fts5: bool = True):
        self._db_path = db_path
        self._has_fts5 = has_fts5

    def _new_conn(self) -> sqlite3.Connection:
        """Create a new connection with proper settings."""
        conn = sqlite3.connect(self._db_path)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys=ON")
        return conn

    def read_memory(self, scope: str, key: str) -> str | None:
        with self._new_conn() as conn:
            row = conn.execute(
                "SELECT value FROM memory WHERE scope = ? AND key = ?",
                (scope, key),
            ).fetchone()
            return row["value"] if row else None

    def write_memory(self, scope: str, key: str, value: str) -> None:
        with self._new_conn() as conn:
            conn.execute(
                """INSERT INTO memory (id, scope, key, value) VALUES (?, ?, ?, ?)
                   ON CONFLICT(scope, key) DO UPDATE SET value=excluded.value,
                   updated_at=strftime('%Y-%m-%dT%H:%M:%SZ', 'now')""",
                (str(uuid.uuid4()), scope, key, value),
            )

    def list_memory(self, scope: str) -> list[dict]:
        with self._new_conn() as conn:
            rows = conn.execute(
                "SELECT key, value, updated_at FROM memory WHERE scope = ? ORDER BY key",
                (scope,),
            ).fetchall()
            return [dict(r) for r in rows]

    # ── Settings CRUD ──

    def get_setting(self, key: str) -> str | None:
        with self._new_conn() as conn:
            row = conn.execute(
                "SELECT value FROM settings WHERE key = ?", (key,)
            ).fetchone()
            return row["value"] if row else None

    def set_setting(self, key: str, value: str) -> None:
        with self._new_conn() as conn:
            conn.execute(
                """INSERT INTO settings (key, value) VALUES (?, ?)
                   ON CONFLICT(key) DO UPDATE SET value=excluded.value,
                   updated_at=strftime('%Y-%m-%dT%H:%M:%SZ', 'now')""",
                (key, value),
            )

    def get_all_settings(self) -> dict[str, str]:
        with self._new_conn() as conn:
            rows = conn.execute("SELECT key, value FROM settings").fetchall()
            return {r["key"]: r["value"] for r in rows}
