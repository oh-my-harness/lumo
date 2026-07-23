from __future__ import annotations

"""Quiz question and answer storage."""

import uuid
import sqlite3


class QuizStore:
    """Manages quiz_questions and quiz_answers tables."""

    def __init__(self, db_path: str, has_fts5: bool = True):
        self._db_path = db_path
        self._has_fts5 = has_fts5

    def _new_conn(self) -> sqlite3.Connection:
        """Create a new connection with proper settings."""
        conn = sqlite3.connect(self._db_path)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys=ON")
        return conn

    def create_question(
        self, question_type: str, question: str, options: str,
        answer: str, explanation: str = "", knowledge_points: str = "",
        plan_id: str = "", task_id: str = "",
    ) -> str:
        qid = str(uuid.uuid4())
        with self._new_conn() as conn:
            conn.execute(
                """INSERT INTO quiz_questions
                   (id, plan_id, task_id, question_type, question, options, answer, explanation, knowledge_points)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (qid, plan_id or None, task_id or None,
                 question_type, question, options, answer, explanation, knowledge_points),
            )
        return qid

    def get_question(self, question_id: str) -> dict | None:
        with self._new_conn() as conn:
            row = conn.execute(
                "SELECT * FROM quiz_questions WHERE id = ?", (question_id,)
            ).fetchone()
            return dict(row) if row else None

    def list_questions(self, plan_id: str | None = None) -> list[dict]:
        with self._new_conn() as conn:
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
        with self._new_conn() as conn:
            conn.execute(
                """INSERT INTO quiz_answers (id, question_id, user_answer, is_correct, error_reason)
                   VALUES (?, ?, ?, ?, ?)""",
                (aid, question_id, user_answer, 1 if is_correct else 0, error_reason),
            )
        return aid

    def list_answers(self, question_id: str) -> list[dict]:
        with self._new_conn() as conn:
            rows = conn.execute(
                "SELECT * FROM quiz_answers WHERE question_id = ? ORDER BY created_at",
                (question_id,),
            ).fetchall()
            return [dict(r) for r in rows]

    def get_wrong_answers(self) -> list[dict]:
        """Get all wrong answers joined with question details."""
        with self._new_conn() as conn:
            rows = conn.execute(
                """SELECT a.*, q.question, q.question_type, q.options,
                          q.answer as correct_answer, q.explanation, q.knowledge_points
                   FROM quiz_answers a
                   JOIN quiz_questions q ON a.question_id = q.id
                   WHERE a.is_correct = 0
                   ORDER BY a.created_at DESC"""
            ).fetchall()
            return [dict(r) for r in rows]
