from __future__ import annotations

"""Plan and task storage."""

import uuid
import sqlite3


class PlanStore:
    """Manages plans and tasks tables."""

    def __init__(self, conn: sqlite3.Connection, has_fts5: bool = True):
        self._conn = conn
        self._has_fts5 = has_fts5

    def create_plan(
        self, title: str, goal: str, daily_minutes: int = 60,
        start_date: str = "", end_date: str = "",
    ) -> str:
        pid = str(uuid.uuid4())
        with self._conn:
            self._conn.execute(
                """INSERT INTO plans (id, title, goal, daily_minutes, start_date, end_date)
                   VALUES (?, ?, ?, ?, ?, ?)""",
                (pid, title, goal, daily_minutes, start_date or None, end_date or None),
            )
        return pid

    def list_plans(self, status: str | None = None) -> list[dict]:
        with self._conn:
            if status:
                rows = self._conn.execute(
                    "SELECT * FROM plans WHERE status = ? ORDER BY created_at DESC",
                    (status,),
                ).fetchall()
            else:
                rows = self._conn.execute(
                    "SELECT * FROM plans ORDER BY created_at DESC"
                ).fetchall()
            return [dict(r) for r in rows]

    def get_plan(self, plan_id: str) -> dict | None:
        with self._conn:
            row = self._conn.execute(
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
        with self._conn:
            self._conn.execute(f"UPDATE plans SET {set_clause} WHERE id = ?", values)

    def delete_plan(self, plan_id: str) -> None:
        with self._conn:
            self._conn.execute("DELETE FROM plans WHERE id = ?", (plan_id,))

    def create_task(
        self, plan_id: str, week_num: int, title: str,
        day_of_week: int | None = None, description: str = "",
        knowledge_points: str = "", order_idx: int = 0,
    ) -> str:
        tid = str(uuid.uuid4())
        with self._conn:
            self._conn.execute(
                """INSERT INTO tasks
                   (id, plan_id, week_num, day_of_week, title, description, knowledge_points, order_idx)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                (tid, plan_id, week_num, day_of_week, title, description, knowledge_points, order_idx),
            )
        return tid

    def get_tasks(self, plan_id: str, week_num: int | None = None) -> list[dict]:
        with self._conn:
            if week_num is not None:
                rows = self._conn.execute(
                    "SELECT * FROM tasks WHERE plan_id = ? AND week_num = ? ORDER BY order_idx",
                    (plan_id, week_num),
                ).fetchall()
            else:
                rows = self._conn.execute(
                    "SELECT * FROM tasks WHERE plan_id = ? ORDER BY week_num, order_idx",
                    (plan_id,),
                ).fetchall()
            return [dict(r) for r in rows]

    def get_task(self, task_id: str) -> dict | None:
        with self._conn:
            row = self._conn.execute("SELECT * FROM tasks WHERE id = ?", (task_id,)).fetchone()
            return dict(row) if row else None

    def get_tasks_for_day(self, plan_id: str, week_num: int, day_of_week: int) -> list[dict]:
        with self._conn:
            rows = self._conn.execute(
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
        with self._conn:
            self._conn.execute(f"UPDATE tasks SET {set_clause} WHERE id = ?", values)

    def delete_task(self, task_id: str) -> None:
        with self._conn:
            self._conn.execute("DELETE FROM tasks WHERE id = ?", (task_id,))

    def reorder_tasks(self, plan_id: str, task_ids_in_order: list[str]) -> None:
        with self._conn:
            for idx, tid in enumerate(task_ids_in_order):
                self._conn.execute(
                    "UPDATE tasks SET order_idx = ? WHERE id = ? AND plan_id = ?",
                    (idx, tid, plan_id),
                )
