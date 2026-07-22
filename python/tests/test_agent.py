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
