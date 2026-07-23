"""AI tutor agent for Lumo.

Manages streaming chat sessions using Senza AgentHarness.
Each ChatSession owns a harness with 4 tools and after_turn_hook
for message persistence to SQLite.
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
    - retry for API resilience
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

        # Build system prompt with injected learner memory
        system_prompt = SYSTEM_PROMPT
        try:
            mem = store.list_memory("global")
            if mem:
                mem_lines = "\n".join(
                    f"- {m['key']}: {m['value']}" for m in mem
                )
                system_prompt += (
                    "\n\n## 学习者画像（已从记忆加载）\n"
                    f"{mem_lines}\n\n"
                    "请基于以上信息个性化你的回答。"
                )
        except Exception:
            pass

        # Build harness
        self._harness = (
            senza.HarnessBuilder(config.model)
            .provider("*", provider)
            .system_prompt(system_prompt)
            .max_tokens(2048)
            .auto_compact(True)
            .tool(tools[0])
            .tool(tools[1])
            .tool(tools[2])
            .tool(tools[3])
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

    def abort(self) -> None:
        """Abort the current streaming response."""
        self._harness.abort()


def grade_short_answer(store, config: ProviderConfig, question_id: str, user_answer: str) -> dict:
    """Use LLM to grade a short-answer question.

    Returns dict with:
    - is_correct: bool
    - explanation: str
    """
    question = store.get_question(question_id)
    if not question:
        return {"is_correct": False, "explanation": "Question not found"}

    correct_answer = question.get("answer", "")
    explanation = question.get("explanation", "")

    provider = create_provider(config)
    harness = (
        senza.HarnessBuilder(config.model)
        .provider("*", provider)
        .system_prompt(
            "你是阅卷老师。判断学生的简答题回答是否正确。"
            "正确答案可能不要求完全一致，只要意思对即可。"
            '回复 JSON: {"correct": true/false, "feedback": "..."}'
        )
        .max_tokens(256)
        .build()
    )

    prompt = (
        f"问题：{question['question']}\n"
        f"正确答案：{correct_answer}\n"
        f"学生回答：{user_answer}\n"
        f"参考解析：{explanation}\n\n"
        "请判断学生回答是否正确，给出反馈。"
    )

    events = harness.prompt_and_collect(prompt, timeout_ms=30000)
    text = ""
    for event in events:
        if event["type"] == "text_delta":
            text += event.get("text", "")

    try:
        from lumo.workflows import _extract_json
        parsed = _extract_json(text)
        return {
            "is_correct": parsed.get("correct", False),
            "explanation": parsed.get("feedback", ""),
        }
    except Exception:
        return {"is_correct": False, "explanation": text[:200]}


def summarize_note(store, config: ProviderConfig, note_id: str) -> str:
    """Use LLM to generate a knowledge point summary for a note.

    Returns the summary text.
    """
    note = store.get_note(note_id)
    if not note:
        return "Note not found"

    content = note.get("content", "")
    title = note.get("title", "")

    provider = create_provider(config)
    harness = (
        senza.HarnessBuilder(config.model)
        .provider("*", provider)
        .system_prompt(
            "你是学习笔记助手。为用户的笔记生成简洁的知识点摘要。"
        )
        .max_tokens(512)
        .build()
    )

    prompt = f"请为以下笔记生成知识点摘要：\n\n标题：{title}\n内容：{content}"

    events = harness.prompt_and_collect(prompt, timeout_ms=30000)
    text = ""
    for event in events:
        if event["type"] == "text_delta":
            text += event.get("text", "")

    return text


def summarize_conversation(store, config: ProviderConfig, session_id: str) -> str:
    """Use LLM to summarize a conversation into a note.

    Returns the summary text suitable for saving as a note.
    """
    messages = store.get_messages(session_id)
    if not messages:
        return "No messages to summarize"

    conversation = "\n".join(
        f"{m['role']}: {m['content']}" for m in messages
    )

    provider = create_provider(config)
    harness = (
        senza.HarnessBuilder(config.model)
        .provider("*", provider)
        .system_prompt(
            "你是学习助手。将对话总结为一份学习笔记，用 Markdown 格式。"
        )
        .max_tokens(1024)
        .build()
    )

    prompt = f"请将以下对话总结为学习笔记：\n\n{conversation}"

    events = harness.prompt_and_collect(prompt, timeout_ms=30000)
    text = ""
    for event in events:
        if event["type"] == "text_delta":
            text += event.get("text", "")

    return text

def summarize_notes(store, config: ProviderConfig, note_ids: list[str]) -> str:
    """Use LLM to summarize multiple notes into one consolidated note.

    Returns the summary text suitable for saving as a note.
    """
    notes = []
    for nid in note_ids:
        note = store.get_note(nid)
        if note:
            notes.append(f"### {note['title']}\n\n{note.get('content', '')}")

    if not notes:
        return "没有可汇总的笔记"

    combined = "\n\n---\n\n".join(notes)

    provider = create_provider(config)
    harness = (
        senza.HarnessBuilder(config.model)
        .provider("*", provider)
        .system_prompt(
            "你是学习助手。将多份笔记归纳汇总为一份结构清晰的学习笔记。"
 "保留重要知识点，去除重复内容，按主题分类整理。用 Markdown 格式输出。"
        )
        .max_tokens(2048)
        .build()
    )

    prompt = f"请将以下 {len(notes)} 份笔记归纳汇总为一份学习笔记：\n\n{combined}"

    events = harness.prompt_and_collect(prompt, timeout_ms=60000)
    text = ""
    for event in events:
        if event["type"] == "text_delta":
            text += event.get("text", "")

    return text
