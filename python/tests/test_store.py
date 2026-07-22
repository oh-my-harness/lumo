"""Tests for lumo.store."""
import os
import uuid
import pytest
from lumo.store import Store


class TestStoreSchema:
    def test_store_creates_all_tables(self, tmp_db_path):
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
        Store(tmp_db_path)
        Store(tmp_db_path)

    def test_store_sets_wal_mode(self, tmp_db_path):
        Store(tmp_db_path)
        import sqlite3
        conn = sqlite3.connect(tmp_db_path)
        mode = conn.execute("PRAGMA journal_mode").fetchone()[0]
        conn.close()
        assert mode == "wal"


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
