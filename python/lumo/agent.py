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

        # Build harness
        self._harness = (
            senza.HarnessBuilder(config.model)
            .provider("*", provider)
            .system_prompt(SYSTEM_PROMPT)
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
