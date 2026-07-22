# Lumo P0 — Plan 2: AI 导师对话（模块 1）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现流式 AI 导师对话：Senza AgentHarness + 4 个工具（read_memory/write_memory/search_notes/search_quiz_errors）+ after_turn_hook 持久化 + bridge 层流式 callback。

**Architecture:** `lumo.prompts` 管理提示词，`lumo.tools` 定义 4 个 Senza 工具，`lumo.agent` 构建 HarnessBuilder 并管理流式对话。after_turn_hook 在每轮结束后把消息写入 SQLite。bridge 层暴露 `start_chat` / `send_message` / `stream_chat` 给 Kotlin。

**Tech Stack:** Senza（AgentHarness + create_tool + hooks + events streaming），Python 3.12，pytest

## Global Constraints

- 继承 Plan 1 的全部约束
- Senza AgentHarness 是内存对象，无磁盘持久化（与 WorkflowEngine 不同）
- 跨会话记忆通过 read_memory/write_memory 工具实现，不靠 LLM 上下文恢复
- 消息持久化用 after_turn_hook 写入 SQLite，供 UI 展示和 FTS5 搜索
- 流式：events() 迭代器在后台线程消费，通过 callback 推送到 Kotlin
- 4 个工具的 callback 签名：`def callback(args: dict, ctx: senza.ToolContext) -> dict`
- 工具返回格式：`{"content": [{"type": "text", "text": "..."}], "terminate": False}`
- 测试不依赖真实 LLM API（mock Senza provider），但工具和 hook 逻辑可测

---

## 文件结构

```
python/lumo/
├── prompts.py     # 系统提示词 + 快捷提问模板
├── tools.py       # 4 个 Senza 工具定义
├── agent.py       # HarnessBuilder 构建 + 流式对话管理
├── bridge.py      # 追加 chat bridge 函数（修改已有文件）
└── tests/
    ├── test_prompts.py
    ├── test_tools.py
    └── test_agent.py
```

---

## Task 1: Prompts — 系统提示词 + 快捷提问

**Files:**
- Create: `python/lumo/prompts.py`
- Create: `python/tests/test_prompts.py`

**Interfaces:**
- Produces: `lumo.prompts.SYSTEM_PROMPT` — 导师系统提示词
- Produces: `lumo.prompts.QUICK_PROMPTS` — 快捷提问列表 `list[dict]`
- Produces: `lumo.prompts.get_quick_prompt(key) -> str`

- [ ] **Step 1: 写失败测试**

Create `python/tests/test_prompts.py`:
```python
"""Tests for lumo.prompts."""
from lumo.prompts import SYSTEM_PROMPT, QUICK_PROMPTS, get_quick_prompt


class TestSystemPrompt:
    def test_prompt_is_nonempty(self):
        assert len(SYSTEM_PROMPT) > 100

    def test_prompt_mentions_tutor_role(self):
        assert "导师" in SYSTEM_PROMPT or "tutor" in SYSTEM_PROMPT.lower()

    def test_prompt_mentions_tools(self):
        """System prompt should instruct the AI about available tools."""
        assert "memory" in SYSTEM_PROMPT.lower() or "记忆" in SYSTEM_PROMPT


class TestQuickPrompts:
    def test_quick_prompts_not_empty(self):
        assert len(QUICK_PROMPTS) >= 4

    def test_quick_prompts_have_keys(self):
        for p in QUICK_PROMPTS:
            assert "key" in p
            assert "label" in p
            assert "prompt" in p

    def test_get_quick_prompt(self):
        # Should return the prompt text for a known key
        first = QUICK_PROMPTS[0]
        result = get_quick_prompt(first["key"])
        assert result == first["prompt"]

    def test_get_quick_prompt_unknown(self):
        assert get_quick_prompt("nonexistent") == ""
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd python && .venv/bin/python -m pytest tests/test_prompts.py -v`
Expected: FAIL

- [ ] **Step 3: 实现 prompts 模块**

Create `python/lumo/prompts.py`:
```python
"""Prompt templates for Lumo AI tutor."""

SYSTEM_PROMPT = """你是 Lumo，一位专业的 AI 学习教练。你的职责是帮助学习者高效学习。

## 你的能力

你可以使用以下工具来增强对话体验：

1. **read_memory** — 读取学习者画像记忆。在对话开始时主动调用，了解学习者的学习风格、薄弱点、已学内容。
2. **write_memory** — 更新学习者画像。当你发现学习者的薄弱点、偏好或进展时，记录下来供未来对话使用。
3. **search_notes** — 搜索学习者的笔记。当对话涉及已学内容时，引用笔记帮助巩固。
4. **search_quiz_errors** — 搜索错题本。了解学习者常犯的错误，针对性讲解。

## 对话原则

- 主动调用 read_memory 了解学习者背景
- 发现重要信息时用 write_memory 记录
- 回答时结合学习者的笔记和错题历史
- 用 Markdown 格式回答，支持 LaTeX 公式、代码块、Mermaid 图表
- 保持简洁，避免冗长说教
- 鼓励学习者思考，而非直接给答案
"""

QUICK_PROMPTS = [
    {
        "key": "explain",
        "label": "讲解一下",
        "prompt": "请详细讲解一下这个知识点，举一个简单的例子。",
    },
    {
        "key": "example",
        "label": "举个例子",
        "prompt": "请给我举一个实际的例子来帮助理解。",
    },
    {
        "key": "quiz",
        "label": "出几道题",
        "prompt": "请出 3 道练习题来测试我对这个知识点的理解。",
    },
    {
        "key": "confused",
        "label": "我不理解",
        "prompt": "我不太理解这个概念，请换一种方式解释，尽量用类比。",
    },
]


def get_quick_prompt(key: str) -> str:
    """Return the prompt text for a quick prompt key, or empty string."""
    for p in QUICK_PROMPTS:
        if p["key"] == key:
            return p["prompt"]
    return ""
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd python && .venv/bin/python -m pytest tests/test_prompts.py -v`
Expected: 7 PASSED

- [ ] **Step 5: Commit**

```bash
git add python/lumo/prompts.py python/tests/test_prompts.py
git commit -m "feat: add system prompt and quick prompts for AI tutor"
```

---

## Task 2: Tools — 4 个 Senza 工具

**Files:**
- Create: `python/lumo/tools.py`
- Create: `python/tests/test_tools.py`

**Interfaces:**
- Consumes: `Store` from Plan 1
- Produces: `lumo.tools.create_lumo_tools(store) -> list[senza.Tool]`
- Produces: `lumo.tools.read_memory_callback(args, ctx) -> dict`
- Produces: `lumo.tools.write_memory_callback(args, ctx) -> dict`
- Produces: `lumo.tools.search_notes_callback(args, ctx) -> dict`
- Produces: `lumo.tools.search_quiz_errors_callback(args, ctx) -> dict`

工具签名（JSON Schema）：
- `read_memory`: `{"scope": "global", "key": "learning_style"}` → 返回记忆值
- `write_memory`: `{"scope": "global", "key": "weak_point", "value": "closures"}`
- `search_notes`: `{"query": "Python closures"}` → 返回匹配笔记
- `search_quiz_errors`: `{"query": "closures"}` → 返回相关错题

- [ ] **Step 1: 写失败测试**

Create `python/tests/test_tools.py`:
```python
"""Tests for lumo.tools."""
import json
import pytest
from lumo.store import Store
from lumo.tools import (
    create_lumo_tools,
    read_memory_callback,
    write_memory_callback,
    search_notes_callback,
    search_quiz_errors_callback,
)


class TestReadMemory:
    def test_read_existing_memory(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.write_memory("global", "learning_style", "visual")
        result = read_memory_callback({"scope": "global", "key": "learning_style"}, None)
        assert result["terminate"] is False
        text = result["content"][0]["text"]
        assert "visual" in text

    def test_read_missing_memory(self, tmp_db_path):
        store = Store(tmp_db_path)
        result = read_memory_callback({"scope": "global", "key": "nonexistent"}, None)
        text = result["content"][0]["text"]
        assert "not found" in text.lower() or "无" in text


class TestWriteMemory:
    def test_write_memory(self, tmp_db_path):
        store = Store(tmp_db_path)
        result = write_memory_callback(
            {"scope": "global", "key": "weak_point", "value": "closures"}, None
        )
        assert result["terminate"] is False
        assert store.read_memory("global", "weak_point") == "closures"

    def test_write_memory_overwrites(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.write_memory("global", "style", "visual")
        write_memory_callback(
            {"scope": "global", "key": "style", "value": "auditory"}, None
        )
        assert store.read_memory("global", "style") == "auditory"


class TestSearchNotes:
    def test_search_notes_with_results(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.create_note("Python", "Learn Python closures and decorators")
        result = search_notes_callback({"query": "Python"}, None)
        text = result["content"][0]["text"]
        assert "Python" in text or "python" in text.lower()

    def test_search_notes_no_results(self, tmp_db_path):
        store = Store(tmp_db_path)
        result = search_notes_callback({"query": "nonexistent_xyz"}, None)
        text = result["content"][0]["text"]
        assert "no" in text.lower() or "无" in text


class TestSearchQuizErrors:
    def test_search_quiz_errors_with_results(self, tmp_db_path):
        store = Store(tmp_db_path)
        qid = store.create_question(
            "single_choice", "What is a closure?", '["A","B","C"]', "A",
            knowledge_points='["closures"]',
        )
        store.record_answer(qid, "B", is_correct=False, error_reason="confused closures with decorators")
        result = search_quiz_errors_callback({"query": "closure"}, None)
        text = result["content"][0]["text"]
        assert "closure" in text.lower()

    def test_search_quiz_errors_empty(self, tmp_db_path):
        store = Store(tmp_db_path)
        result = search_quiz_errors_callback({"query": "nothing"}, None)
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
```

注意：工具 callback 需要访问 Store。但 Senza 的 `create_tool(callback)` 签名是 `callback(args, ctx)`，没有 store 参数。解决方案：用闭包捕获 store。测试中直接调用 callback 时传 `None` 作为 ctx。

但上面的测试直接调用 `read_memory_callback` 时没有 store 参数。需要改设计：callback 工厂函数返回闭包。

修正设计：
- `lumo.tools.make_read_memory_tool(store) -> senza.Tool`
- `lumo.tools.make_write_memory_tool(store) -> senza.Tool`
- `lumo.tools.make_search_notes_tool(store) -> senza.Tool`
- `lumo.tools.make_search_quiz_errors_tool(store) -> senza.Tool`
- `lumo.tools.create_lumo_tools(store) -> list[senza.Tool]`

测试改为测试工厂函数返回的 callback。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd python && .venv/bin/python -m pytest tests/test_tools.py -v`
Expected: FAIL

- [ ] **Step 3: 实现 tools 模块**

Create `python/lumo/tools.py`:
```python
"""Senza tool definitions for Lumo AI tutor.

Four tools for P0:
- read_memory: read learner profile from memory store
- write_memory: update learner profile
- search_notes: FTS5 search over notes
- search_quiz_errors: search wrong quiz answers
"""

import json

import senza


def _make_read_memory(store):
    """Create the read_memory tool."""
    def callback(args, ctx):
        scope = args.get("scope", "global")
        key = args.get("key", "")
        value = store.read_memory(scope, key)
        if value is not None:
            text = f"[Memory: {scope}/{key}] {value}"
        else:
            text = f"[Memory: {scope}/{key}] No memory found for this key."
        return {"content": [{"type": "text", "text": text}], "terminate": False}

    tool = senza.create_tool(
        name="read_memory",
        description="Read a memory entry for the learner. Use scope='global' for cross-plan memory, or a plan_id for plan-specific memory.",
        parameters_schema=json.dumps({
            "type": "object",
            "properties": {
                "scope": {"type": "string", "description": "Memory scope: 'global' or a plan_id", "default": "global"},
                "key": {"type": "string", "description": "Memory key to read, e.g. 'learning_style', 'weak_points'"},
            },
            "required": ["key"],
        }),
        callback=callback,
    )
    return tool


def _make_write_memory(store):
    """Create the write_memory tool."""
    def callback(args, ctx):
        scope = args.get("scope", "global")
        key = args.get("key", "")
        value = args.get("value", "")
        store.write_memory(scope, key, value)
        text = f"[Memory saved: {scope}/{key}]"
        return {"content": [{"type": "text", "text": text}], "terminate": False}

    tool = senza.create_tool(
        name="write_memory",
        description="Write or update a memory entry for the learner. Use this to record learning style, weak points, progress, etc.",
        parameters_schema=json.dumps({
            "type": "object",
            "properties": {
                "scope": {"type": "string", "description": "Memory scope: 'global' or a plan_id", "default": "global"},
                "key": {"type": "string", "description": "Memory key, e.g. 'learning_style', 'weak_points'"},
                "value": {"type": "string", "description": "Memory value to store"},
            },
            "required": ["key", "value"],
        }),
        callback=callback,
    )
    return tool


def _make_search_notes(store):
    """Create the search_notes tool."""
    def callback(args, ctx):
        query = args.get("query", "")
        results = store.search_notes(query)
        if results:
            lines = [f"Found {len(results)} notes:"]
            for note in results:
                title = note.get("title", "")
                content = note.get("content", "")[:200]
                lines.append(f"- **{title}**: {content}")
            text = "\n".join(lines)
        else:
            text = "No notes found matching the query."
        return {"content": [{"type": "text", "text": text}], "terminate": False}

    tool = senza.create_tool(
        name="search_notes",
        description="Search the learner's notes by keyword. Returns matching notes with title and content preview.",
        parameters_schema=json.dumps({
            "type": "object",
            "properties": {
                "query": {"type": "string", "description": "Search query"},
            },
            "required": ["query"],
        }),
        callback=callback,
    )
    return tool


def _make_search_quiz_errors(store):
    """Create the search_quiz_errors tool."""
    def callback(args, ctx):
        query = args.get("query", "")
        wrong_answers = store.get_wrong_answers()
        # Filter by query in question or knowledge_points
        filtered = []
        for wa in wrong_answers:
            question = wa.get("question", "").lower()
            kp = wa.get("knowledge_points", "").lower()
            if query.lower() in question or query.lower() in kp:
                filtered.append(wa)
        if filtered:
            lines = [f"Found {len(filtered)} related wrong answers:"]
            for wa in filtered:
                q = wa.get("question", "")
                ua = wa.get("user_answer", "")
                ca = wa.get("correct_answer", "")
                reason = wa.get("error_reason", "")
                lines.append(f"- Q: {q}\n  Your answer: {ua} | Correct: {ca}\n  Reason: {reason}")
            text = "\n".join(lines)
        else:
            text = "No related quiz errors found."
        return {"content": [{"type": "text", "text": text}], "terminate": False}

    tool = senza.create_tool(
        name="search_quiz_errors",
        description="Search the learner's quiz error history by keyword. Returns wrong answers with the correct answer and error reason.",
        parameters_schema=json.dumps({
            "type": "object",
            "properties": {
                "query": {"type": "string", "description": "Search query to filter errors by question or knowledge point"},
            },
            "required": ["query"],
        }),
        callback=callback,
    )
    return tool


def create_lumo_tools(store) -> list:
    """Create all 4 P0 tools for the Lumo tutor."""
    return [
        _make_read_memory(store),
        _make_write_memory(store),
        _make_search_notes(store),
        _make_search_quiz_errors(store),
    ]
```

- [ ] **Step 4: 修正测试以匹配工厂函数设计**

Rewrite `python/tests/test_tools.py`:
```python
"""Tests for lumo.tools."""
import json
import pytest
from lumo.store import Store
from lumo.tools import (
    create_lumo_tools,
    _make_read_memory,
    _make_write_memory,
    _make_search_notes,
    _make_search_quiz_errors,
)


def _call_tool(tool, args):
    """Helper: extract the callback from a tool and call it.

    Senza Tool objects store the callback internally. For testing,
    we call the original callback function directly.
    """
    # The callback is stored in the tool's internal _callback attribute
    # by create_tool. We access it for testing.
    # If Senza doesn't expose it, we test the factory functions directly.
    return tool._callback(args, None) if hasattr(tool, "_callback") else None


class TestReadMemory:
    def test_read_existing_memory(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.write_memory("global", "learning_style", "visual")
        tool = _make_read_memory(store)
        # Senza Tool stores callback; we call it via the tool's internal attribute
        result = tool._callback({"scope": "global", "key": "learning_style"}, None)
        assert result["terminate"] is False
        assert "visual" in result["content"][0]["text"]

    def test_read_missing_memory(self, tmp_db_path):
        store = Store(tmp_db_path)
        tool = _make_read_memory(store)
        result = tool._callback({"scope": "global", "key": "nonexistent"}, None)
        assert "not found" in result["content"][0]["text"].lower() or "无" in result["content"][0]["text"]


class TestWriteMemory:
    def test_write_memory(self, tmp_db_path):
        store = Store(tmp_db_path)
        tool = _make_write_memory(store)
        result = tool._callback(
            {"scope": "global", "key": "weak_point", "value": "closures"}, None
        )
        assert result["terminate"] is False
        assert store.read_memory("global", "weak_point") == "closures"

    def test_write_memory_overwrites(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.write_memory("global", "style", "visual")
        tool = _make_write_memory(store)
        tool._callback({"scope": "global", "key": "style", "value": "auditory"}, None)
        assert store.read_memory("global", "style") == "auditory"


class TestSearchNotes:
    def test_search_notes_with_results(self, tmp_db_path):
        store = Store(tmp_db_path)
        store.create_note("Python", "Learn Python closures and decorators")
        tool = _make_search_notes(store)
        result = tool._callback({"query": "Python"}, None)
        assert "Python" in result["content"][0]["text"]

    def test_search_notes_no_results(self, tmp_db_path):
        store = Store(tmp_db_path)
        tool = _make_search_notes(store)
        result = tool._callback({"query": "nonexistent_xyz"}, None)
        assert "no" in result["content"][0]["text"].lower() or "无" in result["content"][0]["text"]


class TestSearchQuizErrors:
    def test_search_quiz_errors_with_results(self, tmp_db_path):
        store = Store(tmp_db_path)
        qid = store.create_question(
            "single_choice", "What is a closure?", '["A","B","C"]', "A",
            knowledge_points='["closures"]',
        )
        store.record_answer(qid, "B", is_correct=False,
                           error_reason="confused closures with decorators")
        tool = _make_search_quiz_errors(store)
        result = tool._callback({"query": "closure"}, None)
        assert "closure" in result["content"][0]["text"].lower()

    def test_search_quiz_errors_empty(self, tmp_db_path):
        store = Store(tmp_db_path)
        tool = _make_search_quiz_errors(store)
        result = tool._callback({"query": "nothing"}, None)
        assert "no" in result["content"][0]["text"].lower() or "无" in result["content"][0]["text"]


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
```

注意：测试通过 `tool._callback` 访问 Senza Tool 内部属性。如果 Senza 不暴露此属性，需要改为测试工厂函数返回的闭包。备选方案是把 callback 函数也导出：

```python
# 在 tools.py 中也导出裸 callback 工厂
def make_read_memory_callback(store):
    def callback(args, ctx):
        ...
    return callback
```

- [ ] **Step 5: 运行测试确认通过**

Run: `cd python && .venv/bin/python -m pytest tests/test_tools.py -v`
Expected: ALL PASSED

如果 `_callback` 属性不存在，改为导出 callback 工厂函数并直接测试。

- [ ] **Step 6: Commit**

```bash
git add python/lumo/tools.py python/tests/test_tools.py
git commit -m "feat: add 4 Senza tools for AI tutor (read/write memory, search notes/quiz errors)"
```

---

## Task 3: Agent — HarnessBuilder + 流式对话 + after_turn 持久化

**Files:**
- Create: `python/lumo/agent.py`
- Create: `python/tests/test_agent.py`

**Interfaces:**
- Consumes: `Store`, `ProviderConfig`, `create_lumo_tools`, `SYSTEM_PROMPT`
- Produces: `lumo.agent.ChatSession` — 管理单个对话会话的 harness
- Produces: `lumo.agent.ChatSession.__init__(store, config, session_id)`
- Produces: `lumo.agent.ChatSession.send_message(text) -> str` — 非流式，返回完整回复
- Produces: `lumo.agent.ChatSession.stream_message(text, on_token) -> str` — 流式，callback 推送 token
- Produces: `lumo.agent.ChatSession.get_history() -> list[dict]`

**设计：**
- 每个 ChatSession 持有一个 AgentHarness（带 4 个工具 + after_turn_hook）
- after_turn_hook 在每轮结束后把 user/assistant 消息写入 SQLite
- stream_message 用后台线程消费 events() 迭代器
- auto_compact 开启，处理长对话

- [ ] **Step 1: 写失败测试**

Create `python/tests/test_agent.py`:
```python
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

    def test_chat_session_has_history(self, tmp_db_path):
        store = Store(tmp_db_path)
        config = ProviderConfig("openai", "sk-test", "", "gpt-4o")
        sid = store.create_session("Test")
        session = ChatSession(store, config, sid)
        assert session.get_history() == []
```

注意：这些测试只验证构造，不调用 LLM。`send_message` 和 `stream_message` 需要真实 API，不在单元测试中覆盖。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd python && .venv/bin/python -m pytest tests/test_agent.py -v`
Expected: FAIL

- [ ] **Step 3: 实现 agent 模块**

Create `python/lumo/agent.py`:
```python
"""AI tutor agent for Lumo.

Manages streaming chat sessions using Senza AgentHarness.
Each ChatSession owns a harness with 4 tools and after_turn_hook
for message persistence.
"""

import threading

import senza

from lumo.config import ProviderConfig, create_provider
from lumo.prompts import SYSTEM_PROMPT
from lumo.tools import create_lumo_tools


class ChatSession:
    """Manages a single conversation session.

    Each session owns a Senza AgentHarness with:
    - 4 tools (read_memory, write_memory, search_notes, search_quiz_errors)
    - after_turn_hook for SQLite persistence
    - auto_compact for long conversations
    """

    def __init__(self, store, config: ProviderConfig, session_id: str):
        self._store = store
        self._config = config
        self._session_id = session_id

        # Create provider
        provider = create_provider(config)

        # Create tools
        tools = create_lumo_tools(store)

        # after_turn_hook: persist messages to SQLite
        def persist_messages(ctx):
            new_messages = ctx.get("new_messages", [])
            for msg in new_messages:
                role = msg.get("role", "user")
                content = ""
                if isinstance(msg.get("content"), str):
                    content = msg["content"]
                elif isinstance(msg.get("content"), list):
                    for block in msg["content"]:
                        if isinstance(block, dict) and block.get("type") == "text":
                            content += block.get("text", "")
                if content:
                    self._store.add_message(self._session_id, role, content)

        after_turn = senza.create_after_turn_hook(persist_messages)

        # Build harness
        self._harness = (
            senza.HarnessBuilder(config.model)
            .provider("*", provider)
            .system_prompt(SYSTEM_PROMPT)
            .max_tokens(2048)
            .auto_compact(True)
            .tools(tools)
            .hooks([after_turn])
            .retry(3, 1000)
            .stream_options(timeout_ms=30000)
            .build()
        )

    def send_message(self, text: str) -> str:
        """Send a message and return the complete response (non-streaming)."""
        events = self._harness.prompt_and_collect(text, timeout_ms=60000)
        result = ""
        for event in events:
            if event["type"] == "text_delta":
                result += event.get("text", "")
        return result

    def stream_message(self, text: str, on_token=None) -> str:
        """Send a message with streaming. Calls on_token(text) for each delta.

        Returns the complete response text.
        """
        result = [""]
        done = threading.Event()

        def stream_events():
            for event in self._harness.events(timeout_ms=30000):
                t = event["type"]
                if t == "text_delta":
                    token = event.get("text", "")
                    result[0] += token
                    if on_token:
                        on_token(token)
                elif t in ("settled", "aborted", "error"):
                    done.set()
                    break

        stream_thread = threading.Thread(target=stream_events)
        stream_thread.start()

        self._harness.prompt(text)
        stream_thread.join(timeout=120)

        return result[0]

    def get_history(self) -> list[dict]:
        """Return persisted messages from SQLite for this session."""
        return self._store.get_messages(self._session_id)

    def usage(self) -> dict:
        """Return token usage for this session."""
        return self._harness.usage()
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd python && .venv/bin/python -m pytest tests/test_agent.py -v`
Expected: 2 PASSED

- [ ] **Step 5: Commit**

```bash
git add python/lumo/agent.py python/tests/test_agent.py
git commit -m "feat: add ChatSession with streaming, tools, and after_turn persistence"
```

---

## Task 4: Bridge — 追加 chat bridge 函数

**Files:**
- Modify: `python/lumo/bridge.py`
- Modify: `python/tests/test_bridge.py`

**Interfaces:**
- Produces: `lumo.bridge.start_chat(session_id) -> None` — 创建 ChatSession 单例
- Produces: `lumo.bridge.send_message(text) -> str` — 非流式发送
- Produces: `lumo.bridge.stream_chat(text, callback) -> str` — 流式发送
- Produces: `lumo.bridge.get_chat_history() -> list[dict]`
- Produces: `lumo.bridge.get_quick_prompts() -> list[dict]`
- Produces: `lumo.bridge.abort_chat() -> None`

- [ ] **Step 1: 写失败测试**

Append to `python/tests/test_bridge.py`:
```python
from lumo.bridge import (
    start_chat, send_message, stream_chat,
    get_chat_history, get_quick_prompts, abort_chat,
)


class TestBridgeChat:
    def test_get_quick_prompts(self, tmp_path):
        init(str(tmp_path / "lumo"))
        prompts = get_quick_prompts()
        assert len(prompts) >= 4
        assert "key" in prompts[0]
        assert "label" in prompts[0]

    def test_get_chat_history_empty(self, tmp_path):
        init(str(tmp_path / "lumo"))
        sid = create_session("Chat")
        start_chat(sid)
        assert get_chat_history() == []
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd python && .venv/bin/python -m pytest tests/test_bridge.py::TestBridgeChat -v`
Expected: FAIL

- [ ] **Step 3: 追加 chat bridge 函数**

Append to `python/lumo/bridge.py`:
```python
# ── Chat (Module 1) ──

from lumo.prompts import QUICK_PROMPTS

_chat_session = None


def start_chat(session_id: str) -> None:
    """Start a chat session. Creates a ChatSession with the current provider config."""
    global _chat_session
    from lumo.agent import ChatSession
    from lumo.config import get_provider_config, ProviderConfig

    store = _ensure_store()
    config = get_provider_config(store)
    if config is None:
        raise RuntimeError("Provider not configured. Call save_provider_config first.")
    _chat_session = ChatSession(store, config, session_id)


def send_message(text: str) -> str:
    """Send a message and return the complete response."""
    if _chat_session is None:
        raise RuntimeError("Chat not started. Call start_chat first.")
    return _chat_session.send_message(text)


def stream_chat(text: str, callback) -> str:
    """Stream a chat response. callback.onToken(text) is called for each token."""
    if _chat_session is None:
        raise RuntimeError("Chat not started. Call start_chat first.")
    return _chat_session.stream_message(text, on_token=callback.onToken)


def get_chat_history() -> list[dict]:
    """Return persisted chat messages for the current session."""
    if _chat_session is None:
        raise RuntimeError("Chat not started. Call start_chat first.")
    return _chat_session.get_history()


def get_quick_prompts() -> list[dict]:
    """Return quick prompt buttons for the chat UI."""
    return QUICK_PROMPTS


def abort_chat() -> None:
    """Abort the current streaming response."""
    if _chat_session is not None:
        _chat_session._harness.abort()
```

- [ ] **Step 4: 运行全部测试**

Run: `cd python && .venv/bin/python -m pytest tests/ -v`
Expected: ALL PASSED

- [ ] **Step 5: Commit**

```bash
git add python/lumo/bridge.py python/tests/test_bridge.py
git commit -m "feat: add chat bridge functions (start_chat, send_message, stream_chat)"
```
