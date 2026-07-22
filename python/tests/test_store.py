"""Tests for lumo.store."""
import os
import uuid
import pytest
from lumo.store import Store


class TestStoreSchema:
    def test_store_creates_all_tables(self, tmp_db_path):
        """Store() should create all expected tables on init."""
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
        """Opening Store on existing db should not error."""
        Store(tmp_db_path)
        Store(tmp_db_path)

    def test_store_sets_wal_mode(self, tmp_db_path):
        """Store should enable WAL mode for concurrent access."""
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
