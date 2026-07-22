# Lumo P0 — Plan 1B: Store CRUD（Sessions/Messages + Plans/Tasks + Notes/Folders/Quiz/Study/Checkins/KP）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Store 类上实现全部 P0 表的 CRUD 操作，包括对话历史、学习计划/任务、笔记/文件夹、测验题/答题记录、学习时长、打卡、知识点掌握度。

**Architecture:** 所有方法都在 `lumo.store.Store` 类上，每个方法打开独立的 SQLite 连接（线程安全），返回 JSON 可序列化的 dict/list。

**Tech Stack:** Python 3.12, SQLite3 (stdlib), pytest

## Global Constraints

- 继承 Plan 1A 的全部约束
- 所有 CRUD 方法返回 `dict` 或 `list[dict]`，不暴露 `sqlite3.Row`
- `ON DELETE CASCADE` 已在 schema 中定义，删除父记录自动清理子记录
- FTS5 表需要手动同步（插入/更新时同步 FTS 内容）
- 每个方法独立打开/关闭连接（`with self._conn() as conn`）

---

## Task 4: Store — Sessions 和 Messages CRUD

**Files:**
- Modify: `python/lumo/store.py`
- Modify: `python/tests/test_store.py`

**Interfaces:**
- Consumes: `Store` from Task 2, `settings/memory` CRUD from Task 3
- Produces: `Store.create_session(title="") -> str`
- Produces: `Store.list_sessions() -> list[dict]`
- Produces: `Store.get_session(session_id) -> dict | None`
- Produces: `Store.delete_session(session_id) -> None`
- Produces: `Store.update_session_title(session_id, title) -> None`
- Produces: `Store.touch_session(session_id) -> None`
- Produces: `Store.add_message(session_id, role, content) -> str`
- Produces: `Store.get_messages(session_id) -> list[dict]`
- Produces: `Store.search_messages(query) -> list[dict]` (FTS5)

- [ ] **Step 1: 写失败测试**

Append to `python/tests/test_store.py`:
```python
class TestSessionsCRUD:
    def test_create_session_returns_id(self, tmp_db_path):
        store = Store(tmp_db_path)
        sid = store.create_session("Test Chat")
        assert isinstance(sid, str) and len(sid) > 0

    def test_list_sessions(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.create_session("Chat 1")
        store.create_session("Chat 2")
        assert len(store.list_sessions()) == 2

    def test_get_session(self, tmp_db_path):
        store = Store(tmp_db_path)
        sid = store.create_session("My Chat")
        assert store.get_session(sid)["title"] == "My Chat"

    def test_delete_session(self, tmp_db_path):
        store = Store(tmp_db_path)
        sid = store.create_session("To Delete")
        store.delete_session(sid)
        assert store.get_session(sid) is None
        assert len(store.list_sessions()) == 0

    def test_update_session_title(self, tmp_db_path):
        store = Store(tmp_db_path)
        sid = store.create_session("Old")
        store.update_session_title(sid, "New")
        assert store.get_session(sid)["title"] == "New"

    def test_delete_session_cascades_messages(self, tmp_db_path):
        store = Store(tmp_db_path)
        sid = store.create_session("Chat")
        store.add_message(sid, "user", "hello")
        store.delete_session(sid)
        assert store.get_messages(sid) == []


class TestMessagesCRUD:
    def test_add_message(self, tmp_db_path):
        store = Store(tmp_db_path)
        sid = store.create_session("Chat")
        mid = store.add_message(sid, "user", "Hello world")
        assert isinstance(mid, str)

    def test_get_messages_ordered(self, tmp_db_path):
        store = Store(tmp_db_path)
        sid = store.create_session("Chat")
        store.add_message(sid, "user", "first")
        store.add_message(sid, "assistant", "second")
        store.add_message(sid, "user", "third")
        msgs = store.get_messages(sid)
        assert len(msgs) == 3
        assert msgs[0]["content"] == "first"
        assert msgs[2]["content"] == "third"

    def test_get_messages_empty(self, tmp_db_path):
        store = Store(tmp_db_path)
        sid = store.create_session("Empty")
        assert store.get_messages(sid) == []

    def test_search_messages(self, tmp_db_path):
        store = Store(tmp_db_path)
        sid = store.create_session("Chat")
        store.add_message(sid, "user", "Tell me about Python closures")
        store.add_message(sid, "assistant", "A closure captures variables...")
        store.add_message(sid, "user", "What about JavaScript?")
        results = store.search_messages("Python")
        assert len(results) >= 1

    def test_search_messages_no_results(self, tmp_db_path):
        store = Store(tmp_db_path)
        sid = store.create_session("Chat")
        store.add_message(sid, "user", "Hello world")
        assert store.search_messages("nonexistent_xyz") == []
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd python && python -m pytest tests/test_store.py::TestSessionsCRUD tests/test_store.py::TestMessagesCRUD -v`
Expected: FAIL with `AttributeError`

- [ ] **Step 3: 实现 sessions 和 messages CRUD**

Append to `Store` class in `python/lumo/store.py`:
```python
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
            # Sync FTS
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
            rows = conn.execute(
                """SELECT m.* FROM messages_fts fts
                   JOIN messages m ON m.rowid = fts.rowid
                   WHERE messages_fts MATCH ?
                   ORDER BY m.created_at DESC""",
                (query,),
            ).fetchall()
            return [dict(r) for r in rows]
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd python && python -m pytest tests/test_store.py -v`
Expected: ALL PASSED

- [ ] **Step 5: Commit**

```bash
git add python/lumo/store.py python/tests/test_store.py
git commit -m "feat: add sessions and messages CRUD with FTS5 search"
```

---

## Task 5: Store — Plans 和 Tasks CRUD

**Files:**
- Modify: `python/lumo/store.py`
- Modify: `python/tests/test_store.py`

**Interfaces:**
- Produces: `Store.create_plan(title, goal, daily_minutes=60, start_date="", end_date="") -> str`
- Produces: `Store.list_plans(status=None) -> list[dict]`
- Produces: `Store.get_plan(plan_id) -> dict | None`
- Produces: `Store.update_plan(plan_id, **fields) -> None`
- Produces: `Store.delete_plan(plan_id) -> None`
- Produces: `Store.create_task(plan_id, week_num, title, day_of_week=None, description="", knowledge_points="", order_idx=0) -> str`
- Produces: `Store.get_tasks(plan_id, week_num=None) -> list[dict]`
- Produces: `Store.get_tasks_for_day(plan_id, week_num, day_of_week) -> list[dict]`
- Produces: `Store.update_task(task_id, **fields) -> None`
- Produces: `Store.delete_task(task_id) -> None`
- Produces: `Store.reorder_tasks(plan_id, task_ids_in_order) -> None`

- [ ] **Step 1: 写失败测试**

Append to `python/tests/test_store.py`:
```python
class TestPlansCRUD:
    def test_create_plan(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("前端基础", "2个月学会前端", 60, "2026-07-22", "2026-09-22")
        plan = store.get_plan(pid)
        assert plan["title"] == "前端基础"
        assert plan["goal"] == "2个月学会前端"
        assert plan["daily_minutes"] == 60
        assert plan["status"] == "active"

    def test_list_plans(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.create_plan("Plan A", "Goal A")
        store.create_plan("Plan B", "Goal B")
        assert len(store.list_plans()) == 2

    def test_list_plans_by_status(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Active", "Goal")
        store.create_plan("Paused", "Goal")
        store.update_plan(pid, status="paused")
        assert len(store.list_plans(status="active")) == 1
        assert len(store.list_plans(status="paused")) == 1

    def test_update_plan(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Old", "Old Goal")
        store.update_plan(pid, title="New", status="paused")
        plan = store.get_plan(pid)
        assert plan["title"] == "New"
        assert plan["status"] == "paused"

    def test_delete_plan_cascades_tasks(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        store.create_task(pid, 1, "Task 1")
        store.delete_plan(pid)
        assert store.get_plan(pid) is None
        assert store.get_tasks(pid) == []


class TestTasksCRUD:
    def test_create_task(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        store.create_task(pid, 1, "Learn HTML", day_of_week=1)
        task = store.get_tasks(pid)[0]
        assert task["title"] == "Learn HTML"
        assert task["week_num"] == 1
        assert task["status"] == "pending"

    def test_get_tasks_by_week(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        store.create_task(pid, 1, "Week 1")
        store.create_task(pid, 2, "Week 2")
        assert len(store.get_tasks(pid, week_num=1)) == 1
        assert len(store.get_tasks(pid, week_num=2)) == 1

    def test_update_task_status(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        tid = store.create_task(pid, 1, "Task")
        store.update_task(tid, status="completed")
        assert store.get_tasks(pid)[0]["status"] == "completed"

    def test_reorder_tasks(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        t1 = store.create_task(pid, 1, "A", order_idx=0)
        t2 = store.create_task(pid, 1, "B", order_idx=1)
        t3 = store.create_task(pid, 1, "C", order_idx=2)
        store.reorder_tasks(pid, [t3, t1, t2])
        tasks = store.get_tasks(pid, week_num=1)
        assert tasks[0]["title"] == "C"
        assert tasks[1]["title"] == "A"
        assert tasks[2]["title"] == "B"

    def test_delete_task(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        tid = store.create_task(pid, 1, "Task")
        store.delete_task(tid)
        assert len(store.get_tasks(pid)) == 0

    def test_get_tasks_for_day(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        store.create_task(pid, 1, "Mon", day_of_week=1)
        store.create_task(pid, 1, "Tue", day_of_week=2)
        store.create_task(pid, 1, "Mon 2", day_of_week=1)
        assert len(store.get_tasks_for_day(pid, 1, 1)) == 2
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd python && python -m pytest tests/test_store.py::TestPlansCRUD tests/test_store.py::TestTasksCRUD -v`
Expected: FAIL

- [ ] **Step 3: 实现 plans 和 tasks CRUD**

Append to `Store` class:
```python
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
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd python && python -m pytest tests/test_store.py -v`
Expected: ALL PASSED

- [ ] **Step 5: Commit**

```bash
git add python/lumo/store.py python/tests/test_store.py
git commit -m "feat: add plans and tasks CRUD to store"
```

---

## Task 6: Store — Notes, Folders, Quiz, Study, Checkins, Knowledge Points CRUD

**Files:**
- Modify: `python/lumo/store.py`
- Modify: `python/tests/test_store.py`

**Interfaces:**
- Produces notes CRUD: `create_note`, `get_note`, `list_notes`, `update_note`, `delete_note`, `search_notes`
- Produces folders CRUD: `create_folder`, `list_folders`, `delete_folder`
- Produces quiz CRUD: `create_question`, `get_question`, `list_questions`, `record_answer`, `list_answers`, `get_wrong_answers`
- Produces study: `record_study_session`, `get_study_sessions`, `get_total_study_time`
- Produces checkins: `checkin`, `get_checkins`, `get_streak`, `get_checkin_heatmap`
- Produces kp: `upsert_kp`, `get_kp`, `list_kps`

- [ ] **Step 1: 写失败测试（notes + folders）**

Append to `python/tests/test_store.py`:
```python
class TestNotesCRUD:
    def test_create_note(self, tmp_db_path):
        store = Store(tmp_db_path)
        nid = store.create_note("My Note", "Content here")
        note = store.get_note(nid)
        assert note["title"] == "My Note"
        assert note["content"] == "Content here"
        assert note["source"] == "manual"

    def test_list_notes(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.create_note("A", "aaa")
        store.create_note("B", "bbb")
        assert len(store.list_notes()) == 2

    def test_update_note(self, tmp_db_path):
        store = Store(tmp_db_path)
        nid = store.create_note("Old", "old content")
        store.update_note(nid, title="New", content="new content")
        note = store.get_note(nid)
        assert note["title"] == "New"
        assert note["content"] == "new content"

    def test_delete_note(self, tmp_db_path):
        store = Store(tmp_db_path)
        nid = store.create_note("Note", "content")
        store.delete_note(nid)
        assert store.get_note(nid) is None

    def test_search_notes(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.create_note("Python", "Learn Python closures")
        store.create_note("JS", "Learn JavaScript promises")
        results = store.search_notes("Python")
        assert len(results) >= 1

    def test_create_note_with_folder(self, tmp_db_path):
        store = Store(tmp_db_path)
        fid = store.create_folder("My Folder")
        nid = store.create_note("Note", "content", folder_id=fid)
        assert store.get_note(nid)["folder_id"] == fid


class TestFoldersCRUD:
    def test_create_folder(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.create_folder("Test Folder")
        assert len(store.list_folders()) == 1

    def test_delete_folder(self, tmp_db_path):
        store = Store(tmp_db_path)
        fid = store.create_folder("Folder")
        store.delete_folder(fid)
        assert len(store.list_folders()) == 0
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd python && python -m pytest tests/test_store.py::TestNotesCRUD tests/test_store.py::TestFoldersCRUD -v`
Expected: FAIL

- [ ] **Step 3: 实现 notes 和 folders CRUD**

Append to `Store` class:
```python
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
            conn.execute(
                f"""UPDATE notes SET {set_clause},
                    updated_at = strftime('%Y-%m-%dT%H:%M:%SZ', 'now')
                    WHERE id = ?""",
                values + [note_id],
            )
            if "title" in updates or "content" in updates:
                note = conn.execute(
                    "SELECT title, content FROM notes WHERE id = ?", (note_id,)
                ).fetchone()
                if note:
                    conn.execute(
                        """UPDATE notes_fts SET title = ?, content = ?
                           WHERE rowid = (SELECT rowid FROM notes WHERE id = ?)""",
                        (note["title"], note["content"], note_id),
                    )
            conn.commit()

    def delete_note(self, note_id: str) -> None:
        with self._conn() as conn:
            conn.execute("DELETE FROM notes WHERE id = ?", (note_id,))
            conn.commit()

    def search_notes(self, query: str) -> list[dict]:
        with self._conn() as conn:
            rows = conn.execute(
                """SELECT n.* FROM notes_fts fts
                   JOIN notes n ON n.rowid = fts.rowid
                   WHERE notes_fts MATCH ?
                   ORDER BY n.updated_at DESC""",
                (query,),
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
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd python && python -m pytest tests/test_store.py::TestNotesCRUD tests/test_store.py::TestFoldersCRUD -v`
Expected: ALL PASSED

- [ ] **Step 5: 写失败测试（quiz + study + checkins + kp）**

Append to `python/tests/test_store.py`:
```python
class TestQuizCRUD:
    def test_create_question(self, tmp_db_path):
        store = Store(tmp_db_path)
        qid = store.create_question(
            "single_choice", "What is 2+2?", '["3","4","5","6"]', "4",
            explanation="2+2=4", knowledge_points='["math"]',
        )
        q = store.get_question(qid)
        assert q["question"] == "What is 2+2?"
        assert q["answer"] == "4"

    def test_list_questions(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.create_question("single_choice", "Q1", "[]", "A")
        store.create_question("true_false", "Q2", "", "True")
        assert len(store.list_questions()) == 2

    def test_record_answer(self, tmp_db_path):
        store = Store(tmp_db_path)
        qid = store.create_question("single_choice", "Q", '["a","b"]', "a")
        store.record_answer(qid, "a", is_correct=True)
        answers = store.list_answers(qid)
        assert len(answers) == 1
        assert answers[0]["is_correct"] == 1

    def test_get_wrong_answers(self, tmp_db_path):
        store = Store(tmp_db_path)
        q1 = store.create_question("single_choice", "Q1", '["a","b"]', "a")
        q2 = store.create_question("single_choice", "Q2", '["c","d"]', "c")
        store.record_answer(q1, "a", is_correct=True)
        store.record_answer(q2, "d", is_correct=False, error_reason="confused")
        wrong = store.get_wrong_answers()
        assert len(wrong) == 1
        assert wrong[0]["question_id"] == q2


class TestStudySessions:
    def test_record_study_session(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        store.record_study_session("2026-07-22T10:00:00Z", 1500, plan_id=pid)
        sessions = store.get_study_sessions()
        assert len(sessions) == 1
        assert sessions[0]["duration_seconds"] == 1500

    def test_get_total_study_time(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.record_study_session("2026-07-22T10:00:00Z", 1500)
        store.record_study_session("2026-07-22T14:00:00Z", 1800)
        assert store.get_total_study_time() == 3300


class TestCheckins:
    def test_checkin(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.checkin("2026-07-22", '["task1","task2"]')
        checkins = store.get_checkins()
        assert len(checkins) == 1
        assert checkins[0]["date"] == "2026-07-22"

    def test_get_streak_empty(self, tmp_db_path):
        store = Store(tmp_db_path)
        assert store.get_streak() == 0

    def test_get_streak(self, tmp_db_path):
        from datetime import date, timedelta
        store = Store(tmp_db_path)
        today = date.today()
        for i in range(3):
            d = (today - timedelta(days=i)).isoformat()
            store.checkin(d, "[]")
        assert store.get_streak() >= 3

    def test_get_checkin_heatmap(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.checkin("2026-07-20", "[]")
        store.checkin("2026-07-21", "[]")
        store.checkin("2026-07-22", "[]")
        heatmap = store.get_checkin_heatmap("2026-07")
        assert len(heatmap) == 3


class TestKnowledgePoints:
    def test_upsert_kp(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        store.upsert_kp(pid, "HTML basics", mastery_level=50)
        kp = store.get_kp(pid, "HTML basics")
        assert kp["mastery_level"] == 50

    def test_upsert_kp_updates(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        store.upsert_kp(pid, "CSS", mastery_level=30)
        store.upsert_kp(pid, "CSS", mastery_level=60)
        assert store.get_kp(pid, "CSS")["mastery_level"] == 60

    def test_list_kps(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        store.upsert_kp(pid, "HTML", 40)
        store.upsert_kp(pid, "CSS", 60)
        assert len(store.list_kps(pid)) == 2
```

- [ ] **Step 6: 运行测试确认失败**

Run: `cd python && python -m pytest tests/test_store.py::TestQuizCRUD tests/test_store.py::TestStudySessions tests/test_store.py::TestCheckins tests/test_store.py::TestKnowledgePoints -v`
Expected: FAIL

- [ ] **Step 7: 实现 quiz, study, checkins, kp CRUD**

Append to `Store` class:
```python
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
```

- [ ] **Step 8: 运行全部测试确认通过**

Run: `cd python && python -m pytest tests/ -v`
Expected: ALL PASSED

- [ ] **Step 9: Commit**

```bash
git add python/lumo/store.py python/tests/test_store.py
git commit -m "feat: add notes, folders, quiz, study, checkins, kp CRUD to store"
```
