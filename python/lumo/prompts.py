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
