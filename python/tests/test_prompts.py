"""Tests for lumo.prompts."""
from lumo.prompts import SYSTEM_PROMPT, QUICK_PROMPTS, get_quick_prompt


class TestSystemPrompt:
    def test_prompt_is_nonempty(self):
        assert len(SYSTEM_PROMPT) > 100

    def test_prompt_mentions_tutor_role(self):
        assert "教练" in SYSTEM_PROMPT or "tutor" in SYSTEM_PROMPT.lower()

    def test_prompt_mentions_tools(self):
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
        first = QUICK_PROMPTS[0]
        result = get_quick_prompt(first["key"])
        assert result == first["prompt"]

    def test_get_quick_prompt_unknown(self):
        assert get_quick_prompt("nonexistent") == ""
