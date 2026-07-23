from __future__ import annotations

"""SQLite storage layer for Lumo.

All data persistence goes through this module. SQLite is the single
source of truth, managed exclusively by Python.

The Store class is a facade that delegates to specialized sub-stores
(SessionStore, PlanStore, NoteStore, QuizStore, StatsStore, MemoryStore).
It preserves the original flat API for backward compatibility with
existing callers (agent.py, tools.py, bridge).
"""

import sqlite3

from lumo.store.schema import init_db
from lumo.store.sessions import SessionStore
from lumo.store.plans import PlanStore
from lumo.store.notes import NoteStore
from lumo.store.quiz import QuizStore
from lumo.store.stats import StatsStore
from lumo.store.memory import MemoryStore

__all__ = ["Store"]


class Store:
    """SQLite storage facade for Lumo.

    Provides the same flat API as the original monolithic Store class.
    All methods delegate to specialized sub-store instances.
    """

    def __init__(self, db_path: str):
        self.db_path = str(db_path)
        # init_db creates tables + runs migrations; the returned connection
        # is only used during initialization. All runtime queries create
        # fresh per-call connections (thread-safe, like the original Store).
        init_conn, self._has_fts5 = init_db(self.db_path)
        init_conn.close()

        # Sub-stores receive db_path and create per-call connections.
        self._sessions = SessionStore(self.db_path, self._has_fts5)
        self._plans = PlanStore(self.db_path, self._has_fts5)
        self._notes = NoteStore(self.db_path, self._has_fts5)
        self._quiz = QuizStore(self.db_path, self._has_fts5)
        self._stats = StatsStore(self.db_path, self._has_fts5)
        self._memory = MemoryStore(self.db_path, self._has_fts5)

    @property
    def has_fts5(self) -> bool:
        return self._has_fts5

    def list_tables(self) -> list[str]:
        """Return list of all table names in the database."""
        with self._conn() as conn:
            rows = conn.execute(
                "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
            ).fetchall()
            return [r["name"] for r in rows]

    def _conn(self) -> sqlite3.Connection:
        """Create a new connection for backward compatibility.
        Supports use as a context manager: `with store._conn() as conn:`.
        """
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys=ON")
        return conn

    # ── Settings CRUD ──

    def get_setting(self, key: str) -> str | None:
        return self._memory.get_setting(key)

    def set_setting(self, key: str, value: str) -> None:
        self._memory.set_setting(key, value)

    def get_all_settings(self) -> dict[str, str]:
        return self._memory.get_all_settings()

    # ── Memory CRUD ──

    def read_memory(self, scope: str, key: str) -> str | None:
        return self._memory.read_memory(scope, key)

    def write_memory(self, scope: str, key: str, value: str) -> None:
        self._memory.write_memory(scope, key, value)

    def list_memory(self, scope: str) -> list[dict]:
        return self._memory.list_memory(scope)

    # ── Sessions CRUD ──

    def create_session(self, title: str = "") -> str:
        return self._sessions.create_session(title)

    def list_sessions(self) -> list[dict]:
        return self._sessions.list_sessions()

    def get_session(self, session_id: str) -> dict | None:
        return self._sessions.get_session(session_id)

    def delete_session(self, session_id: str) -> None:
        self._sessions.delete_session(session_id)

    def update_session_title(self, session_id: str, title: str) -> None:
        self._sessions.update_session_title(session_id, title)

    def touch_session(self, session_id: str) -> None:
        self._sessions.touch_session(session_id)

    # ── Messages CRUD ──

    def add_message(self, session_id: str, role: str, content: str) -> str:
        return self._sessions.add_message(session_id, role, content)

    def get_messages(self, session_id: str) -> list[dict]:
        return self._sessions.get_messages(session_id)

    def search_messages(self, query: str) -> list[dict]:
        return self._sessions.search_messages(query)

    # ── Plans CRUD ──

    def create_plan(
        self, title: str, goal: str, daily_minutes: int = 60,
        start_date: str = "", end_date: str = "",
    ) -> str:
        return self._plans.create_plan(title, goal, daily_minutes, start_date, end_date)

    def list_plans(self, status: str | None = None) -> list[dict]:
        return self._plans.list_plans(status)

    def get_plan(self, plan_id: str) -> dict | None:
        return self._plans.get_plan(plan_id)

    def update_plan(self, plan_id: str, **fields) -> None:
        self._plans.update_plan(plan_id, **fields)

    def delete_plan(self, plan_id: str) -> None:
        self._plans.delete_plan(plan_id)

    # ── Tasks CRUD ──

    def create_task(
        self, plan_id: str, week_num: int, title: str,
        day_of_week: int | None = None, description: str = "",
        knowledge_points: str = "", order_idx: int = 0,
    ) -> str:
        return self._plans.create_task(
            plan_id, week_num, title, day_of_week, description, knowledge_points, order_idx
        )

    def get_tasks(self, plan_id: str, week_num: int | None = None) -> list[dict]:
        return self._plans.get_tasks(plan_id, week_num)

    def get_task(self, task_id: str) -> dict | None:
        return self._plans.get_task(task_id)

    def get_tasks_for_day(self, plan_id: str, week_num: int, day_of_week: int) -> list[dict]:
        return self._plans.get_tasks_for_day(plan_id, week_num, day_of_week)

    def update_task(self, task_id: str, **fields) -> None:
        self._plans.update_task(task_id, **fields)

    def delete_task(self, task_id: str) -> None:
        self._plans.delete_task(task_id)

    def reorder_tasks(self, plan_id: str, task_ids_in_order: list[str]) -> None:
        self._plans.reorder_tasks(plan_id, task_ids_in_order)

    # ── Notes CRUD ──

    def create_note(
        self, title: str, content: str = "", folder_id: str = "",
        source: str = "manual", linked_kp: str = "",
    ) -> str:
        return self._notes.create_note(title, content, folder_id, source, linked_kp)

    def get_note(self, note_id: str) -> dict | None:
        return self._notes.get_note(note_id)

    def list_notes(self, folder_id: str | None = None) -> list[dict]:
        return self._notes.list_notes(folder_id)

    def update_note(self, note_id: str, **fields) -> None:
        self._notes.update_note(note_id, **fields)

    def delete_note(self, note_id: str) -> None:
        self._notes.delete_note(note_id)

    def search_notes(self, query: str) -> list[dict]:
        return self._notes.search_notes(query)

    # ── Folders CRUD ──

    def create_folder(self, name: str, parent_id: str = "") -> str:
        return self._notes.create_folder(name, parent_id)

    def list_folders(self) -> list[dict]:
        return self._notes.list_folders()

    def delete_folder(self, folder_id: str) -> None:
        self._notes.delete_folder(folder_id)

    # ── Quiz CRUD ──

    def create_question(
        self, question_type: str, question: str, options: str,
        answer: str, explanation: str = "", knowledge_points: str = "",
        plan_id: str = "", task_id: str = "",
    ) -> str:
        return self._quiz.create_question(
            question_type, question, options, answer, explanation,
            knowledge_points, plan_id, task_id,
        )

    def get_question(self, question_id: str) -> dict | None:
        return self._quiz.get_question(question_id)

    def list_questions(self, plan_id: str | None = None) -> list[dict]:
        return self._quiz.list_questions(plan_id)

    def record_answer(
        self, question_id: str, user_answer: str, is_correct: bool,
        error_reason: str = "",
    ) -> str:
        return self._quiz.record_answer(question_id, user_answer, is_correct, error_reason)

    def list_answers(self, question_id: str) -> list[dict]:
        return self._quiz.list_answers(question_id)

    def get_wrong_answers(self) -> list[dict]:
        return self._quiz.get_wrong_answers()

    # ── Study Sessions ──

    def record_study_session(
        self, started_at: str, duration_seconds: int,
        task_id: str = "", plan_id: str = "", pomodoro_count: int = 0,
    ) -> str:
        return self._stats.record_study_session(
            started_at, duration_seconds, task_id, plan_id, pomodoro_count
        )

    def get_study_sessions(self, plan_id: str | None = None) -> list[dict]:
        return self._stats.get_study_sessions(plan_id)

    def get_total_study_time(self) -> int:
        return self._stats.get_total_study_time()

    # ── Checkins ──

    def checkin(self, date_str: str, task_ids_completed: str = "[]") -> str:
        return self._stats.checkin(date_str, task_ids_completed)

    def get_checkins(self, month: str | None = None) -> list[dict]:
        return self._stats.get_checkins(month)

    def get_streak(self) -> int:
        return self._stats.get_streak()

    def get_checkin_heatmap(self, month: str) -> list[dict]:
        return self._stats.get_checkin_heatmap(month)

    # ── Knowledge Points ──

    def upsert_kp(self, plan_id: str, name: str, mastery_level: int = 0) -> str:
        return self._stats.upsert_kp(plan_id, name, mastery_level)

    def get_kp(self, plan_id: str, name: str) -> dict | None:
        return self._stats.get_kp(plan_id, name)

    def list_kps(self, plan_id: str) -> list[dict]:
        return self._stats.list_kps(plan_id)
