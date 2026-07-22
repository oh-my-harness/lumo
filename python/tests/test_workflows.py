"""Tests for lumo.workflows."""
import json
import pytest
from lumo.workflows import _extract_json, PLAN_WORKFLOW, QUIZ_WORKFLOW, _plan_judge, _quiz_judge


class TestExtractJson:
    def test_plain_json(self):
        result = _extract_json('{"key": "value"}')
        assert result == {"key": "value"}

    def test_json_in_code_fence(self):
        text = 'Here is the result:\n```json\n{"a": 1}\n```\nDone.'
        result = _extract_json(text)
        assert result == {"a": 1}

    def test_json_in_plain_code_fence(self):
        text = '```\n{"b": 2}\n```'
        result = _extract_json(text)
        assert result == {"b": 2}

    def test_invalid_json_raises(self):
        with pytest.raises(json.JSONDecodeError):
            _extract_json("not json at all")


class TestPlanWorkflow:
    def test_workflow_has_four_steps(self):
        assert len(PLAN_WORKFLOW["steps"]) == 4

    def test_entry_step_is_decompose(self):
        assert PLAN_WORKFLOW["entry_step"] == "decompose"

    def test_edges_chain_correctly(self):
        step_ids = [s["id"] for s in PLAN_WORKFLOW["steps"]]
        for step_id in step_ids[:-1]:
            edges = [e for e in PLAN_WORKFLOW["edges"] if e["from"] == step_id]
            assert len(edges) >= 1

    def test_judge_routes_decompose_to_detail(self):
        result = _plan_judge({"step_id": "decompose"})
        assert result == "to:detail"

    def test_judge_routes_detail_to_verify(self):
        result = _plan_judge({"step_id": "detail"})
        assert result == "to:verify"

    def test_judge_routes_verify_approved_to_publish(self):
        result = _plan_judge({"step_id": "verify", "output": "APPROVED"})
        assert result == "to:publish"

    def test_judge_routes_verify_issues_back_to_detail(self):
        result = _plan_judge({"step_id": "verify", "output": "ISSUES: too many tasks"})
        assert result == "to:detail"

    def test_judge_routes_publish_to_done(self):
        result = _plan_judge({"step_id": "publish"})
        assert result == "done"


class TestQuizWorkflow:
    def test_workflow_has_three_steps(self):
        assert len(QUIZ_WORKFLOW["steps"]) == 3

    def test_entry_step_is_generate(self):
        assert QUIZ_WORKFLOW["entry_step"] == "generate"

    def test_judge_routes_generate_to_verify(self):
        result = _quiz_judge({"step_id": "generate"})
        assert result == "to:verify"

    def test_judge_routes_verify_approved_to_publish(self):
        result = _quiz_judge({"step_id": "verify", "output": "APPROVED"})
        assert result == "to:publish"

    def test_judge_routes_verify_issues_back_to_generate(self):
        result = _quiz_judge({"step_id": "verify", "output": "ISSUES: wrong answer"})
        assert result == "to:generate"

    def test_judge_routes_publish_to_done(self):
        result = _quiz_judge({"step_id": "publish"})
        assert result == "done"
