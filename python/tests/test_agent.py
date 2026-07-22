"""Tests for lumo.agent."""
import pytest
from lumo.store import Store
from lumo.config import ProviderConfig
from lumo.agent import ChatSession


class TestChatSession:
    def test_chat_session_creates_harness(self, tmp_db_path):
        """ChatSession should be constructable with a valid config."""
        store = Store(tmp_db_path)
        config = ProviderConfig("openai", "sk-test", "", "gpt-4o")
        sid = store.create_session("Test")
        session = ChatSession(store, config, sid)
        assert session is not None

    def test_chat_session_has_empty_history(self, tmp_db_path):
        store = Store(tmp_db_path)
        config = ProviderConfig("openai", "sk-test", "", "gpt-4o")
        sid = store.create_session("Test")
        session = ChatSession(store, config, sid)
        assert session.get_history() == []

    def test_chat_session_has_usage(self, tmp_db_path):
        store = Store(tmp_db_path)
        config = ProviderConfig("openai", "sk-test", "", "gpt-4o")
        sid = store.create_session("Test")
        session = ChatSession(store, config, sid)
        usage = session.usage()
        assert "total_input_tokens" in usage
        assert usage["total_input_tokens"] == 0


class TestGradeShortAnswer:
    def test_grade_nonexistent_question(self, tmp_db_path):
        from lumo.agent import grade_short_answer
        store = Store(tmp_db_path)
        config = ProviderConfig("openai", "sk-test", "", "gpt-4o")
        result = grade_short_answer(store, config, "nonexistent", "answer")
        assert result["is_correct"] is False
        assert "not found" in result["explanation"].lower()


class TestSummarizeNote:
    def test_summarize_nonexistent_note(self, tmp_db_path):
        from lumo.agent import summarize_note
        store = Store(tmp_db_path)
        config = ProviderConfig("openai", "sk-test", "", "gpt-4o")
        result = summarize_note(store, config, "nonexistent")
        assert "not found" in result.lower()


class TestSummarizeConversation:
    def test_summarize_empty_conversation(self, tmp_db_path):
        from lumo.agent import summarize_conversation
        store = Store(tmp_db_path)
        config = ProviderConfig("openai", "sk-test", "", "gpt-4o")
        sid = store.create_session("Empty")
        result = summarize_conversation(store, config, sid)
        assert "no messages" in result.lower()
