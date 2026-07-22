# Lumo Python Core

Lumo 移动端 AI 学习教练的 Python 核心逻辑层，基于 [Senza](https://github.com/oh-my-harness/Senza)（`llm-harness-runtime` Python SDK）。

## 安装

```bash
pip install -e .
```

## 模块

- `lumo.agent` — 对话、计划、测验、笔记摘要（调用 Senza API）
- `lumo.tools` — read_memory / write_memory / search_notes / search_quiz_errors
- `lumo.store` — SQLite 存储层
- `lumo.workflows` — 计划生成/测验 workflow 定义
- `lumo.prompts` — 提示词模板
