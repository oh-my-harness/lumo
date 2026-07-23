"""Quiz bridge functions (Module 4)."""

from __future__ import annotations

import json
import os

from lumo.bridge._serialization import to_json


def list_questions(plan_id: str = "") -> str:
    """List quiz questions as JSON. Optional plan_id filter."""
    from lumo.bridge import _ensure_store
    store = _ensure_store()
    return to_json(store.list_questions(plan_id if plan_id else None))


def get_wrong_answers() -> str:
    """Get all wrong answers with question details as JSON."""
    from lumo.bridge import _ensure_store
    return to_json(_ensure_store().get_wrong_answers())


def record_answer(
    question_id: str, user_answer: str, is_correct: bool,
    error_reason: str = "",
) -> str:
    """Record an answer. Returns the answer ID."""
    from lumo.bridge import _ensure_store
    return _ensure_store().record_answer(
        question_id, user_answer, is_correct, error_reason
    )


def generate_quiz(knowledge_points: str, num_questions: int = 3,
                  plan_id: str = "", task_id: str = "") -> str:
    """Run quiz generation workflow. Returns JSON with questions, verified, cost."""
    from lumo.bridge import _ensure_store, _get_data_dir
    from lumo.workflows import run_quiz_workflow
    from lumo.config import get_provider_config

    store = _ensure_store()
    config = get_provider_config(store)
    if config is None:
        raise RuntimeError("Provider not configured")

    session_dir = os.path.join(_get_data_dir(), "workflows")
    os.makedirs(session_dir, exist_ok=True)

    result = run_quiz_workflow(
        store, config, session_dir, knowledge_points, num_questions,
        plan_id, task_id,
    )

    _TYPE_MAP = {
        "single_choice": "single_choice",
        "multi_choice": "multi_choice",
        "true_false": "true_false",
        "short_answer": "short_answer",
        "single": "single_choice",
        "multiple": "multi_choice",
        "multi": "multi_choice",
        "truefalse": "true_false",
        "boolean": "true_false",
        "判断": "true_false",
        "单选": "single_choice",
        "多选": "multi_choice",
        "简答": "short_answer",
        "填空": "short_answer",
    }
    question_ids = []
    for q in result.get("questions", []):
        raw_type = q.get("type", "single_choice")
        normalized_type = _TYPE_MAP.get(raw_type, "single_choice")
        qid = store.create_question(
            question_type=normalized_type,
            question=q.get("question", ""),
            options=json.dumps(q.get("options", []), ensure_ascii=False),
            answer=q.get("answer", ""),
            explanation=q.get("explanation", ""),
            knowledge_points=json.dumps(q.get("knowledge_points", []), ensure_ascii=False),
            plan_id=plan_id,
            task_id=task_id,
        )
        question_ids.append(qid)
    result["question_ids"] = question_ids

    return to_json(result)


def grade_answer(question_id: str, user_answer: str) -> str:
    """Grade an answer. Objective questions are graded directly;
    short_answer questions use AI grading.

    Returns JSON with is_correct and explanation.
    """
    from lumo.bridge import _ensure_store
    from lumo.agent import grade_short_answer
    from lumo.config import get_provider_config

    store = _ensure_store()
    question = store.get_question(question_id)
    if not question:
        return to_json({"is_correct": False, "explanation": "Question not found"})

    q_type = question.get("question_type", "")
    correct_answer = question.get("answer", "")

    if q_type == "short_answer":
        config = get_provider_config(store)
        if config is None:
            return to_json({"is_correct": False, "explanation": "Provider not configured"})
        result = grade_short_answer(store, config, question_id, user_answer)
        store.record_answer(
            question_id, user_answer,
            is_correct=result["is_correct"],
            error_reason=result.get("explanation", ""),
        )
        _update_mastery_after_answer(store, question, result["is_correct"])
        return to_json(result)
    else:
        is_correct = user_answer.strip().upper() == correct_answer.strip().upper()
        error_reason = "" if is_correct else f"Correct answer: {correct_answer}"
        store.record_answer(question_id, user_answer, is_correct, error_reason)
        _update_mastery_after_answer(store, question, is_correct)
        return to_json({
            "is_correct": is_correct,
            "explanation": question.get("explanation", ""),
        })


def _update_mastery_after_answer(store, question: dict, is_correct: bool) -> None:
    """Update knowledge point mastery and record weak points in memory.

    - Correct answer: +10 mastery (cap 100)
    - Wrong answer: -15 mastery (floor 0), and record to memory as weak point
    """
    kp_json = question.get("knowledge_points", "[]")
    try:
        kps = json.loads(kp_json) if isinstance(kp_json, str) else kp_json
    except Exception:
        kps = []
    if not kps:
        return

    plan_id = question.get("plan_id", "")
    if not plan_id:
        return

    for kp_name in kps:
        if not isinstance(kp_name, str) or not kp_name.strip():
            continue
        existing = store.get_kp(plan_id, kp_name)
        if existing:
            current = existing.get("mastery_level", 0)
            if is_correct:
                new_level = min(100, current + 10)
            else:
                new_level = max(0, current - 15)
            store.upsert_kp(plan_id, kp_name, new_level)
        else:
            store.upsert_kp(plan_id, kp_name, 0 if not is_correct else 10)

    if not is_correct:
        try:
            existing_weak = store.read_memory("global", "weak_points") or ""
            weak_set = set(
                w.strip() for w in existing_weak.split(",") if w.strip()
            )
            for kp_name in kps:
                if isinstance(kp_name, str) and kp_name.strip():
                    weak_set.add(kp_name.strip())
            store.write_memory("global", "weak_points", ", ".join(sorted(weak_set)))
        except Exception:
            pass


def get_quiz_questions(plan_id: str = "") -> str:
    """List quiz questions as JSON. Optional plan_id filter."""
    from lumo.bridge import _ensure_store
    store = _ensure_store()
    return to_json(store.list_questions(plan_id if plan_id else None))


def get_quiz_errors() -> str:
    """Get all wrong answers with question details as JSON."""
    from lumo.bridge import _ensure_store
    return to_json(_ensure_store().get_wrong_answers())
