from __future__ import annotations

"""SQLite schema management for Lumo.

Handles table creation, FTS5 detection, and schema versioning with migrations.
"""

import sqlite3

SCHEMA_VERSION = 2

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


def check_fts5(conn: sqlite3.Connection) -> bool:
    """Check if SQLite has FTS5 support."""
    try:
        conn.execute("CREATE VIRTUAL TABLE IF NOT EXISTS _fts5_test USING fts5(x)")
        conn.execute("DROP TABLE IF EXISTS _fts5_test")
        return True
    except Exception:
        return False


def _strip_fts5(schema: str) -> str:
    """Remove FTS5 virtual table statements when FTS5 is unavailable."""
    return schema.replace(
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


def create_tables(conn: sqlite3.Connection) -> bool:
    """Create all tables. Returns True if FTS5 is available."""
    has_fts5 = check_fts5(conn)
    schema = SCHEMA_SQL if has_fts5 else _strip_fts5(SCHEMA_SQL)
    conn.executescript(schema)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    return has_fts5


def _migrate_v1_to_v2(conn: sqlite3.Connection) -> None:
    """Migration from schema v1 to v2.

    v1→v2: No structural changes needed — this is the initial migration
    framework setup. Future schema changes go here.
    """
    pass


MIGRATIONS = {
    1: _migrate_v1_to_v2,
}


def run_migrations(conn: sqlite3.Connection) -> None:
    """Run pending migrations based on PRAGMA user_version."""
    row = conn.execute("PRAGMA user_version").fetchone()
    current = row[0] if row else 0

    for version in range(current + 1, SCHEMA_VERSION + 1):
        migration = MIGRATIONS.get(version)
        if migration:
            migration(conn)
        conn.execute(f"PRAGMA user_version = {version}")
    conn.commit()


def init_db(db_path: str) -> tuple[sqlite3.Connection, bool]:
    """Initialize database: create tables + run migrations.

    Returns (connection, has_fts5).
    """
    conn = sqlite3.connect(str(db_path))
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys=ON")
    has_fts5 = create_tables(conn)
    run_migrations(conn)
    return conn, has_fts5
