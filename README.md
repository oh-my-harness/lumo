# Lumo

移动端优先的 AI 学习教练。数据全在本地，AI 用云端大模型。

## 架构

```
Android App (Kotlin + Jetpack Compose)
  → uniffi FFI
  → Rust Core (llm-harness-runtime)
      → AI 对话 / 计划生成 / 测验 / 笔记摘要 / 记忆
      → SQLite 本地存储
      → 云端 LLM API（用户自配 Key）
```

## 仓库结构
```
python/lumo/
├── bridge/   # Chaquopy 入口，按业务域拆分（chat/plans/quiz/notes/stats/daily）
├── store/    # SQLite 存储层，按表域拆分 + migration 框架
├── agent.py  # Senza ChatSession + LLM 调用
├── tools.py  # read_memory / write_memory / search_notes / search_quiz_errors
├── workflows.py  # 计划生成 / 测验 workflow 定义
├── config.py # ProviderConfig
└── prompts.py
android/      # Kotlin + Jetpack Compose 前端（MVVM + ViewModel + StateFlow）
docs/         # 设计文档
```

## License

MIT
