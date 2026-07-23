"""Tests for lumo.bridge — the Chaquopy entry point."""
import json
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
    get_quick_prompts, get_chat_history,
)


class TestBridgeInit:
    def test_init_creates_db(self, tmp_path):
        data_dir = str(tmp_path / "lumo")
        init(data_dir)
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
        store1.set_setting("test", "1")
        init(dir2)
        store2 = get_store()
        assert store2.get_setting("test") is None


class TestBridgeProviderConfig:
    def test_save_and_get_provider_config(self, tmp_path):
        init(str(tmp_path / "lumo"))
        save_provider_config("openai", "sk-test123", "http://api.example.com/", "gpt-4o")
        result = get_provider_config()
        assert result != "null"
        config = json.loads(result)
        assert config["provider_type"] == "openai"
        assert config["api_key"] == "sk-test123"
        assert config["model"] == "gpt-4o"

    def test_get_provider_config_when_none(self, tmp_path):
        init(str(tmp_path / "lumo"))
        assert get_provider_config() == "null"


class TestBridgeSessions:
    def test_create_and_list_sessions(self, tmp_path):
        init(str(tmp_path / "lumo"))
        sid = create_session("Test Session")
        assert isinstance(sid, str)
        result = list_sessions()
        sessions = json.loads(result)
        assert len(sessions) == 1
        assert sessions[0]["title"] == "Test Session"

    def test_delete_session(self, tmp_path):
        init(str(tmp_path / "lumo"))
        sid = create_session("To Delete")
        delete_session(sid)
        sessions = json.loads(list_sessions())
        assert len(sessions) == 0

    def test_search_messages_empty(self, tmp_path):
        init(str(tmp_path / "lumo"))
        result = search_messages("anything")
        assert json.loads(result) == []


class TestBridgePlans:
    def test_create_and_list_plans(self, tmp_path):
        init(str(tmp_path / "lumo"))
        pid = create_plan("前端基础", "学前端", 60, "2024-01-01", "2024-03-01")
        plans = json.loads(list_plans())
        assert len(plans) == 1
        assert plans[0]["title"] == "前端基础"


class TestBridgeNotes:
    def test_create_and_list_notes(self, tmp_path):
        init(str(tmp_path / "lumo"))
        nid = create_note("My Note", "Content here")
        notes = json.loads(list_notes())
        assert len(notes) == 1
        assert notes[0]["title"] == "My Note"

    def test_update_note(self, tmp_path):
        init(str(tmp_path / "lumo"))
        nid = create_note("Original", "Original content")
        update_note(nid, "Updated", "Updated content")
        notes = json.loads(list_notes())
        assert notes[0]["title"] == "Updated"

    def test_delete_note(self, tmp_path):
        init(str(tmp_path / "lumo"))
        nid = create_note("To Delete", "Content")
        delete_note(nid)
        assert len(json.loads(list_notes())) == 0

    def test_search_notes_empty(self, tmp_path):
        init(str(tmp_path / "lumo"))
        assert json.loads(search_notes("nonexistent")) == []


class TestBridgeFolders:
    def test_create_and_list_folders(self, tmp_path):
        init(str(tmp_path / "lumo"))
        fid = create_folder("My Folder")
        folders = json.loads(list_folders())
        assert len(folders) == 1
        assert folders[0]["name"] == "My Folder"


class TestBridgeChat:
    def test_get_quick_prompts(self, tmp_path):
        init(str(tmp_path / "lumo"))
        result = get_quick_prompts()
        prompts = json.loads(result)
        assert len(prompts) >= 4
        for p in prompts:
            assert "key" in p
            assert "label" in p
            assert "prompt" in p

    def test_get_chat_history_not_started(self, tmp_path):
        init(str(tmp_path / "lumo"))
        with pytest.raises(RuntimeError):
            get_chat_history()
