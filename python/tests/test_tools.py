"""Tests for lumo.tools."""
import json
import pytest
from lumo.store import Store
from lumo.tools import (
    create_lumo_tools,
    make_read_memory_callback,
    make_write_memory_callback,
    make_search_notes_callback,
    make_search_quiz_errors_callback,
)


class TestReadMemory:
    def test_read_existing_memory(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.write_memory("global", "learning_style", "visual")
        cb = make_read_memory_callback(store)
        result = cb({"scope": "global", "key": "learning_style"}, None)
        assert result["terminate"] is False
        assert "visual" in result["content"][0]["text"]

    def test_read_missing_memory(self, tmp_db_path):
        store = Store(tmp_db_path)
        cb = make_read_memory_callback(store)
        result = cb({"scope": "global", "key": "nonexistent"}, None)
        text = result["content"][0]["text"]
        assert "no memory" in text.lower() or "无" in text


class TestWriteMemory:
    def test_write_memory(self, tmp_db_path):
        store = Store(tmp_db_path)
        cb = make_write_memory_callback(store)
        result = cb(
            {"scope": "global", "key": "weak_point", "value": "closures"}, None
        )
        assert result["terminate"] is False
        assert store.read_memory("global", "weak_point") == "closures"

    def test_write_memory_overwrites(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.write_memory("global", "style", "visual")
        cb = make_write_memory_callback(store)
        cb({"scope": "global", "key": "style", "value": "auditory"}, None)
        assert store.read_memory("global", "style") == "auditory"


class TestSearchNotes:
    def test_search_notes_with_results(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.create_note("Python", "Learn Python closures and decorators")
        cb = make_search_notes_callback(store)
        result = cb({"query": "Python"}, None)
        assert "Python" in result["content"][0]["text"]

    def test_search_notes_no_results(self, tmp_db_path):
        store = Store(tmp_db_path)
        cb = make_search_notes_callback(store)
        result = cb({"query": "nonexistent_xyz"}, None)
        text = result["content"][0]["text"]
        assert "no" in text.lower() or "无" in text


class TestSearchQuizErrors:
    def test_search_quiz_errors_with_results(self, tmp_db_path):
        store = Store(tmp_db_path)
        qid = store.create_question(
            "single_choice", "What is a closure?", '["A","B","C"]', "A",
            knowledge_points='["closures"]',
        )
        store.record_answer(qid, "B", is_correct=False,
                           error_reason="confused closures with decorators")
        cb = make_search_quiz_errors_callback(store)
        result = cb({"query": "closure"}, None)
        assert "closure" in result["content"][0]["text"].lower()

    def test_search_quiz_errors_empty(self, tmp_db_path):
        store = Store(tmp_db_path)
        cb = make_search_quiz_errors_callback(store)
        result = cb({"query": "nothing"}, None)
        text = result["content"][0]["text"]
        assert "no" in text.lower() or "无" in text


class TestCreateLumoTools:
    def test_creates_four_tools(self, tmp_db_path):
        store = Store(tmp_db_path)
        tools = create_lumo_tools(store)
        assert len(tools) == 4
        names = [t.name for t in tools]
        assert "read_memory" in names
        assert "write_memory" in names
        assert "search_notes" in names
        assert "search_quiz_errors" in names

    def test_tool_descriptions_nonempty(self, tmp_db_path):
        store = Store(tmp_db_path)
        tools = create_lumo_tools(store)
        for t in tools:
            assert len(t.description) > 10
