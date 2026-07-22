"""Lumo integration test on Android — verifies all modules via bridge layer."""

import json
import os


def test_init(data_dir):
    """Initialize bridge and verify DB is created."""
    from lumo.bridge import init, get_store
    init(data_dir)
    store = get_store()
    tables = store.list_tables()
    return f"Init OK. Tables: {len(tables)}"


def test_settings(data_dir):
    """Test settings CRUD via bridge."""
    from lumo.bridge import init, save_provider_config, get_provider_config
    init(data_dir)
    save_provider_config("openai", "sk-test-key", "http://api.test.com/", "gpt-4o")
    config = get_provider_config()
    return f"Settings OK. Provider: {config['provider_type']}, Model: {config['model']}"


def test_sessions(data_dir):
    """Test session CRUD via bridge."""
    from lumo.bridge import init, create_session, list_sessions, delete_session
    init(data_dir)
    sid = create_session("Test Chat")
    sessions = list_sessions()
    result = f"Sessions OK. Created: {sid[:8]}..., Count: {len(sessions)}"
    delete_session(sid)
    return result


def test_plans(data_dir):
    """Test plan CRUD via bridge."""
    from lumo.bridge import init, create_plan, list_plans
    init(data_dir)
    pid = create_plan("前端基础", "2个月学会前端", 60, "2026-07-22", "2026-09-22")
    plans = list_plans()
    return f"Plans OK. Created: {pid[:8]}..., Count: {len(plans)}, Title: {plans[0]['title']}"


def test_notes(data_dir):
    """Test notes CRUD via bridge."""
    from lumo.bridge import init, create_note, list_notes, search_notes
    init(data_dir)
    nid = create_note("Python 笔记", "Python 闭包和装饰器")
    notes = list_notes()
    results = search_notes("Python")
    return f"Notes OK. Created: {nid[:8]}..., Count: {len(notes)}, Search results: {len(results)}"


def test_quiz(data_dir):
    """Test quiz CRUD via bridge."""
    from lumo.bridge import init, create_plan, generate_quiz, grade_answer, get_quiz_questions
    init(data_dir)
    # Create a plan first
    pid = create_plan("测试计划", "测试", 30)
    # Manually create a question for grading test
    from lumo.bridge import _ensure_store
    store = _ensure_store()
    qid = store.create_question(
        "single_choice", "2+2=?", '["3","4","5","6"]', "4",
        explanation="2+2=4", knowledge_points='["math"]',
    )
    # Grade it
    result = grade_answer(qid, "4")
    # Grade wrong answer
    result_wrong = grade_answer(qid, "3")
    questions = get_quiz_questions()
    return f"Quiz OK. Correct grade: {result['is_correct']}, Wrong grade: {result_wrong['is_correct']}, Questions: {len(questions)}"


def test_stats(data_dir):
    """Test stats via bridge."""
    from lumo.bridge import init, checkin_today, get_streak, get_stats
    init(data_dir)
    checkin_today('["task1"]')
    streak = get_streak()
    stats = get_stats()
    return f"Stats OK. Streak: {streak}, Total study: {stats['total_study_time']}s"


def test_quick_prompts(data_dir):
    """Test quick prompts via bridge."""
    from lumo.bridge import init, get_quick_prompts
    init(data_dir)
    prompts = get_quick_prompts()
    return f"QuickPrompts OK. Count: {len(prompts)}, First: {prompts[0]['label']}"


def run_all_tests(data_dir):
    """Run all integration tests and return results."""
    results = []
    tests = [
        ("Init", test_init),
        ("Settings", test_settings),
        ("Sessions", test_sessions),
        ("Plans", test_plans),
        ("Notes", test_notes),
        ("Quiz", test_quiz),
        ("Stats", test_stats),
        ("QuickPrompts", test_quick_prompts),
    ]

    for name, test_fn in tests:
        try:
            # Use a fresh subdirectory for each test
            test_dir = os.path.join(data_dir, name.lower())
            os.makedirs(test_dir, exist_ok=True)
            result = test_fn(test_dir)
            results.append(f"✓ {name}: {result}")
        except Exception as e:
            results.append(f"✗ {name}: {type(e).__name__}: {e}")

    return "\n".join(results)
