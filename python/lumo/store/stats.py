from __future__ import annotations

"""Study sessions, checkins, and knowledge points storage."""

import uuid
import sqlite3
from datetime import date, timedelta


class StatsStore:
    """Manages study_sessions, checkins, and knowledge_points tables."""

    def __init__(self, db_path: str, has_fts5: bool = True):
        self._db_path = db_path
        self._has_fts5 = has_fts5

    def _new_conn(self) -> sqlite3.Connection:
        """Create a new connection with proper settings."""
        conn = sqlite3.connect(self._db_path)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys=ON")
        return conn

    def record_study_session(
        self, started_at: str, duration_seconds: int,
        task_id: str = "", plan_id: str = "", pomodoro_count: int = 0,
    ) -> str:
        sid = str(uuid.uuid4())
        with self._new_conn() as conn:
            conn.execute(
                """INSERT INTO study_sessions
                   (id, task_id, plan_id, started_at, duration_seconds, pomodoro_count)
                   VALUES (?, ?, ?, ?, ?, ?)""",
                (sid, task_id or None, plan_id or None,
                 started_at, duration_seconds, pomodoro_count),
            )
        return sid

    def get_study_sessions(self, plan_id: str | None = None) -> list[dict]:
        with self._new_conn() as conn:
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
        with self._new_conn() as conn:
            row = conn.execute(
                "SELECT COALESCE(SUM(duration_seconds), 0) as total FROM study_sessions"
            ).fetchone()
            return row["total"]

    def checkin(self, date_str: str, task_ids_completed: str = "[]") -> str:
        cid = str(uuid.uuid4())
        with self._new_conn() as conn:
            conn.execute(
                "INSERT INTO checkins (id, date, task_ids_completed) VALUES (?, ?, ?)",
                (cid, date_str, task_ids_completed),
            )
        return cid

    def get_checkins(self, month: str | None = None) -> list[dict]:
        with self._new_conn() as conn:
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
        with self._new_conn() as conn:
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
        with self._new_conn() as conn:
            rows = conn.execute(
                "SELECT date, task_ids_completed FROM checkins WHERE date LIKE ? ORDER BY date",
                (f"{month}%",),
            ).fetchall()
            return [dict(r) for r in rows]

    def upsert_kp(self, plan_id: str, name: str, mastery_level: int = 0) -> str:
        with self._new_conn() as conn:
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
                return existing["id"]
            else:
                kid = str(uuid.uuid4())
                conn.execute(
                    """INSERT INTO knowledge_points (id, plan_id, name, mastery_level)
                       VALUES (?, ?, ?, ?)""",
                    (kid, plan_id, name, mastery_level),
                )
                return kid

    def get_kp(self, plan_id: str, name: str) -> dict | None:
        with self._new_conn() as conn:
            row = conn.execute(
                "SELECT * FROM knowledge_points WHERE plan_id = ? AND name = ?",
                (plan_id, name),
            ).fetchone()
            return dict(row) if row else None

    def list_kps(self, plan_id: str) -> list[dict]:
        with self._new_conn() as conn:
            rows = conn.execute(
                "SELECT * FROM knowledge_points WHERE plan_id = ? ORDER BY name",
                (plan_id,),
            ).fetchall()
            return [dict(r) for r in rows]
