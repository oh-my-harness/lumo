"""Edge-case and branch tests for lumo.bridge."""
import json
import pytest

from lumo.bridge import (
    init,
    get_store,
    create_session,
    create_plan,
    create_note,
    create_folder,
    list_plans,
    update_plan_status,
    delete_plan,
    update_task_status,
    reorder_plan_tasks,
    get_plan_tasks,
    get_today_tasks,
    record_pomodoro,
    checkin_today,
    get_stats,
    get_study_trend,
    get_knowledge_mastery,
    grade_answer,
    get_quiz_questions,
    get_quiz_errors,
    abort_chat,
    start_chat,
    send_message,
    stream_chat,
)


@pytest.fixture
def bridge(tmp_path):
    """Initialize bridge with a temp data dir."""
    init(str(tmp_path / "lumo"))
    return get_store()


class TestBridgeErrorPaths:
    def test_get_store_before_init_raises(self, tmp_path, monkeypatch):
        import lumo.bridge as b
        monkeypatch.setattr(b, "_store", None)
        with pytest.raises(RuntimeError, match="bridge.init"):
            get_store()

    def test_ensure_store_before_init_raises(self, tmp_path, monkeypatch):
        import lumo.bridge as b
        monkeypatch.setattr(b, "_store", None)
        with pytest.raises(RuntimeError, match="bridge.init"):
            create_session("test")

    def test_start_chat_without_provider_raises(self, bridge):
        sid = create_session("Chat")
        with pytest.raises(RuntimeError, match="Provider not configured"):
            start_chat(sid)

    def test_send_message_without_chat_raises(self, bridge):
        with pytest.raises(RuntimeError, match="Chat not started"):
            send_message("hello")

    def test_stream_chat_without_chat_raises(self, bridge):
        with pytest.raises(RuntimeError, match="Chat not started"):
            stream_chat("hello", type("cb", (), {"onToken": lambda self, t: None})())

    def test_abort_chat_without_session_is_noop(self, bridge):
        # Should not raise
        abort_chat()


class TestBridgeGradeAnswer:
    def test_grade_nonexistent_question(self, bridge):
        result = grade_answer("nonexistent", "answer")
        assert result["is_correct"] is False
        assert "not found" in result["explanation"].lower()

    def test_grade_objective_correct(self, bridge):
        from lumo.store import Store
        store = get_store()
        qid = store.create_question("single_choice", "Q?", '["a","b"]', "A")
        result = grade_answer(qid, "a")
        assert result["is_correct"] is True

    def test_grade_objective_wrong(self, bridge):
        from lumo.store import Store
        store = get_store()
        qid = store.create_question("single_choice", "Q?", '["a","b"]', "A")
        result = grade_answer(qid, "b")
        assert result["is_correct"] is False

    def test_grade_objective_case_insensitive(self, bridge):
        from lumo.store import Store
        store = get_store()
        qid = store.create_question("single_choice", "Q?", '["a","b"]', "A")
        result = grade_answer(qid, "  a  ")
        assert result["is_correct"] is True

    def test_grade_records_answer(self, bridge):
        from lumo.store import Store
        store = get_store()
        qid = store.create_question("single_choice", "Q?", '["a","b"]', "A")
        grade_answer(qid, "b")
        answers = store.list_answers(qid)
        assert len(answers) == 1
        assert answers[0]["is_correct"] == 0


class TestBridgeTaskManagement:
    def test_update_task_status(self, bridge):
        from lumo.store import Store
        store = get_store()
        pid = create_plan("Plan", "Goal")
        tid = store.create_task(pid, 1, "Task")
        update_task_status(tid, "completed")
        assert store.get_tasks(pid)[0]["status"] == "completed"

    def test_reorder_plan_tasks(self, bridge):
        from lumo.store import Store
        store = get_store()
        pid = create_plan("Plan", "Goal")
        t1 = store.create_task(pid, 1, "A", order_idx=0)
        t2 = store.create_task(pid, 1, "B", order_idx=1)
        reorder_plan_tasks(pid, json.dumps([t2, t1]))
        tasks = get_plan_tasks(pid)
        assert tasks[0]["title"] == "B"
        assert tasks[1]["title"] == "A"

    def test_get_plan_tasks_filtered_by_week(self, bridge):
        from lumo.store import Store
        store = get_store()
        pid = create_plan("Plan", "Goal")
        store.create_task(pid, 1, "Week 1")
        store.create_task(pid, 2, "Week 2")
        week1 = get_plan_tasks(pid, week_num=1)
        assert len(week1) == 1
        assert week1[0]["title"] == "Week 1"

    def test_update_plan_status(self, bridge):
        pid = create_plan("Plan", "Goal")
        update_plan_status(pid, "paused")
        assert list_plans(status="paused")[0]["title"] == "Plan"

    def test_delete_plan(self, bridge):
        pid = create_plan("Plan", "Goal")
        delete_plan(pid)
        assert len(list_plans()) == 0


class TestBridgeStatsAndDaily:
    def test_get_stats_empty(self, bridge):
        stats = get_stats()
        assert stats["total_study_time"] == 0
        assert stats["streak"] == 0
        assert stats["study_sessions"] == []

    def test_get_stats_with_data(self, bridge):
        from lumo.store import Store
        store = get_store()
        pid = create_plan("Plan", "Goal")
        store.create_task(pid, 1, "Task")
        record_pomodoro("", pid, 1500, "2026-07-22T10:00:00Z")
        stats = get_stats()
        assert stats["total_study_time"] == 1500
        assert len(stats["study_sessions"]) == 1

    def test_record_pomodoro_default_started_at(self, bridge):
        from lumo.store import Store
        store = get_store()
        pid = create_plan("Plan", "Goal")
        sid = record_pomodoro("", pid, 1500, "")
        sessions = store.get_study_sessions()
        assert len(sessions) == 1
        assert sessions[0]["pomodoro_count"] == 1
        assert sessions[0]["duration_seconds"] == 1500

    def test_checkin_today(self, bridge):
        from datetime import date
        cid = checkin_today('["task1"]')
        from lumo.store import Store
        store = get_store()
        checkins = store.get_checkins()
        assert len(checkins) == 1
        assert checkins[0]["date"] == date.today().isoformat()

    def test_get_today_tasks(self, bridge):
        from lumo.store import Store
        store = get_store()
        pid = create_plan("Plan", "Goal")
        store.create_task(pid, 1, "Task 1")
        store.create_task(pid, 1, "Task 2")
        tasks = get_today_tasks()
        assert len(tasks) == 2
        assert tasks[0]["plan_title"] == "Plan"

    def test_get_today_tasks_empty(self, bridge):
        tasks = get_today_tasks()
        assert tasks == []

    def test_get_study_trend_empty(self, bridge):
        trend = get_study_trend()
        assert trend["total"] == 0
        assert trend["data"] == {}

    def test_get_study_trend_with_data(self, bridge):
        record_pomodoro("", "", 1500, "2026-07-22T10:00:00Z")
        record_pomodoro("", "", 1800, "2026-07-22T14:00:00Z")
        trend = get_study_trend()
        assert trend["total"] == 3300
        assert "2026-07-22" in trend["data"]

    def test_get_knowledge_mastery_empty(self, bridge):
        pid = create_plan("Plan", "Goal")
        assert get_knowledge_mastery(pid) == []


class TestBridgeQuizListing:
    def test_get_quiz_questions_all(self, bridge):
        from lumo.store import Store
        store = get_store()
        store.create_question("single_choice", "Q1", '["a"]', "a")
        store.create_question("true_false", "Q2", "", "True")
        assert len(get_quiz_questions()) == 2

    def test_get_quiz_questions_by_plan(self, bridge):
        from lumo.store import Store
        store = get_store()
        pid = create_plan("Plan", "Goal")
        store.create_question("single_choice", "Q1", '["a"]', "a", plan_id=pid)
        store.create_question("true_false", "Q2", "", "True")
        assert len(get_quiz_questions(plan_id=pid)) == 1

    def test_get_quiz_errors_empty(self, bridge):
        assert get_quiz_errors() == []

    def test_get_quiz_errors_with_data(self, bridge):
        from lumo.store import Store
        store = get_store()
        qid = store.create_question("single_choice", "Q?", '["a","b"]', "a")
        grade_answer(qid, "b")  # wrong answer
        errors = get_quiz_errors()
        assert len(errors) == 1
