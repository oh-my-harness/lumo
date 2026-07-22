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
crates/
├── lumo-runtime/   # uniffi 绑定 + 流式 callback + 生命周期管理
├── lumo-agent/     # 能力路由 + workflow 定义 + 提示词 + LLM provider
├── lumo-tools/     # read_memory / write_memory
└── lumo-store/     # SQLite 全量存储
android/            # Kotlin + Jetpack Compose 前端
docs/               # 设计文档
```

## License

MIT
