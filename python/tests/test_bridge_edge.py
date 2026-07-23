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
        with pytest.raises(RuntimeError):
            get_store()

    def test_abort_chat_when_not_started(self, tmp_path):
        init(str(tmp_path / "lumo"))
        # Should not raise
        abort_chat()


class TestBridgeGradeAnswer:
    def test_grade_nonexistent_question(self, bridge):
        result = grade_answer("nonexistent", "answer")
        data = json.loads(result)
        assert data["is_correct"] is False
        assert "not found" in data["explanation"].lower()

    def test_grade_objective_correct(self, bridge):
        from lumo.store import Store
        store = bridge
        qid = store.create_question(
            "single_choice", "What is 2+2?", '["1","2","3","4"]', "4",
            knowledge_points='["math"]',
        )
        result = grade_answer(qid, "4")
        data = json.loads(result)
        assert data["is_correct"] is True

    def test_grade_objective_wrong(self, bridge):
        from lumo.store import Store
        store = bridge
        qid = store.create_question(
            "single_choice", "What is 2+2?", '["1","2","3","4"]', "4",
            knowledge_points='["math"]',
        )
        result = grade_answer(qid, "3")
        data = json.loads(result)
        assert data["is_correct"] is False

    def test_grade_records_answer(self, bridge):
        store = bridge
        qid = store.create_question(
            "true_false", "Sky is blue?", '', "true",
        )
        grade_answer(qid, "false")
        wrong = store.get_wrong_answers()
        assert len(wrong) == 1
        assert wrong[0]["is_correct"] == 0


class TestBridgeTaskManagement:
    def test_update_task_status(self, bridge):
        store = bridge
        pid = store.create_plan("Plan", "Goal")
        tid = store.create_task(pid, 1, "Task 1")
        update_task_status(tid, "completed")
        task = store.get_task(tid)
        assert task["status"] == "completed"

    def test_reorder_plan_tasks(self, bridge):
        store = bridge
        pid = store.create_plan("Plan", "Goal")
        t1 = store.create_task(pid, 1, "Task A")
        t2 = store.create_task(pid, 1, "Task B")
        reorder_plan_tasks(pid, json.dumps([t2, t1]))
        tasks = store.get_tasks(pid)
        assert tasks[0]["title"] == "Task B"
        assert tasks[1]["title"] == "Task A"

    def test_delete_plan(self, bridge):
        store = bridge
        pid = store.create_plan("Plan", "Goal")
        delete_plan(pid)
        plans = json.loads(list_plans())
        assert len(plans) == 0

    def test_update_plan_status(self, bridge):
        store = bridge
        pid = store.create_plan("Plan", "Goal")
        update_plan_status(pid, "paused")
        plan = store.get_plan(pid)
        assert plan["status"] == "paused"

    def test_get_plan_tasks(self, bridge):
        store = bridge
        pid = store.create_plan("Plan", "Goal")
        store.create_task(pid, 1, "Task 1")
        store.create_task(pid, 1, "Task 2")
        tasks = json.loads(get_plan_tasks(pid))
        assert len(tasks) == 2


class TestBridgeStatsAndDaily:
    def test_get_stats_empty(self, bridge):
        result = get_stats()
        data = json.loads(result)
        assert data["total_study_time"] == 0
        assert data["streak"] == 0
        assert isinstance(data["study_sessions"], list)

    def test_get_study_trend_empty(self, bridge):
        result = get_study_trend("week")
        data = json.loads(result)
        assert data["period"] == "week"
        assert isinstance(data["data"], dict)
        assert data["total"] == 0

    def test_record_pomodoro(self, bridge):
        store = bridge
        pid = store.create_plan("Plan", "Goal")
        tid = store.create_task(pid, 1, "Task")
        sid = record_pomodoro(tid, pid, 1500, "2024-01-01T10:00:00Z")
        assert isinstance(sid, str)

    def test_checkin_today(self, bridge):
        store = bridge
        cid = checkin_today()
        assert isinstance(cid, str)
        # Checkin again should create a new record
        cid2 = checkin_today('["task1"]')
        assert cid2 != cid

    def test_get_today_tasks_empty(self, bridge):
        result = get_today_tasks()
        tasks = json.loads(result)
        assert tasks == []

    def test_get_today_tasks_with_plan(self, bridge):
        store = bridge
        pid = store.create_plan("Plan", "Goal")
        store.create_task(pid, 1, "Task 1")
        result = get_today_tasks()
        tasks = json.loads(result)
        assert len(tasks) == 1
        assert tasks[0]["plan_title"] == "Plan"

    def test_get_knowledge_mastery_empty(self, bridge):
        store = bridge
        pid = store.create_plan("Plan", "Goal")
        result = get_knowledge_mastery(pid)
        assert json.loads(result) == []


class TestBridgeQuizListing:
    def test_get_quiz_questions_all(self, bridge):
        store = bridge
        store.create_question("single_choice", "Q1", '["a","b"]', "a")
        store.create_question("true_false", "Q2", '', "true")
        result = get_quiz_questions()
        questions = json.loads(result)
        assert len(questions) == 2

    def test_get_quiz_errors_empty(self, bridge):
        result = get_quiz_errors()
        assert json.loads(result) == []

    def test_get_quiz_errors_with_wrong_answer(self, bridge):
        store = bridge
        qid = store.create_question("single_choice", "Q1", '["a","b"]', "a")
        store.record_answer(qid, "b", is_correct=False, error_reason="wrong")
        result = get_quiz_errors()
        errors = json.loads(result)
        assert len(errors) == 1
