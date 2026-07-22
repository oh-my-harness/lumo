# Lumo P0 — Plan 1A: 基础层（Python 包骨架 + SQLite Schema + Settings/Memory CRUD）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建 Lumo Python 核心包骨架，创建 SQLite 存储层（含完整 P0 schema），并实现 settings 和 memory 表的 CRUD。

**Architecture:** Python 包 `lumo`（store.py），通过 Chaquopy 嵌入 Android。SQLite 作为唯一存储，由 Python 层统一管理。

**Tech Stack:** Python 3.12, SQLite3 (stdlib), Senza (PyO3), Chaquopy

## Global Constraints

- Python 包位于 `python/lumo/`，打包到 Android 项目的 `app/src/main/python/` 下
- 所有 SQLite 操作在 Python 侧，Kotlin 只做 UI
- Senza runtime SHA 锁定：`f0d034295ea97bddc69d9fa34f4bc73fac64d46c`
- Android minSdk 24，targetSdk 34，abiFilters 仅 arm64-v8a
- Chaquopy Python 版本 3.12
- 数据库文件路径：Android 应用私有存储 `{app_data}/files/lumo/lumo.db`
- 测试在桌面端用 pytest 运行，不依赖 Android 模拟器
- 包名：`com.lumo.app`

---

## 文件结构

```
lumo/
├── python/
│   ├── lumo/
│   │   ├── __init__.py          # 已存在，包初始化
│   │   └── store.py             # ← 本计划创建
│   ├── tests/
│   │   ├── conftest.py          # ← 本计划创建
│   │   └── test_store.py        # ← 本计划创建
│   └── pyproject.toml           # 已存在，需修改
└── docs/plans/
    ├── 2026-07-22-plan1a-foundation.md   (本文)
    ├── 2026-07-22-plan1b-store-crud.md   (后续)
    └── 2026-07-22-plan1c-config-bridge.md (后续)
```

---

## Task 1: Python 包骨架完善 + conftest

**Files:**
- Modify: `python/pyproject.toml`（添加 dev 依赖）
- Create: `python/tests/conftest.py`
- Create: `python/tests/__init__.py`（空文件，确保 pytest 发现）

**Interfaces:**
- Produces: `lumo` Python 包（可 `import lumo`），`lumo.__version__` 返回版本号
- Produces: `tmp_db_path` pytest fixture

- [ ] **Step 1: 修改 pyproject.toml 添加 dev 依赖**

`python/pyproject.toml` 当前内容：
```toml
[project]
name = "lumo"
version = "0.1.0"
requires-python = ">=3.9"
dependencies = [
    "senza-sdk",
    "aiosqlite",
]

[build-system]
requires = ["setuptools>=64"]
build-backend = "setuptools.backends._legacy:_Backend"

[tool.setuptools.packages.find]
where = ["."]
```

改为：
```toml
[project]
name = "lumo"
version = "0.1.0"
description = "Lumo — AI learning coach, Python core"
requires-python = ">=3.9"
dependencies = [
    "senza-sdk",
]

[project.optional-dependencies]
dev = ["pytest>=7.0"]

[build-system]
requires = ["setuptools>=64"]
build-backend = "setuptools.backends._legacy:_Backend"

[tool.setuptools.packages.find]
where = ["."]

[tool.pytest.ini_options]
testpaths = ["tests"]
```

注意：移除 `aiosqlite`，P0 用 stdlib `sqlite3` 即可。添加 `description`、`dev` optional deps、`pytest.ini_options`。

- [ ] **Step 2: 创建 conftest.py**

Create `python/tests/conftest.py`:
```python
"""Shared test fixtures."""
import os
import tempfile
import pytest


@pytest.fixture
def tmp_db_path():
    """Provide a temporary database file path.

    Creates a temp file, deletes it (so SQLite creates fresh),
    and cleans up after the test.
    """
    with tempfile.NamedTemporaryFile(suffix=".db", delete=False) as f:
        path = f.name
    os.unlink(path)
    yield path
    if os.path.exists(path):
        os.unlink(path)
```

- [ ] **Step 3: 创建 tests/__init__.py**

Create `python/tests/__init__.py`:
```python
```
（空文件）

- [ ] **Step 4: 验证包可导入**

Run: `cd python && python -c "import lumo; print(lumo.__version__)"`
Expected: `0.1.0`

- [ ] **Step 5: 验证 pytest 可运行**

Run: `cd python && python -m pytest --co`
Expected: `no tests ran` (no errors, pytest discovers 0 tests)

- [ ] **Step 6: Commit**

```bash
cd /Users/hhl/Documents/projs/oh-my-harness/lumo
git add python/pyproject.toml python/tests/
git commit -m "feat: add dev deps and test fixtures for lumo package"
```

---

## Task 2: SQLite Store — Schema 初始化

**Files:**
- Create: `python/lumo/store.py`
- Create: `python/tests/test_store.py`

**Interfaces:**
- Produces: `lumo.store.Store` 类，`Store(db_path)` 构造时自动执行 schema migration
- Produces: `Store.list_tables() -> list[str]`

P0 数据库 schema（全部表）：

| 表 | 用途 |
|---|---|
| `sessions` | 对话会话（id, title, created_at, updated_at） |
| `messages` | 对话消息（id, session_id, role, content, created_at） |
| `messages_fts` | messages 全文索引（FTS5 virtual table） |
| `plans` | 学习计划（id, title, goal, daily_minutes, start_date, end_date, status, created_at） |
| `tasks` | 学习任务（id, plan_id, week_num, day_of_week, title, description, knowledge_points, order_idx, status, created_at） |
| `notes` | 笔记（id, folder_id, title, content, source, linked_kp, created_at, updated_at） |
| `notes_fts` | notes 全文索引（FTS5） |
| `folders` | 笔记文件夹（id, name, parent_id, created_at） |
| `quiz_questions` | 题目（id, plan_id, task_id, question_type, question, options, answer, explanation, knowledge_points, created_at） |
| `quiz_answers` | 答题记录（id, question_id, user_answer, is_correct, error_reason, created_at） |
| `study_sessions` | 学习时长记录（id, task_id, plan_id, started_at, duration_seconds, pomodoro_count） |
| `checkins` | 打卡记录（id, date, plan_id, task_ids_completed） |
| `knowledge_points` | 知识点掌握度（id, plan_id, name, mastery_level, last_reviewed, created_at） |
| `settings` | 应用设置 key-value（key, value, updated_at） |
| `memory` | 记忆系统简版（id, scope, key, value, updated_at） |

- [ ] **Step 1: 写 schema 初始化的失败测试**

Create `python/tests/test_store.py`:
```python
"""Tests for lumo.store."""
import os
import pytest
from lumo.store import Store


class TestStoreSchema:
    def test_store_creates_all_tables(self, tmp_db_path):
        """Store() should create all expected tables on init."""
        store = Store(tmp_db_path)
        tables = store.list_tables()
        expected = {
            "sessions", "messages", "messages_fts",
            "plans", "tasks",
            "notes", "notes_fts", "folders",
            "quiz_questions", "quiz_answers",
            "study_sessions", "checkins",
            "knowledge_points", "settings", "memory",
        }
        assert expected.issubset(set(tables)), \
            f"Missing tables: {expected - set(tables)}"

    def test_store_is_idempotent(self, tmp_db_path):
        """Opening Store on existing db should not error."""
        Store(tmp_db_path)
        Store(tmp_db_path)  # Should not raise

    def test_store_sets_wal_mode(self, tmp_db_path):
        """Store should enable WAL mode for concurrent access."""
        Store(tmp_db_path)
        import sqlite3
        conn = sqlite3.connect(tmp_db_path)
        mode = conn.execute("PRAGMA journal_mode").fetchone()[0]
        conn.close()
        assert mode == "wal"
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd python && python -m pytest tests/test_store.py -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'lumo.store'`

- [ ] **Step 3: 实现 Store 类**

Create `python/lumo/store.py`:
```python
"""SQLite storage layer for Lumo.

All data persistence goes through this module. SQLite is the single
source of truth, managed exclusively by Python.
"""

import sqlite3

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
    All table-specific CRUD methods are defined in later tasks.
    """

    def __init__(self, db_path: str):
        self.db_path = str(db_path)
        self._init_schema()

    def _init_schema(self):
        """Create all tables if they don't exist."""
        with self._conn() as conn:
            conn.executescript(SCHEMA_SQL)
            conn.execute("PRAGMA journal_mode=WAL")
            conn.execute("PRAGMA foreign_keys=ON")

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
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd python && python -m pytest tests/test_store.py -v`
Expected: 3 PASSED

- [ ] **Step 5: Commit**

```bash
git add python/lumo/store.py python/tests/test_store.py
git commit -m "feat: add SQLite store with full P0 schema"
```

---

## Task 3: Store — Settings 和 Memory CRUD

**Files:**
- Modify: `python/lumo/store.py`（追加方法到 Store 类）
- Modify: `python/tests/test_store.py`（追加测试）

**Interfaces:**
- Consumes: `Store` class from Task 2
- Produces: `Store.get_setting(key) -> str | None`
- Produces: `Store.set_setting(key, value) -> None`
- Produces: `Store.get_all_settings() -> dict[str, str]`
- Produces: `Store.read_memory(scope, key) -> str | None`
- Produces: `Store.write_memory(scope, key, value) -> None`
- Produces: `Store.list_memory(scope) -> list[dict]`

- [ ] **Step 1: 写失败测试**

Append to `python/tests/test_store.py`:
```python
import uuid


class TestSettingsCRUD:
    def test_set_and_get_setting(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.set_setting("api_key", "sk-123")
        assert store.get_setting("api_key") == "sk-123"

    def test_get_missing_setting_returns_none(self, tmp_db_path):
        store = Store(tmp_db_path)
        assert store.get_setting("nonexistent") is None

    def test_update_setting(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.set_setting("model", "gpt-4o")
        store.set_setting("model", "glm-5.2")
        assert store.get_setting("model") == "glm-5.2"

    def test_get_all_settings(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.set_setting("a", "1")
        store.set_setting("b", "2")
        settings = store.get_all_settings()
        assert settings == {"a": "1", "b": "2"}


class TestMemoryCRUD:
    def test_write_and_read_memory(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.write_memory("global", "learning_style", "visual")
        assert store.read_memory("global", "learning_style") == "visual"

    def test_read_missing_memory_returns_none(self, tmp_db_path):
        store = Store(tmp_db_path)
        assert store.read_memory("global", "nonexistent") is None

    def test_update_memory(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.write_memory("global", "style", "visual")
        store.write_memory("global", "style", "auditory")
        assert store.read_memory("global", "style") == "auditory"

    def test_list_memory_by_scope(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.write_memory("global", "a", "1")
        store.write_memory("global", "b", "2")
        store.write_memory("plan-123", "c", "3")
        assert len(store.list_memory("global")) == 2
        assert len(store.list_memory("plan-123")) == 1
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd python && python -m pytest tests/test_store.py::TestSettingsCRUD tests/test_store.py::TestMemoryCRUD -v`
Expected: FAIL with `AttributeError: 'Store' object has no attribute 'get_setting'`

- [ ] **Step 3: 实现 settings 和 memory CRUD**

Append to `Store` class in `python/lumo/store.py` (after `list_tables` method):
```python
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
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd python && python -m pytest tests/test_store.py -v`
Expected: 11 PASSED (3 schema + 4 settings + 4 memory)

- [ ] **Step 5: Commit**

```bash
git add python/lumo/store.py python/tests/test_store.py
git commit -m "feat: add settings and memory CRUD to store"
```
