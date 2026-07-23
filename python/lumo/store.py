"""SQLite storage layer for Lumo.

All data persistence goes through this module. SQLite is the single
source of truth, managed exclusively by Python.
"""

import sqlite3
import uuid

SCHEMA_SQL = """
-- Sessions (conversations)
CREATE TABLE IF NOT EXISTS sessions (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

-- Messages
CREATE TABLE IF NOT EXISTS messages (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK(role IN ('user', 'assistant', 'system')),
    content TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);
CREATE INDEX IF NOT EXISTS idx_messages_session ON messages(session_id, created_at);

-- Messages FTS
CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts5(
    content, content='messages', content_rowid='rowid'
);

-- Plans
CREATE TABLE IF NOT EXISTS plans (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    goal TEXT NOT NULL,
    daily_minutes INTEGER NOT NULL DEFAULT 60,
    start_date TEXT,
    end_date TEXT,
    status TEXT NOT NULL DEFAULT 'active'
        CHECK(status IN ('active', 'paused', 'completed', 'archived')),
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

-- Tasks
CREATE TABLE IF NOT EXISTS tasks (
    id TEXT PRIMARY KEY,
    plan_id TEXT NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    week_num INTEGER NOT NULL,
    day_of_week INTEGER,
    title TEXT NOT NULL,
    description TEXT,
    knowledge_points TEXT,
    order_idx INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'pending'
        CHECK(status IN ('pending', 'in_progress', 'completed', 'review')),
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);
CREATE INDEX IF NOT EXISTS idx_tasks_plan ON tasks(plan_id, week_num, order_idx);

-- Folders
CREATE TABLE IF NOT EXISTS folders (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    parent_id TEXT REFERENCES folders(id) ON DELETE CASCADE,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

-- Notes
CREATE TABLE IF NOT EXISTS notes (
    id TEXT PRIMARY KEY,
    folder_id TEXT REFERENCES folders(id) ON DELETE SET NULL,
    title TEXT NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    source TEXT NOT NULL DEFAULT 'manual'
        CHECK(source IN ('manual', 'ai_summary', 'conversation', 'paste')),
    linked_kp TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);
CREATE INDEX IF NOT EXISTS idx_notes_folder ON notes(folder_id);

-- Notes FTS
CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING fts5(
    title, content, content='notes', content_rowid='rowid'
);

-- Quiz questions
CREATE TABLE IF NOT EXISTS quiz_questions (
    id TEXT PRIMARY KEY,
    plan_id TEXT REFERENCES plans(id) ON DELETE SET NULL,
    task_id TEXT REFERENCES tasks(id) ON DELETE SET NULL,
    question_type TEXT NOT NULL
        CHECK(question_type IN ('single_choice', 'multi_choice', 'true_false', 'short_answer')),
    question TEXT NOT NULL,
    options TEXT,
    answer TEXT NOT NULL,
    explanation TEXT,
    knowledge_points TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

-- Quiz answers
CREATE TABLE IF NOT EXISTS quiz_answers (
    id TEXT PRIMARY KEY,
    question_id TEXT NOT NULL REFERENCES quiz_questions(id) ON DELETE CASCADE,
    user_answer TEXT NOT NULL,
    is_correct INTEGER NOT NULL CHECK(is_correct IN (0, 1)),
    error_reason TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);
CREATE INDEX IF NOT EXISTS idx_answers_question ON quiz_answers(question_id);

-- Study sessions (time tracking)
CREATE TABLE IF NOT EXISTS study_sessions (
    id TEXT PRIMARY KEY,
    task_id TEXT REFERENCES tasks(id) ON DELETE SET NULL,
    plan_id TEXT REFERENCES plans(id) ON DELETE SET NULL,
    started_at TEXT NOT NULL,
    duration_seconds INTEGER NOT NULL DEFAULT 0,
    pomodoro_count INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_study_started ON study_sessions(started_at);

-- Checkins
CREATE TABLE IF NOT EXISTS checkins (
    id TEXT PRIMARY KEY,
    date TEXT NOT NULL,
    plan_id TEXT REFERENCES plans(id) ON DELETE CASCADE,
    task_ids_completed TEXT
);
CREATE INDEX IF NOT EXISTS idx_checkins_date ON checkins(date);

-- Knowledge points mastery
CREATE TABLE IF NOT EXISTS knowledge_points (
    id TEXT PRIMARY KEY,
    plan_id TEXT REFERENCES plans(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    mastery_level INTEGER NOT NULL DEFAULT 0 CHECK(mastery_level BETWEEN 0 AND 100),
    last_reviewed TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);
CREATE INDEX IF NOT EXISTS idx_kp_plan ON knowledge_points(plan_id);

-- Settings (key-value)
CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

-- Memory (simplified for P0)
CREATE TABLE IF NOT EXISTS memory (
    id TEXT PRIMARY KEY,
    scope TEXT NOT NULL DEFAULT 'global',
    key TEXT NOT NULL,
    value TEXT NOT NULL,
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_memory_scope_key ON memory(scope, key);
"""


class Store:
    """SQLite storage for Lumo.

    Manages schema initialization and provides low-level DB access.
    All table-specific CRUD methods are defined below.
    """

    def __init__(self, db_path: str):
        self.db_path = str(db_path)
        self._init_schema()

    def _init_schema(self):
        """Create all tables if they don't exist."""
        with self._conn() as conn:
            # Try FTS5 first; if unsupported, create tables without it
            self._has_fts5 = self._check_fts5(conn)
            schema = SCHEMA_SQL
            if not self._has_fts5:
                schema = schema.replace(
                    'CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts5(\n'
                    '    content, content=\'messages\', content_rowid=\'rowid\'\n'
                    ');',
                    '-- FTS5 not available; messages_fts skipped'
                ).replace(
                    'CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING fts5(\n'
                    '    title, content, content=\'notes\', content_rowid=\'rowid\'\n'
                    ');',
                    '-- FTS5 not available; notes_fts skipped'
                )
            conn.executescript(schema)
            conn.execute("PRAGMA journal_mode=WAL")
            conn.execute("PRAGMA foreign_keys=ON")

    @staticmethod
    def _check_fts5(conn) -> bool:
        """Check if SQLite has FTS5 support."""
        try:
            conn.execute("CREATE VIRTUAL TABLE IF NOT EXISTS _fts5_test USING fts5(x)")
            conn.execute("DROP TABLE IF EXISTS _fts5_test")
            return True
        except Exception:
            return False

    def _conn(self) -> sqlite3.Connection:
        """Create a new connection. Caller is responsible for closing."""
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys=ON")
        return conn

    def list_tables(self) -> list[str]:
        """Return list of all table names in the database."""
        with self._conn() as conn:
            rows = conn.execute(
                "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
            ).fetchall()
            return [r["name"] for r in rows]

    # ── Settings CRUD ──

    def get_setting(self, key: str) -> str | None:
        with self._conn() as conn:
            row = conn.execute(
                "SELECT value FROM settings WHERE key = ?", (key,)
            ).fetchone()
            return row["value"] if row else None

    def set_setting(self, key: str, value: str) -> None:
        with self._conn() as conn:
            conn.execute(
                """INSERT INTO settings (key, value) VALUES (?, ?)
                   ON CONFLICT(key) DO UPDATE SET value=excluded.value,
                   updated_at=strftime('%Y-%m-%dT%H:%M:%SZ', 'now')""",
                (key, value),
            )
            conn.commit()

    def get_all_settings(self) -> dict[str, str]:
        with self._conn() as conn:
            rows = conn.execute("SELECT key, value FROM settings").fetchall()
            return {r["key"]: r["value"] for r in rows}

    # ── Memory CRUD ──

    def read_memory(self, scope: str, key: str) -> str | None:
        with self._conn() as conn:
            row = conn.execute(
                "SELECT value FROM memory WHERE scope = ? AND key = ?",
                (scope, key),
            ).fetchone()
            return row["value"] if row else None

    def write_memory(self, scope: str, key: str, value: str) -> None:
        with self._conn() as conn:
            conn.execute(
                """INSERT INTO memory (id, scope, key, value) VALUES (?, ?, ?, ?)
                   ON CONFLICT(scope, key) DO UPDATE SET value=excluded.value,
                   updated_at=strftime('%Y-%m-%dT%H:%M:%SZ', 'now')""",
                (str(uuid.uuid4()), scope, key, value),
            )
            conn.commit()

    def list_memory(self, scope: str) -> list[dict]:
        with self._conn() as conn:
            rows = conn.execute(
                "SELECT key, value, updated_at FROM memory WHERE scope = ? ORDER BY key",
                (scope,),
            ).fetchall()
            return [dict(r) for r in rows]
    # ── Sessions CRUD ──

    def create_session(self, title: str = "") -> str:
        sid = str(uuid.uuid4())
        with self._conn() as conn:
            conn.execute(
                "INSERT INTO sessions (id, title) VALUES (?, ?)",
                (sid, title),
            )
            conn.commit()
        return sid

    def list_sessions(self) -> list[dict]:
        with self._conn() as conn:
            rows = conn.execute(
                "SELECT * FROM sessions ORDER BY updated_at DESC"
            ).fetchall()
            return [dict(r) for r in rows]

    def get_session(self, session_id: str) -> dict | None:
        with self._conn() as conn:
            row = conn.execute(
                "SELECT * FROM sessions WHERE id = ?", (session_id,)
            ).fetchone()
            return dict(row) if row else None

    def delete_session(self, session_id: str) -> None:
        with self._conn() as conn:
            conn.execute("DELETE FROM sessions WHERE id = ?", (session_id,))
            conn.commit()

    def update_session_title(self, session_id: str, title: str) -> None:
        with self._conn() as conn:
            conn.execute(
                """UPDATE sessions SET title = ?,
                   updated_at = strftime('%Y-%m-%dT%H:%M:%SZ', 'now')
                   WHERE id = ?""",
                (title, session_id),
            )
            conn.commit()

    def touch_session(self, session_id: str) -> None:
        with self._conn() as conn:
            conn.execute(
                """UPDATE sessions SET updated_at = strftime('%Y-%m-%dT%H:%M:%SZ', 'now')
                   WHERE id = ?""",
                (session_id,),
            )
            conn.commit()

    # ── Messages CRUD ──

    def add_message(self, session_id: str, role: str, content: str) -> str:
        mid = str(uuid.uuid4())
        with self._conn() as conn:
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
            conn.commit()
        return mid

    def get_messages(self, session_id: str) -> list[dict]:
        with self._conn() as conn:
            rows = conn.execute(
                "SELECT * FROM messages WHERE session_id = ? ORDER BY created_at",
                (session_id,),
            ).fetchall()
            return [dict(r) for r in rows]

    def search_messages(self, query: str) -> list[dict]:
        with self._conn() as conn:
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

    # ── Plans CRUD ──

    def create_plan(
        self, title: str, goal: str, daily_minutes: int = 60,
        start_date: str = "", end_date: str = "",
    ) -> str:
        pid = str(uuid.uuid4())
        with self._conn() as conn:
            conn.execute(
                """INSERT INTO plans (id, title, goal, daily_minutes, start_date, end_date)
                   VALUES (?, ?, ?, ?, ?, ?)""",
                (pid, title, goal, daily_minutes, start_date or None, end_date or None),
            )
            conn.commit()
        return pid

    def list_plans(self, status: str | None = None) -> list[dict]:
        with self._conn() as conn:
            if status:
                rows = conn.execute(
                    "SELECT * FROM plans WHERE status = ? ORDER BY created_at DESC",
                    (status,),
                ).fetchall()
            else:
                rows = conn.execute(
                    "SELECT * FROM plans ORDER BY created_at DESC"
                ).fetchall()
            return [dict(r) for r in rows]

    def get_plan(self, plan_id: str) -> dict | None:
        with self._conn() as conn:
            row = conn.execute(
                "SELECT * FROM plans WHERE id = ?", (plan_id,)
            ).fetchone()
            return dict(row) if row else None

    def update_plan(self, plan_id: str, **fields) -> None:
        allowed = {"title", "goal", "daily_minutes", "start_date", "end_date", "status"}
        updates = {k: v for k, v in fields.items() if k in allowed}
        if not updates:
            return
        set_clause = ", ".join(f"{k} = ?" for k in updates)
        values = list(updates.values()) + [plan_id]
        with self._conn() as conn:
            conn.execute(f"UPDATE plans SET {set_clause} WHERE id = ?", values)
            conn.commit()

    def delete_plan(self, plan_id: str) -> None:
        with self._conn() as conn:
            conn.execute("DELETE FROM plans WHERE id = ?", (plan_id,))
            conn.commit()

    # ── Tasks CRUD ──

    def create_task(
        self, plan_id: str, week_num: int, title: str,
        day_of_week: int | None = None, description: str = "",
        knowledge_points: str = "", order_idx: int = 0,
    ) -> str:
        tid = str(uuid.uuid4())
        with self._conn() as conn:
            conn.execute(
                """INSERT INTO tasks
                   (id, plan_id, week_num, day_of_week, title, description, knowledge_points, order_idx)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                (tid, plan_id, week_num, day_of_week, title, description, knowledge_points, order_idx),
            )
            conn.commit()
        return tid

    def get_tasks(self, plan_id: str, week_num: int | None = None) -> list[dict]:
        with self._conn() as conn:
            if week_num is not None:
                rows = conn.execute(
                    "SELECT * FROM tasks WHERE plan_id = ? AND week_num = ? ORDER BY order_idx",
                    (plan_id, week_num),
                ).fetchall()
            else:
                rows = conn.execute(
                    "SELECT * FROM tasks WHERE plan_id = ? ORDER BY week_num, order_idx",
                    (plan_id,),
                ).fetchall()
            return [dict(r) for r in rows]

    def get_task(self, task_id: str) -> dict | None:
        with self._conn() as conn:
            row = conn.execute("SELECT * FROM tasks WHERE id = ?", (task_id,)).fetchone()
            return dict(row) if row else None

    def get_tasks_for_day(self, plan_id: str, week_num: int, day_of_week: int) -> list[dict]:
        with self._conn() as conn:
            rows = conn.execute(
                """SELECT * FROM tasks WHERE plan_id = ? AND week_num = ? AND day_of_week = ?
                   ORDER BY order_idx""",
                (plan_id, week_num, day_of_week),
            ).fetchall()
            return [dict(r) for r in rows]

    def update_task(self, task_id: str, **fields) -> None:
        allowed = {"title", "description", "knowledge_points", "order_idx",
                   "status", "day_of_week", "week_num"}
        updates = {k: v for k, v in fields.items() if k in allowed}
        if not updates:
            return
        set_clause = ", ".join(f"{k} = ?" for k in updates)
        values = list(updates.values()) + [task_id]
        with self._conn() as conn:
            conn.execute(f"UPDATE tasks SET {set_clause} WHERE id = ?", values)
            conn.commit()

    def delete_task(self, task_id: str) -> None:
        with self._conn() as conn:
            conn.execute("DELETE FROM tasks WHERE id = ?", (task_id,))
            conn.commit()

    def reorder_tasks(self, plan_id: str, task_ids_in_order: list[str]) -> None:
        with self._conn() as conn:
            for idx, tid in enumerate(task_ids_in_order):
                conn.execute(
                    "UPDATE tasks SET order_idx = ? WHERE id = ? AND plan_id = ?",
                    (idx, tid, plan_id),
                )
            conn.commit()

    # ── Notes CRUD ──

    def create_note(
        self, title: str, content: str = "", folder_id: str = "",
        source: str = "manual", linked_kp: str = "",
    ) -> str:
        nid = str(uuid.uuid4())
        with self._conn() as conn:
            conn.execute(
                """INSERT INTO notes (id, folder_id, title, content, source, linked_kp)
                   VALUES (?, ?, ?, ?, ?, ?)""",
                (nid, folder_id or None, title, content, source, linked_kp or None),
            )
            if self._has_fts5:
                conn.execute(
                    """INSERT INTO notes_fts (rowid, title, content)
                       VALUES ((SELECT rowid FROM notes WHERE id = ?), ?, ?)""",
                    (nid, title, content),
                )
            conn.commit()
        return nid

    def get_note(self, note_id: str) -> dict | None:
        with self._conn() as conn:
            row = conn.execute(
                "SELECT * FROM notes WHERE id = ?", (note_id,)
            ).fetchone()
            return dict(row) if row else None

    def list_notes(self, folder_id: str | None = None) -> list[dict]:
        with self._conn() as conn:
            if folder_id:
                rows = conn.execute(
                    "SELECT * FROM notes WHERE folder_id = ? ORDER BY updated_at DESC",
                    (folder_id,),
                ).fetchall()
            else:
                rows = conn.execute(
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
        with self._conn() as conn:
            # FTS5 external content tables: must delete the old indexed row
            # (using its current values) BEFORE updating the parent row,
            # then re-insert with the new values after.
            if self._has_fts5 and ("title" in updates or "content" in updates):
                old = conn.execute(
                    "SELECT rowid, title, content FROM notes WHERE id = ?", (note_id,)
                ).fetchone()
                if old:
                    conn.execute(
                        "INSERT INTO notes_fts(notes_fts, rowid, title, content) "
                        "VALUES('delete', ?, ?, ?)",
                        (old["rowid"], old["title"], old["content"]),
                    )
            conn.execute(
                f"""UPDATE notes SET {set_clause},
                    updated_at = strftime('%Y-%m-%dT%H:%M:%SZ', 'now')
                    WHERE id = ?""",
                values + [note_id],
            )
            if self._has_fts5 and ("title" in updates or "content" in updates):
                new = conn.execute(
                    "SELECT rowid, title, content FROM notes WHERE id = ?", (note_id,)
                ).fetchone()
                if new:
                    conn.execute(
                        "INSERT INTO notes_fts(rowid, title, content) VALUES (?, ?, ?)",
                        (new["rowid"], new["title"], new["content"]),
                    )
            conn.commit()

    def delete_note(self, note_id: str) -> None:
        with self._conn() as conn:
            if self._has_fts5:
                row = conn.execute(
                    "SELECT rowid, title, content FROM notes WHERE id = ?", (note_id,)
                ).fetchone()
                if row:
                    conn.execute(
                        "INSERT INTO notes_fts(notes_fts, rowid, title, content) "
                        "VALUES('delete', ?, ?, ?)",
                        (row["rowid"], row["title"], row["content"]),
                    )
            conn.execute("DELETE FROM notes WHERE id = ?", (note_id,))
            conn.commit()

    def search_notes(self, query: str) -> list[dict]:
        with self._conn() as conn:
            if self._has_fts5:
                rows = conn.execute(
                    """SELECT n.* FROM notes_fts fts
                       JOIN notes n ON n.rowid = fts.rowid
                       WHERE notes_fts MATCH ?
                       ORDER BY n.updated_at DESC""",
                    (query,),
                ).fetchall()
            else:
                rows = conn.execute(
                    """SELECT * FROM notes WHERE title LIKE ? OR content LIKE ?
                       ORDER BY updated_at DESC""",
                    (f"%{query}%", f"%{query}%"),
                ).fetchall()
            return [dict(r) for r in rows]

    # ── Folders CRUD ──

    def create_folder(self, name: str, parent_id: str = "") -> str:
        fid = str(uuid.uuid4())
        with self._conn() as conn:
            conn.execute(
                "INSERT INTO folders (id, name, parent_id) VALUES (?, ?, ?)",
                (fid, name, parent_id or None),
            )
            conn.commit()
        return fid

    def list_folders(self) -> list[dict]:
        with self._conn() as conn:
            rows = conn.execute("SELECT * FROM folders ORDER BY name").fetchall()
            return [dict(r) for r in rows]

    def delete_folder(self, folder_id: str) -> None:
        with self._conn() as conn:
            conn.execute("DELETE FROM folders WHERE id = ?", (folder_id,))
            conn.commit()

    # ── Quiz CRUD ──

    def create_question(
        self, question_type: str, question: str, options: str,
        answer: str, explanation: str = "", knowledge_points: str = "",
        plan_id: str = "", task_id: str = "",
    ) -> str:
        qid = str(uuid.uuid4())
        with self._conn() as conn:
            conn.execute(
                """INSERT INTO quiz_questions
                   (id, plan_id, task_id, question_type, question, options, answer, explanation, knowledge_points)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (qid, plan_id or None, task_id or None,
                 question_type, question, options, answer, explanation, knowledge_points),
            )
            conn.commit()
        return qid

    def get_question(self, question_id: str) -> dict | None:
        with self._conn() as conn:
            row = conn.execute(
                "SELECT * FROM quiz_questions WHERE id = ?", (question_id,)
            ).fetchone()
            return dict(row) if row else None

    def list_questions(self, plan_id: str | None = None) -> list[dict]:
        with self._conn() as conn:
            if plan_id:
                rows = conn.execute(
                    "SELECT * FROM quiz_questions WHERE plan_id = ? ORDER BY created_at DESC",
                    (plan_id,),
                ).fetchall()
            else:
                rows = conn.execute(
                    "SELECT * FROM quiz_questions ORDER BY created_at DESC"
                ).fetchall()
            return [dict(r) for r in rows]

    def record_answer(
        self, question_id: str, user_answer: str, is_correct: bool,
        error_reason: str = "",
    ) -> str:
        aid = str(uuid.uuid4())
        with self._conn() as conn:
            conn.execute(
                """INSERT INTO quiz_answers (id, question_id, user_answer, is_correct, error_reason)
                   VALUES (?, ?, ?, ?, ?)""",
                (aid, question_id, user_answer, 1 if is_correct else 0, error_reason),
            )
            conn.commit()
        return aid

    def list_answers(self, question_id: str) -> list[dict]:
        with self._conn() as conn:
            rows = conn.execute(
                "SELECT * FROM quiz_answers WHERE question_id = ? ORDER BY created_at",
                (question_id,),
            ).fetchall()
            return [dict(r) for r in rows]

    def get_wrong_answers(self) -> list[dict]:
        """Get all wrong answers joined with question details."""
        with self._conn() as conn:
            rows = conn.execute(
                """SELECT a.*, q.question, q.question_type, q.options,
                          q.answer as correct_answer, q.explanation, q.knowledge_points
                   FROM quiz_answers a
                   JOIN quiz_questions q ON a.question_id = q.id
                   WHERE a.is_correct = 0
                   ORDER BY a.created_at DESC"""
            ).fetchall()
            return [dict(r) for r in rows]

    # ── Study Sessions ──

    def record_study_session(
        self, started_at: str, duration_seconds: int,
        task_id: str = "", plan_id: str = "", pomodoro_count: int = 0,
    ) -> str:
        sid = str(uuid.uuid4())
        with self._conn() as conn:
            conn.execute(
                """INSERT INTO study_sessions
                   (id, task_id, plan_id, started_at, duration_seconds, pomodoro_count)
                   VALUES (?, ?, ?, ?, ?, ?)""",
                (sid, task_id or None, plan_id or None,
                 started_at, duration_seconds, pomodoro_count),
            )
            conn.commit()
        return sid

    def get_study_sessions(self, plan_id: str | None = None) -> list[dict]:
        with self._conn() as conn:
            if plan_id:
                rows = conn.execute(
                    "SELECT * FROM study_sessions WHERE plan_id = ? ORDER BY started_at DESC",
                    (plan_id,),
                ).fetchall()
            else:
                rows = conn.execute(
                    "SELECT * FROM study_sessions ORDER BY started_at DESC"
                ).fetchall()
            return [dict(r) for r in rows]

    def get_total_study_time(self) -> int:
        with self._conn() as conn:
            row = conn.execute(
                "SELECT COALESCE(SUM(duration_seconds), 0) as total FROM study_sessions"
            ).fetchone()
            return row["total"]

    # ── Checkins ──

    def checkin(self, date_str: str, task_ids_completed: str = "[]") -> str:
        cid = str(uuid.uuid4())
        with self._conn() as conn:
            conn.execute(
                "INSERT INTO checkins (id, date, task_ids_completed) VALUES (?, ?, ?)",
                (cid, date_str, task_ids_completed),
            )
            conn.commit()
        return cid

    def get_checkins(self, month: str | None = None) -> list[dict]:
        with self._conn() as conn:
            if month:
                rows = conn.execute(
                    "SELECT * FROM checkins WHERE date LIKE ? ORDER BY date",
                    (f"{month}%",),
                ).fetchall()
            else:
                rows = conn.execute(
                    "SELECT * FROM checkins ORDER BY date DESC"
                ).fetchall()
            return [dict(r) for r in rows]

    def get_streak(self) -> int:
        """Calculate current consecutive-day streak ending today."""
        from datetime import date, timedelta
        with self._conn() as conn:
            today = date.today()
            streak = 0
            d = today
            while True:
                row = conn.execute(
                    "SELECT 1 FROM checkins WHERE date = ?", (d.isoformat(),)
                ).fetchone()
                if row:
                    streak += 1
                    d -= timedelta(days=1)
                else:
                    break
            return streak

    def get_checkin_heatmap(self, month: str) -> list[dict]:
        with self._conn() as conn:
            rows = conn.execute(
                "SELECT date, task_ids_completed FROM checkins WHERE date LIKE ? ORDER BY date",
                (f"{month}%",),
            ).fetchall()
            return [dict(r) for r in rows]

    # ── Knowledge Points ──

    def upsert_kp(self, plan_id: str, name: str, mastery_level: int = 0) -> str:
        with self._conn() as conn:
            existing = conn.execute(
                "SELECT id FROM knowledge_points WHERE plan_id = ? AND name = ?",
                (plan_id, name),
            ).fetchone()
            if existing:
                conn.execute(
                    """UPDATE knowledge_points SET mastery_level = ?,
                        last_reviewed = strftime('%Y-%m-%dT%H:%M:%SZ', 'now')
                        WHERE id = ?""",
                    (mastery_level, existing["id"]),
                )
                conn.commit()
                return existing["id"]
            else:
                kid = str(uuid.uuid4())
                conn.execute(
                    """INSERT INTO knowledge_points (id, plan_id, name, mastery_level)
                       VALUES (?, ?, ?, ?)""",
                    (kid, plan_id, name, mastery_level),
                )
                conn.commit()
                return kid

    def get_kp(self, plan_id: str, name: str) -> dict | None:
        with self._conn() as conn:
            row = conn.execute(
                "SELECT * FROM knowledge_points WHERE plan_id = ? AND name = ?",
                (plan_id, name),
            ).fetchone()
            return dict(row) if row else None

    def list_kps(self, plan_id: str) -> list[dict]:
        with self._conn() as conn:
            rows = conn.execute(
                "SELECT * FROM knowledge_points WHERE plan_id = ? ORDER BY name",
                (plan_id,),
            ).fetchall()
            return [dict(r) for r in rows]
