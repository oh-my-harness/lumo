"""Tests for lumo.bridge — the Chaquopy entry point."""
import os
import pytest
from lumo.bridge import (
    init, get_store,
    save_provider_config, get_provider_config,
    create_session, list_sessions, get_messages, delete_session,
    search_messages, update_session_title,
    list_plans, create_plan,
    list_notes, search_notes, create_note, update_note, delete_note,
    list_folders, create_folder,
)


class TestBridgeInit:
    def test_init_creates_db(self, tmp_path):
        data_dir = str(tmp_path / "lumo")
        init(data_dir)
        store = get_store()
        assert store is not None
        assert os.path.exists(os.path.join(data_dir, "lumo.db"))

    def test_init_is_idempotent(self, tmp_path):
        data_dir = str(tmp_path / "lumo")
        init(data_dir)
        init(data_dir)

    def test_init_called_twice_with_different_dirs(self, tmp_path):
        dir1 = str(tmp_path / "lumo1")
        dir2 = str(tmp_path / "lumo2")
        init(dir1)
        store1 = get_store()
        store1.set_setting("test", "value1")
        init(dir2)
        store2 = get_store()
        assert store2.get_setting("test") is None


class TestBridgeProviderConfig:
    def test_save_and_get_provider_config(self, tmp_path):
        init(str(tmp_path / "lumo"))
        save_provider_config("openai", "sk-test", "http://api/", "gpt-4o")
        config = get_provider_config()
        assert config is not None
        assert config["provider_type"] == "openai"
        assert config["api_key"] == "sk-test"
        assert config["model"] == "gpt-4o"

    def test_get_provider_config_when_none(self, tmp_path):
        init(str(tmp_path / "lumo"))
        assert get_provider_config() is None


class TestBridgeSessions:
    def test_create_and_list_sessions(self, tmp_path):
        init(str(tmp_path / "lumo"))
        create_session("Test Chat")
        sessions = list_sessions()
        assert len(sessions) == 1
        assert sessions[0]["title"] == "Test Chat"

    def test_get_messages_empty(self, tmp_path):
        init(str(tmp_path / "lumo"))
        sid = create_session("Chat")
        assert get_messages(sid) == []

    def test_delete_session(self, tmp_path):
        init(str(tmp_path / "lumo"))
        sid = create_session("To Delete")
        delete_session(sid)
        assert len(list_sessions()) == 0

    def test_update_session_title(self, tmp_path):
        init(str(tmp_path / "lumo"))
        sid = create_session("Old")
        update_session_title(sid, "New")
        sessions = list_sessions()
        assert sessions[0]["title"] == "New"

    def test_search_messages_empty(self, tmp_path):
        init(str(tmp_path / "lumo"))
        create_session("Chat")
        assert search_messages("anything") == []


class TestBridgePlans:
    def test_create_and_list_plans(self, tmp_path):
        init(str(tmp_path / "lumo"))
        create_plan("前端基础", "2个月学会前端", 60, "2026-07-22", "2026-09-22")
        plans = list_plans()
        assert len(plans) == 1
        assert plans[0]["title"] == "前端基础"


class TestBridgeNotes:
    def test_create_and_list_notes(self, tmp_path):
        init(str(tmp_path / "lumo"))
        create_note("My Note", "Content")
        notes = list_notes()
        assert len(notes) == 1
        assert notes[0]["title"] == "My Note"

    def test_search_notes(self, tmp_path):
        init(str(tmp_path / "lumo"))
        create_note("Python", "Learn Python closures")
        create_note("JS", "Learn JavaScript")
        results = search_notes("Python")
        assert len(results) >= 1

    def test_update_note(self, tmp_path):
        init(str(tmp_path / "lumo"))
        nid = create_note("Old", "old")
        update_note(nid, title="New", content="new")
        notes = list_notes()
        assert notes[0]["title"] == "New"

    def test_delete_note(self, tmp_path):
        init(str(tmp_path / "lumo"))
        nid = create_note("Note", "content")
        delete_note(nid)
        assert len(list_notes()) == 0


class TestBridgeFolders:
    def test_create_and_list_folders(self, tmp_path):
        init(str(tmp_path / "lumo"))
        create_folder("My Folder")
        folders = list_folders()
        assert len(folders) == 1
        assert folders[0]["name"] == "My Folder"
