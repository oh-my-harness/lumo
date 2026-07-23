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
        if text.strip():
            return True, f"连接成功，模型 {config.model} 已回复"
        else:
            return True, f"连接成功，但模型未返回内容（可能需要检查模型名称）"
    except Exception as e:
        msg = str(e)
        if "401" in msg or "Unauthorized" in msg:
            return False, "API Key 无效，请检查"
        elif "404" in msg or "Not Found" in msg:
            return False, f"模型 {config.model} 不存在，请检查模型名称"
        elif "timeout" in msg.lower() or "timed out" in msg.lower():
            return False, "连接超时，请检查网络或 Base URL"
        elif "Connection" in msg or "connect" in msg.lower():
            return False, f"无法连接到 {config.base_url}，请检查地址"
        return False, f"连接失败: {msg[:100]}"
