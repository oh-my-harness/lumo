"""Provider config bridge functions (Module 7)."""

from __future__ import annotations

from lumo.config import (
    ProviderConfig,
    get_provider_config as _get_provider_config,
    save_provider_config as _save_provider_config,
    test_connection as _test_connection,
)
from lumo.bridge._serialization import to_json


def save_provider_config(
    provider_type: str, api_key: str, base_url: str, model: str,
) -> None:
    """Save LLM provider configuration."""
    from lumo.bridge import _ensure_store
    config = ProviderConfig(
        provider_type=provider_type,
        api_key=api_key,
        base_url=base_url,
        model=model,
    )
    _save_provider_config(_ensure_store(), config)


def get_provider_config() -> str:
    """Load provider config as JSON string, or "null" if not configured."""
    from lumo.bridge import _ensure_store
    config = _get_provider_config(_ensure_store())
    if config is None:
        return "null"
    return to_json(config.to_dict())


def test_provider_connection(
    provider_type: str, api_key: str, base_url: str, model: str,
) -> str:
    """Test LLM connection. Returns a status message string."""
    config = ProviderConfig(
        provider_type=provider_type,
        api_key=api_key,
        base_url=base_url,
        model=model,
    )
    success, message = _test_connection(config)
    return f"{'✅ ' if success else '❌ '}{message}"
