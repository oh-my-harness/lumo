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
