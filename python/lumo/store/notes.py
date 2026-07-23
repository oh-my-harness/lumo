from __future__ import annotations

"""Note and folder storage."""

import uuid
import sqlite3


class NoteStore:
    """Manages notes and folders tables, including FTS5 search."""

    def __init__(self, conn: sqlite3.Connection, has_fts5: bool = True):
        self._conn = conn
        self._has_fts5 = has_fts5

    def create_note(
        self, title: str, content: str = "", folder_id: str = "",
        source: str = "manual", linked_kp: str = "",
    ) -> str:
        nid = str(uuid.uuid4())
        with self._conn:
            self._conn.execute(
                """INSERT INTO notes (id, folder_id, title, content, source, linked_kp)
                   VALUES (?, ?, ?, ?, ?, ?)""",
                (nid, folder_id or None, title, content, source, linked_kp or None),
            )
            if self._has_fts5:
                self._conn.execute(
                    """INSERT INTO notes_fts (rowid, title, content)
                       VALUES ((SELECT rowid FROM notes WHERE id = ?), ?, ?)""",
                    (nid, title, content),
                )
        return nid

    def get_note(self, note_id: str) -> dict | None:
        with self._conn:
            row = self._conn.execute(
                "SELECT * FROM notes WHERE id = ?", (note_id,)
            ).fetchone()
            return dict(row) if row else None

    def list_notes(self, folder_id: str | None = None) -> list[dict]:
        with self._conn:
            if folder_id:
                rows = self._conn.execute(
                    "SELECT * FROM notes WHERE folder_id = ? ORDER BY updated_at DESC",
                    (folder_id,),
                ).fetchall()
            else:
                rows = self._conn.execute(
                    "SELECT * FROM notes ORDER BY updated_at DESC"
                ).fetchall()
            return [dict(r) for r in rows]

    def update_note(self, note_id: str, **fields) -> None:
        allowed = {"title", "content", "folder_id", "linked_kp"}
        updates = {k: v for k, v in fields.items() if k in allowed}
        if not updates:
            return
        set_clause = ", ".join(f"{k} = ?" for k in updates)
        values = list(updates.values())
        with self._conn:
            # FTS5 external content tables: must delete the old indexed row
            # (using its current values) BEFORE updating the parent row,
            # then re-insert with the new values after.
            if self._has_fts5 and ("title" in updates or "content" in updates):
                old = self._conn.execute(
                    "SELECT rowid, title, content FROM notes WHERE id = ?", (note_id,)
                ).fetchone()
                if old:
                    self._conn.execute(
                        "INSERT INTO notes_fts(notes_fts, rowid, title, content) "
                        "VALUES('delete', ?, ?, ?)",
                        (old["rowid"], old["title"], old["content"]),
                    )
            self._conn.execute(
                f"""UPDATE notes SET {set_clause},
                    updated_at = strftime('%Y-%m-%dT%H:%M:%SZ', 'now')
                    WHERE id = ?""",
                values + [note_id],
            )
            if self._has_fts5 and ("title" in updates or "content" in updates):
                new = self._conn.execute(
                    "SELECT rowid, title, content FROM notes WHERE id = ?", (note_id,)
                ).fetchone()
                if new:
                    self._conn.execute(
                        "INSERT INTO notes_fts(rowid, title, content) VALUES (?, ?, ?)",
                        (new["rowid"], new["title"], new["content"]),
                    )

    def delete_note(self, note_id: str) -> None:
        with self._conn:
            if self._has_fts5:
                row = self._conn.execute(
                    "SELECT rowid, title, content FROM notes WHERE id = ?", (note_id,)
                ).fetchone()
                if row:
                    self._conn.execute(
                        "INSERT INTO notes_fts(notes_fts, rowid, title, content) "
                        "VALUES('delete', ?, ?, ?)",
                        (row["rowid"], row["title"], row["content"]),
                    )
            self._conn.execute("DELETE FROM notes WHERE id = ?", (note_id,))

    def search_notes(self, query: str) -> list[dict]:
        with self._conn:
            if self._has_fts5:
                rows = self._conn.execute(
                    """SELECT n.* FROM notes_fts fts
                       JOIN notes n ON n.rowid = fts.rowid
                       WHERE notes_fts MATCH ?
                       ORDER BY n.updated_at DESC""",
                    (query,),
                ).fetchall()
            else:
                rows = self._conn.execute(
                    """SELECT * FROM notes WHERE title LIKE ? OR content LIKE ?
                       ORDER BY updated_at DESC""",
                    (f"%{query}%", f"%{query}%"),
                ).fetchall()
            return [dict(r) for r in rows]

    def create_folder(self, name: str, parent_id: str = "") -> str:
        fid = str(uuid.uuid4())
        with self._conn:
            self._conn.execute(
                "INSERT INTO folders (id, name, parent_id) VALUES (?, ?, ?)",
                (fid, name, parent_id or None),
            )
        return fid

    def list_folders(self) -> list[dict]:
        with self._conn:
            rows = self._conn.execute("SELECT * FROM folders ORDER BY name").fetchall()
            return [dict(r) for r in rows]

    def delete_folder(self, folder_id: str) -> None:
        with self._conn:
            self._conn.execute("DELETE FROM folders WHERE id = ?", (folder_id,))
