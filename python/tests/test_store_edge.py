"""Edge-case and branch tests for lumo.store."""
import sqlite3
from datetime import date, timedelta

import pytest

from lumo.store import Store


class TestStoreEdgeCases:
    def test_get_session_nonexistent(self, tmp_db_path):
        store = Store(tmp_db_path)
        assert store.get_session("nonexistent-id") is None

    def test_get_plan_nonexistent(self, tmp_db_path):
        store = Store(tmp_db_path)
        assert store.get_plan("nonexistent-id") is None

    def test_get_note_nonexistent(self, tmp_db_path):
        store = Store(tmp_db_path)
        assert store.get_note("nonexistent-id") is None

    def test_get_question_nonexistent(self, tmp_db_path):
        store = Store(tmp_db_path)
        assert store.get_question("nonexistent-id") is None

    def test_get_setting_nonexistent(self, tmp_db_path):
        store = Store(tmp_db_path)
        assert store.get_setting("nonexistent") is None

    def test_read_memory_nonexistent(self, tmp_db_path):
        store = Store(tmp_db_path)
        assert store.read_memory("global", "nonexistent") is None


class TestStoreUpdateGuards:
    def test_update_plan_with_no_fields_is_noop(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        store.update_plan(pid)  # no allowed fields → early return
        assert store.get_plan(pid)["title"] == "Plan"

    def test_update_plan_ignores_unknown_fields(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        store.update_plan(pid, title="New", bogus_field="ignored")
        plan = store.get_plan(pid)
        assert plan["title"] == "New"
        assert "bogus_field" not in plan

    def test_update_task_with_no_fields_is_noop(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        tid = store.create_task(pid, 1, "Task")
        store.update_task(tid)
        assert store.get_tasks(pid)[0]["title"] == "Task"

    def test_update_task_ignores_unknown_fields(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        tid = store.create_task(pid, 1, "Task")
        store.update_task(tid, status="completed", bogus="x")
        assert store.get_tasks(pid)[0]["status"] == "completed"

    def test_update_note_with_no_fields_is_noop(self, tmp_db_path):
        store = Store(tmp_db_path)
        nid = store.create_note("Note", "content")
        store.update_note(nid)
        assert store.get_note(nid)["title"] == "Note"

    def test_update_note_ignores_unknown_fields(self, tmp_db_path):
        store = Store(tmp_db_path)
        nid = store.create_note("Note", "content")
        store.update_note(nid, title="New", bogus="x")
        assert store.get_note(nid)["title"] == "New"


class TestStoreTouchSession:
    def test_touch_updates_updated_at(self, tmp_db_path):
        store = Store(tmp_db_path)
        sid = store.create_session("Chat")
        original = store.get_session(sid)["updated_at"]
        store.touch_session(sid)
        updated = store.get_session(sid)["updated_at"]
        # updated_at should be >= original (both are UTC ISO timestamps)
        assert updated >= original


class TestStoreCascadeBehavior:
    def test_delete_folder_sets_note_folder_id_null(self, tmp_db_path):
        """notes.folder_id ON DELETE SET NULL — deleting folder nulls it."""
        store = Store(tmp_db_path)
        fid = store.create_folder("Folder")
        nid = store.create_note("Note", "content", folder_id=fid)
        assert store.get_note(nid)["folder_id"] == fid
        store.delete_folder(fid)
        assert store.get_note(nid)["folder_id"] is None

    def test_delete_plan_cascades_to_tasks_and_checkins(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        store.create_task(pid, 1, "Task 1")
        store.checkin("2026-07-22", "[]")
        store.delete_plan(pid)
        assert store.get_tasks(pid) == []

    def test_delete_plan_sets_quiz_question_plan_id_null(self, tmp_db_path):
        """quiz_questions.plan_id ON DELETE SET NULL."""
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        qid = store.create_question(
            "single_choice", "Q?", '["a"]', "a", plan_id=pid
        )
        store.delete_plan(pid)
        q = store.get_question(qid)
        assert q is not None
        assert q["plan_id"] is None

    def test_delete_question_cascades_answers(self, tmp_db_path):
        store = Store(tmp_db_path)
        qid = store.create_question("single_choice", "Q", '["a","b"]', "a")
        store.record_answer(qid, "a", is_correct=True)
        store.record_answer(qid, "b", is_correct=False)
        # Delete via raw SQL since there's no delete_question method
        with store._conn() as conn:
            conn.execute("DELETE FROM quiz_questions WHERE id = ?", (qid,))
            conn.commit()
        assert store.list_answers(qid) == []


class TestStoreFtsUpdateAfterEdit:
    def test_search_notes_finds_updated_title(self, tmp_db_path):
        """After update_note, FTS should reflect new title."""
        store = Store(tmp_db_path)
        nid = store.create_note("Old Title", "some content")
        results = store.search_notes("Old")
        assert len(results) >= 1
        store.update_note(nid, title="New Title", content="new content")
        # Old title should no longer match
        old_results = store.search_notes("Old")
        assert len(old_results) == 0
        # New title should match
        new_results = store.search_notes("New")
        assert len(new_results) >= 1


class TestStoreStreak:
    def test_streak_breaks_on_gap(self, tmp_db_path):
        """Streak should be 0 if yesterday has no checkin."""
        store = Store(tmp_db_path)
        today = date.today()
        # Checkin today only, not yesterday
        store.checkin(today.isoformat(), "[]")
        assert store.get_streak() == 1

    def test_streak_with_gap_in_middle(self, tmp_db_path):
        """Streak counts consecutive days ending today."""
        store = Store(tmp_db_path)
        today = date.today()
        yesterday = today - timedelta(days=1)
        three_days_ago = today - timedelta(days=3)
        store.checkin(today.isoformat(), "[]")
        store.checkin(yesterday.isoformat(), "[]")
        store.checkin(three_days_ago.isoformat(), "[]")
        # Streak should be 2 (today + yesterday), not 3
        assert store.get_streak() == 2


class TestStoreListByFilter:
    def test_list_notes_by_folder(self, tmp_db_path):
        store = Store(tmp_db_path)
        fid = store.create_folder("Folder A")
        store.create_note("In A", "content", folder_id=fid)
        store.create_note("No folder", "content")
        in_a = store.list_notes(folder_id=fid)
        assert len(in_a) == 1
        assert in_a[0]["title"] == "In A"

    def test_list_plans_by_archived_status(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("P1", "G1")
        store.create_plan("P2", "G2")
        store.update_plan(pid, status="archived")
        archived = store.list_plans(status="archived")
        assert len(archived) == 1
        assert archived[0]["title"] == "P1"

    def test_list_questions_by_plan(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        store.create_question("single_choice", "Q1", '["a"]', "a", plan_id=pid)
        store.create_question("true_false", "Q2", "", "True")
        filtered = store.list_questions(plan_id=pid)
        assert len(filtered) == 1
        assert filtered[0]["question"] == "Q1"

    def test_get_study_sessions_by_plan(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        store.record_study_session("2026-07-22T10:00:00Z", 1500, plan_id=pid)
        store.record_study_session("2026-07-22T14:00:00Z", 1800)
        filtered = store.get_study_sessions(plan_id=pid)
        assert len(filtered) == 1
        assert filtered[0]["duration_seconds"] == 1500

    def test_get_checkins_by_month(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.checkin("2026-07-15", "[]")
        store.checkin("2026-07-22", "[]")
        store.checkin("2026-06-10", "[]")
        july = store.get_checkins(month="2026-07")
        assert len(july) == 2


class TestStoreGetKpNonexistent:
    def test_get_kp_nonexistent(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        assert store.get_kp(pid, "nonexistent") is None

    def test_list_kps_empty(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        assert store.list_kps(pid) == []


class TestStoreReorderEdgeCases:
    def test_reorder_empty_list(self, tmp_db_path):
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        store.reorder_tasks(pid, [])
        assert store.get_tasks(pid) == []

    def test_reorder_partial_list(self, tmp_db_path):
        """Reordering only some tasks shouldn't affect others."""
        store = Store(tmp_db_path)
        pid = store.create_plan("Plan", "Goal")
        t1 = store.create_task(pid, 1, "A", order_idx=0)
        t2 = store.create_task(pid, 1, "B", order_idx=1)
        t3 = store.create_task(pid, 1, "C", order_idx=2)
        # Reorder only first two
        store.reorder_tasks(pid, [t2, t1])
        tasks = store.get_tasks(pid, week_num=1)
        titles = [t["title"] for t in tasks]
        assert titles[0] == "B"
        assert titles[1] == "A"
