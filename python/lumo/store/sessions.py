from __future__ import annotations

"""Session and message storage."""

import uuid
import sqlite3


class SessionStore:
    """Manages sessions and messages tables, including FTS5 search."""

    def __init__(self, db_path: str, has_fts5: bool = True):
        self._db_path = db_path
        self._has_fts5 = has_fts5

    def _new_conn(self) -> sqlite3.Connection:
        """Create a new connection with proper settings."""
        conn = sqlite3.connect(self._db_path)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys=ON")
        return conn

    def create_session(self, title: str = "") -> str:
        sid = str(uuid.uuid4())
        with self._new_conn() as conn:
            conn.execute(
                "INSERT INTO sessions (id, title) VALUES (?, ?)",
                (sid, title),
            )
        return sid

    def list_sessions(self) -> list[dict]:
        with self._new_conn() as conn:
            rows = conn.execute(
                "SELECT * FROM sessions ORDER BY updated_at DESC"
            ).fetchall()
            return [dict(r) for r in rows]

    def get_session(self, session_id: str) -> dict | None:
        with self._new_conn() as conn:
            row = conn.execute(
                "SELECT * FROM sessions WHERE id = ?", (session_id,)
            ).fetchone()
            return dict(row) if row else None

    def delete_session(self, session_id: str) -> None:
        with self._new_conn() as conn:
            conn.execute("DELETE FROM sessions WHERE id = ?", (session_id,))

    def update_session_title(self, session_id: str, title: str) -> None:
        with self._new_conn() as conn:
            conn.execute(
                """UPDATE sessions SET title = ?,
                   updated_at = strftime('%Y-%m-%dT%H:%M:%SZ', 'now')
                   WHERE id = ?""",
                (title, session_id),
            )

    def touch_session(self, session_id: str) -> None:
        with self._new_conn() as conn:
            conn.execute(
                """UPDATE sessions SET updated_at = strftime('%Y-%m-%dT%H:%M:%SZ', 'now')
                   WHERE id = ?""",
                (session_id,),
            )

    def add_message(self, session_id: str, role: str, content: str) -> str:
        mid = str(uuid.uuid4())
        with self._new_conn() as conn:
            conn.execute(
                "INSERT INTO messages (id, session_id, role, content) VALUES (?, ?, ?, ?)",
                (mid, session_id, role, content),
            )
            if self._has_fts5:
                conn.execute(
                    """INSERT INTO messages_fts (rowid, content)
                       VALUES ((SELECT rowid FROM messages WHERE id = ?), ?)""",
                    (mid, content),
                )
            conn.execute(
                """UPDATE sessions SET updated_at = strftime('%Y-%m-%dT%H:%M:%SZ', 'now')
                   WHERE id = ?""",
                (session_id,),
            )
        return mid

    def get_messages(self, session_id: str) -> list[dict]:
        with self._new_conn() as conn:
            rows = conn.execute(
                "SELECT * FROM messages WHERE session_id = ? ORDER BY created_at",
                (session_id,),
            ).fetchall()
            return [dict(r) for r in rows]

    def search_messages(self, query: str) -> list[dict]:
        with self._new_conn() as conn:
            if self._has_fts5:
                rows = conn.execute(
                    """SELECT m.* FROM messages_fts fts
                       JOIN messages m ON m.rowid = fts.rowid
                       WHERE messages_fts MATCH ?
                       ORDER BY m.created_at DESC""",
                    (query,),
                ).fetchall()
            else:
                rows = conn.execute(
                    """SELECT * FROM messages WHERE content LIKE ?
                       ORDER BY created_at DESC""",
                    (f"%{query}%",),
                ).fetchall()
            return [dict(r) for r in rows]
