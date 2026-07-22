# Lumo P0 — Plan 1C: 模型配置 + Chaquopy 桥接层

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现模型配置管理（模块 7：provider 存储 + 创建 + 连通性验证）和 Chaquopy 桥接层（Kotlin 侧的唯一入口，所有函数返回 JSON 可序列化类型）。

**Architecture:** `lumo.config` 管理 LLM provider 配置，`lumo.bridge` 是 Kotlin 通过 Chaquopy 调用的入口。bridge 持有 Store 单例，所有函数接收/返回 Python 原生类型。

**Tech Stack:** Python 3.12, Senza (PyO3), Chaquopy, pytest

## Global Constraints

- 继承 Plan 1A/1B 的全部约束
- bridge.py 的所有函数接收/返回 JSON 可序列化的 Python 原生类型（str, int, float, bool, list, dict, None）
- 不暴露 Senza 或 Store 对象给 Kotlin
- Senza import 在桌面测试时可能不可用（没有 PyO3 扩展），测试用 mock
- 支持的 provider 类型：`openai`（兼容 OpenAI API）、`anthropic`

---

## Task 7: Config — 模型配置管理（模块 7）

**Files:**
- Create: `python/lumo/config.py`
- Create: `python/tests/test_config.py`

**Interfaces:**
- Consumes: `Store` from Plan 1A（settings 表）
- Produces: `lumo.config.ProviderConfig` dataclass（`provider_type`, `api_key`, `base_url`, `model`）
- Produces: `lumo.config.get_provider_config(store) -> ProviderConfig | None`
- Produces: `lumo.config.save_provider_config(store, config) -> None`
- Produces: `lumo.config.create_provider(config) -> senza.Provider`
- Produces: `lumo.config.test_connection(config) -> tuple[bool, str]`

- [ ] **Step 1: 写失败测试**

Create `python/tests/test_config.py`:
```python
"""Tests for lumo.config."""
import json
import pytest
from lumo.store import Store
from lumo.config import ProviderConfig, get_provider_config, save_provider_config


class TestProviderConfig:
    def test_save_and_get_config(self, tmp_db_path):
        store = Store(tmp_db_path)
        config = ProviderConfig(
            provider_type="openai",
            api_key="sk-test123",
            base_url="http://api.example.com/",
            model="gpt-4o",
        )
        save_provider_config(store, config)
        loaded = get_provider_config(store)
        assert loaded is not None
        assert loaded.provider_type == "openai"
        assert loaded.api_key == "sk-test123"
        assert loaded.base_url == "http://api.example.com/"
        assert loaded.model == "gpt-4o"

    def test_get_config_when_none_returns_none(self, tmp_db_path):
        store = Store(tmp_db_path)
        assert get_provider_config(store) is None

    def test_update_config(self, tmp_db_path):
        store = Store(tmp_db_path)
        save_provider_config(store, ProviderConfig(
            "openai", "sk-old", "http://old/", "gpt-4o"
        ))
        save_provider_config(store, ProviderConfig(
            "anthropic", "sk-new", "", "claude-3"
        ))
        loaded = get_provider_config(store)
        assert loaded.provider_type == "anthropic"
        assert loaded.api_key == "sk-new"
        assert loaded.model == "claude-3"

    def test_config_to_dict(self):
        config = ProviderConfig("openai", "sk-123", "http://api/", "gpt-4o")
        d = config.to_dict()
        assert d["provider_type"] == "openai"
        assert d["model"] == "gpt-4o"

    def test_config_from_dict(self):
        d = {"provider_type": "openai", "api_key": "sk-1", "base_url": "", "model": "gpt-4o"}
        config = ProviderConfig.from_dict(d)
        assert config.api_key == "sk-1"
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd python && python -m pytest tests/test_config.py -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'lumo.config'`

- [ ] **Step 3: 实现 config 模块**

Create `python/lumo/config.py`:
```python
"""Model configuration management for Lumo.

Handles LLM provider configuration: storage, creation, and connection testing.
Module 7 of P0 spec.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, asdict
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from lumo.store import Store


@dataclass
class ProviderConfig:
    """LLM provider configuration."""
    provider_type: str   # "openai" or "anthropic"
    api_key: str
    base_url: str
    model: str

    def to_dict(self) -> dict:
        return asdict(self)

    @classmethod
    def from_dict(cls, d: dict) -> ProviderConfig:
        return cls(
            provider_type=d["provider_type"],
            api_key=d["api_key"],
            base_url=d.get("base_url", ""),
            model=d["model"],
        )


def get_provider_config(store: Store) -> ProviderConfig | None:
    """Load provider config from settings. Returns None if not configured."""
    raw = store.get_setting("provider_config")
    if not raw:
        return None
    return ProviderConfig.from_dict(json.loads(raw))


def save_provider_config(store: Store, config: ProviderConfig) -> None:
    """Save provider config to settings."""
    store.set_setting("provider_config", json.dumps(config.to_dict()))


def create_provider(config: ProviderConfig):
    """Create a Senza provider from config."""
    import senza

    if config.provider_type == "anthropic":
        kwargs = {"api_key": config.api_key}
        if config.base_url:
            kwargs["base_url"] = config.base_url
        return senza.create_anthropic_provider(**kwargs)
    else:
        # Default: OpenAI-compatible
        kwargs = {"api_key": config.api_key}
        if config.base_url:
            kwargs["base_url"] = config.base_url
        return senza.create_openai_provider(**kwargs)


def test_connection(config: ProviderConfig) -> tuple[bool, str]:
    """Test LLM connection. Returns (success, message)."""
    try:
        import senza

        provider = create_provider(config)
        harness = (
            senza.HarnessBuilder(config.model)
            .provider("*", provider)
            .system_prompt("You are a test assistant. Reply with 'OK'.")
            .max_tokens(10)
            .build()
        )
        events = harness.prompt_and_collect("Say OK", timeout_ms=15000)
        text = ""
        for event in events:
            if event["type"] == "text_delta":
                text += event.get("text", "")
        return True, f"Connection OK. Response: {text[:50]}"
    except Exception as e:
        return False, f"Connection failed: {e}"
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd python && python -m pytest tests/test_config.py -v`
Expected: 5 PASSED

- [ ] **Step 5: Commit**

```bash
git add python/lumo/config.py python/tests/test_config.py
git commit -m "feat: add model config management (module 7)"
```

---

## Task 8: Bridge — Chaquopy 桥接层

**Files:**
- Create: `python/lumo/bridge.py`
- Create: `python/tests/test_bridge.py`

**Interfaces:**
- Consumes: `Store` from Plan 1A, `ProviderConfig` + functions from Task 7
- Produces: `lumo.bridge.init(data_dir) -> None` — 初始化数据目录和 store 单例
- Produces: `lumo.bridge.get_store() -> Store` — 获取 store 单例
- Produces: `lumo.bridge.save_provider_config(provider_type, api_key, base_url, model) -> None`
- Produces: `lumo.bridge.get_provider_config() -> dict | None`
- Produces: `lumo.bridge.test_provider_connection(provider_type, api_key, base_url, model) -> str`
- Produces: `lumo.bridge.create_session(title) -> str`
- Produces: `lumo.bridge.list_sessions() -> list[dict]`
- Produces: `lumo.bridge.get_messages(session_id) -> list[dict]`
- Produces: `lumo.bridge.delete_session(session_id) -> None`
- Produces: `lumo.bridge.search_messages(query) -> list[dict]`
- Produces: `lumo.bridge.update_session_title(session_id, title) -> None`
- Produces: `lumo.bridge.list_plans() -> list[dict]`
- Produces: `lumo.bridge.create_plan(title, goal, daily_minutes, start_date, end_date) -> str`
- Produces: `lumo.bridge.list_notes() -> list[dict]`
- Produces: `lumo.bridge.search_notes(query) -> list[dict]`
- Produces: `lumo.bridge.create_note(title, content, folder_id, source, linked_kp) -> str`
- Produces: `lumo.bridge.update_note(note_id, title, content) -> None`
- Produces: `lumo.bridge.delete_note(note_id) -> None`
- Produces: `lumo.bridge.list_folders() -> list[dict]`
- Produces: `lumo.bridge.create_folder(name, parent_id) -> str`

**设计原则：** bridge.py 是 Kotlin 侧的唯一入口。所有函数接收/返回 JSON 可序列化的 Python 原生类型。不暴露 Senza 或 Store 对象。内部维护 Store 单例。

- [ ] **Step 1: 写失败测试**

Create `python/tests/test_bridge.py`:
```python
"""Tests for lumo.bridge — the Chaquopy entry point."""
import os
import pytest
from lumo.bridge import (
    init, get_store,
    save_provider_config, get_provider_config, test_provider_connection,
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
        init(data_dir)  # Should not raise

    def test_init_called_twice_with_different_dirs(self, tmp_path):
        dir1 = str(tmp_path / "lumo1")
        dir2 = str(tmp_path / "lumo2")
        init(dir1)
        store1 = get_store()
        store1.set_setting("test", "value1")
        init(dir2)
        store2 = get_store()
        assert store2.get_setting("test") is None  # Different DB


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
        sid = create_session("Test Chat")
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
        sid = create_session("Chat")
        assert search_messages("anything") == []


class TestBridgePlans:
    def test_create_and_list_plans(self, tmp_path):
        init(str(tmp_path / "lumo"))
        pid = create_plan("前端基础", "2个月学会前端", 60, "2026-07-22", "2026-09-22")
        plans = list_plans()
        assert len(plans) == 1
        assert plans[0]["title"] == "前端基础"


class TestBridgeNotes:
    def test_create_and_list_notes(self, tmp_path):
        init(str(tmp_path / "lumo"))
        nid = create_note("My Note", "Content")
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
```

注意：`test_provider_connection` 需要真实 API，不在此测试。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd python && python -m pytest tests/test_bridge.py -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'lumo.bridge'`

- [ ] **Step 3: 实现 bridge 模块**

Create `python/lumo/bridge.py`:
```python
"""Chaquopy bridge layer for Lumo.

This is the single entry point for Kotlin code. All functions accept and
return JSON-serializable Python native types (str, int, float, bool, list,
dict, None). Senza and Store objects are never exposed.

Kotlin calls: Python.getInstance().getModule("lumo.bridge").callAttr(...)
"""

from __future__ import annotations

import os
from typing import Optional

from lumo.store import Store
from lumo.config import (
    ProviderConfig,
    get_provider_config as _get_provider_config,
    save_provider_config as _save_provider_config,
    create_provider as _create_provider,
    test_connection as _test_connection,
)

_store: Optional[Store] = None
_data_dir: str = ""


def init(data_dir: str) -> None:
    """Initialize the bridge with a writable data directory.

    Creates the directory and opens/creates the SQLite database.
    Must be called once at app startup before any other bridge function.
    """
    global _store, _data_dir
    _data_dir = data_dir
    os.makedirs(data_dir, exist_ok=True)
    db_path = os.path.join(data_dir, "lumo.db")
    _store = Store(db_path)


def get_store() -> Store:
    """Get the singleton Store instance. Raises if init() not called."""
    if _store is None:
        raise RuntimeError("bridge.init() must be called first")
    return _store


def _ensure_store() -> Store:
    if _store is None:
        raise RuntimeError("bridge.init() must be called first")
    return _store


# ── Provider Config (Module 7) ──

def save_provider_config(
    provider_type: str, api_key: str, base_url: str, model: str,
) -> None:
    """Save LLM provider configuration."""
    config = ProviderConfig(
        provider_type=provider_type,
        api_key=api_key,
        base_url=base_url,
        model=model,
    )
    _save_provider_config(_ensure_store(), config)


def get_provider_config() -> dict | None:
    """Load provider config as dict, or None if not configured."""
    config = _get_provider_config(_ensure_store())
    return config.to_dict() if config else None


def test_provider_connection(
    provider_type: str, api_key: str, base_url: str, model: str,
) -> str:
    """Test LLM connection. Returns a status message."""
    config = ProviderConfig(
        provider_type=provider_type,
        api_key=api_key,
        base_url=base_url,
        model=model,
    )
    success, message = _test_connection(config)
    return f"{'OK' if success else 'FAIL'}: {message}"


# ── Sessions (Module 1) ──

def create_session(title: str = "") -> str:
    return _ensure_store().create_session(title)


def list_sessions() -> list[dict]:
    return _ensure_store().list_sessions()


def get_messages(session_id: str) -> list[dict]:
    return _ensure_store().get_messages(session_id)


def delete_session(session_id: str) -> None:
    _ensure_store().delete_session(session_id)


def update_session_title(session_id: str, title: str) -> None:
    _ensure_store().update_session_title(session_id, title)


def search_messages(query: str) -> list[dict]:
    return _ensure_store().search_messages(query)


# ── Plans (Module 2) ──

def list_plans(status: str = "") -> list[dict]:
    store = _ensure_store()
    return store.list_plans(status if status else None)


def create_plan(
    title: str, goal: str, daily_minutes: int = 60,
    start_date: str = "", end_date: str = "",
) -> str:
    return _ensure_store().create_plan(
        title, goal, daily_minutes, start_date, end_date
    )


def get_plan(plan_id: str) -> dict | None:
    return _ensure_store().get_plan(plan_id)


def update_plan(plan_id: str, **fields) -> None:
    _ensure_store().update_plan(plan_id, **fields)


def delete_plan(plan_id: str) -> None:
    _ensure_store().delete_plan(plan_id)


# ── Tasks (Module 2/3) ──

def get_tasks(plan_id: str, week_num: int = 0) -> list[dict]:
    store = _ensure_store()
    return store.get_tasks(plan_id, week_num if week_num else None)


def update_task(task_id: str, **fields) -> None:
    _ensure_store().update_task(task_id, **fields)


def delete_task(task_id: str) -> None:
    _ensure_store().delete_task(task_id)


# ── Notes (Module 5) ──

def list_notes(folder_id: str = "") -> list[dict]:
    store = _ensure_store()
    return store.list_notes(folder_id if folder_id else None)


def search_notes(query: str) -> list[dict]:
    return _ensure_store().search_notes(query)


def create_note(
    title: str, content: str = "", folder_id: str = "",
    source: str = "manual", linked_kp: str = "",
) -> str:
    return _ensure_store().create_note(
        title, content, folder_id, source, linked_kp
    )


def update_note(note_id: str, title: str = "", content: str = "") -> None:
    fields = {}
    if title:
        fields["title"] = title
    if content:
        fields["content"] = content
    if fields:
        _ensure_store().update_note(note_id, **fields)


def delete_note(note_id: str) -> None:
    _ensure_store().delete_note(note_id)


# ── Folders (Module 5) ──

def list_folders() -> list[dict]:
    return _ensure_store().list_folders()


def create_folder(name: str, parent_id: str = "") -> str:
    return _ensure_store().create_folder(name, parent_id)


# ── Quiz (Module 4) ──

def list_questions(plan_id: str = "") -> list[dict]:
    store = _ensure_store()
    return store.list_questions(plan_id if plan_id else None)


def get_wrong_answers() -> list[dict]:
    return _ensure_store().get_wrong_answers()


def record_answer(
    question_id: str, user_answer: str, is_correct: bool,
    error_reason: str = "",
) -> str:
    return _ensure_store().record_answer(
        question_id, user_answer, is_correct, error_reason
    )


# ── Study & Stats (Module 3/6) ──

def get_total_study_time() -> int:
    return _ensure_store().get_total_study_time()


def get_streak() -> int:
    return _ensure_store().get_streak()


def get_checkin_heatmap(month: str) -> list[dict]:
    return _ensure_store().get_checkin_heatmap(month)


def get_study_sessions(plan_id: str = "") -> list[dict]:
    store = _ensure_store()
    return store.get_study_sessions(plan_id if plan_id else None)


# ── Knowledge Points (Module 2/6) ──

def list_kps(plan_id: str) -> list[dict]:
    return _ensure_store().list_kps(plan_id)
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd python && python -m pytest tests/test_bridge.py -v`
Expected: ALL PASSED

- [ ] **Step 5: 运行全部测试**

Run: `cd python && python -m pytest tests/ -v`
Expected: ALL PASSED

- [ ] **Step 6: Commit**

```bash
git add python/lumo/bridge.py python/tests/test_bridge.py
git commit -m "feat: add Chaquopy bridge layer and model config (modules 7)"
```
