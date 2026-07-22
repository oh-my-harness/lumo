# Lumo — 移动端优先的 AI 学习教练

> 设计日期：2026-07-22
> 状态：架构已确认，进入 MVP 实现

## 一、产品定位

跑在手机上的私人 AI 学习导师。数据全在本地，AI 用云端大模型。移动端优先做透，验证 PMF 后再扩展桌面端。

独立新产品，不复用 `llm-tutor` 的 crate，参考其实现模式。底层依赖 `llm-harness-runtime`。

## 二、技术架构

### 2.1 核心决策

| 决策点 | 选定方案 |
|---|---|
| 平台 | 先只做 Android |
| UI 层 | Kotlin + Jetpack Compose（原生） |
| 核心逻辑层 | Rust，通过 uniffi 暴露给 Kotlin |
| 底层 runtime | `llm-harness-runtime`（Rust 直连，不用 Senza） |
| 与 llm-tutor | 独立新产品，参考实现模式，不复用 crate |
| 数据存储 | SQLite（全部结构化数据 + 对话历史） |
| 对话持久化 | 自实现 `SessionRepo` 适配 SQLite |
| 流式通信 | uniffi async callback → Kotlin Flow |
| LLM 调用 | Rust 直连云端，API Key 存本地 |
| AI 工作流 | 复用 `WorkflowEngine`（计划生成/测验/记忆） |
| 后台执行 | Foreground Service 保活，通知栏显示进度 |
| Kotlin 架构 | MVVM + Repository，Flow 驱动 |
| 仓库 | `oh-my-harness/lumo`，独立新仓库 |

### 2.2 整体架构图

```
┌─────────────────────────────────────────┐
│            Android App                  │
│  ┌─────────────────────────────────┐    │
│  │  Jetpack Compose UI             │    │
│  │  (今日/对话/题库/笔记/我的)      │    │
│  └──────────────┬──────────────────┘    │
│  ┌──────────────▼──────────────────┐    │
│  │  ViewModel + Repository (MVVM)  │    │
│  └──────────────┬──────────────────┘    │
│  ┌──────────────▼──────────────────┐    │
│  │  Foreground Service (长任务保活) │    │
│  └──────────────┬──────────────────┘    │
└─────────────────┼───────────────────────┘
                  │ uniffi FFI
┌─────────────────▼───────────────────────┐
│         Rust Core (uniffi)              │
│  ┌─────────────────────────────────┐    │
│  │  lumo-runtime                   │    │
│  │  uniffi 绑定 + 流式 callback    │    │
│  └──────────────┬──────────────────┘    │
│  ┌──────────────▼──────────────────┐    │
│  │  lumo-agent                     │    │
│  │  能力路由 + workflow 定义        │    │
│  │  提示词 + LLM provider 配置      │    │
│  └──────┬───────────────┬──────────┘    │
│  ┌──────▼──────┐  ┌─────▼─────────┐    │
│  │ lumo-tools  │  │ lumo-store    │    │
│  │ memory 读写 │  │ SQLite 全量存储│    │
│  └─────────────┘  └───────────────┘    │
└─────────────────┬───────────────────────┘
                  │ HTTP
          ┌───────▼───────┐
          │  云端 LLM API  │
          │ (用户自配 Key) │
          └───────────────┘
```

### 2.3 单进程，无中间服务

- Kotlin UI 和 Rust 核心在同一进程内，通过 uniffi FFI 直接调用，无 HTTP/IPC 开销
- Rust 核心层直接用 `llm-api-adapter` 调云端 API，不经过任何中间服务
- 所有数据存储走 SQLite，由 Rust 层统一管理（Kotlin 侧只做 UI 渲染和交互）

## 三、Crate 划分

```
lumo/
├── Cargo.toml              # workspace
├── crates/
│   ├── lumo-runtime/       # uniffi 绑定 + 流式 callback + 生命周期管理
│   ├── lumo-agent/         # 能力路由 + workflow 定义 + 提示词 + LLM provider
│   ├── lumo-tools/         # read_memory / write_memory（P0 仅此两项）
│   └── lumo-store/         # SQLite 全量存储（对话/计划/任务/题库/笔记/统计/打卡/记忆）
├── android/                # Kotlin + Jetpack Compose
└── docs/                   # 设计文档
```

### 职责

- **lumo-runtime**：唯一的 uniffi 出口。接收 Kotlin 调用，分发给 `lumo-agent` 或 `lumo-store`。将 Rust 异步事件通过 callback 推回 Kotlin。管理 `AgentHarness` / `WorkflowEngine` 生命周期。实现 `SessionRepo` trait 适配 SQLite。
- **lumo-agent**：能力路由（对话/计划/测验/笔记摘要/记忆）。Workflow 定义。提示词模板。LLM provider 配置（多 provider，用户自配 base_url）。
- **lumo-tools**：`Tool` trait 实现。P0 仅 `read_memory` / `write_memory`。
- **lumo-store**：SQLite schema 管理和迁移。全表 CRUD。实现 `SessionRepo` trait。

### 依赖关系

```
lumo-runtime → lumo-agent → lumo-tools → lumo-store
lumo-runtime → lumo-store
lumo-agent   → llm-harness-runtime
lumo-agent   → llm-api-adapter
lumo-runtime → llm-harness-runtime
```

## 四、P0→P1→P2 路线图

### P0 — MVP 核心（7 个模块）

| 功能 | AI 能力 | 归属 |
|---|---|---|
| AI 导师对话 | 流式对话、上下文记忆 | lumo-agent + lumo-tools(memory) |
| 学习计划生成 | 自然语言 → 周计划/日任务（WorkflowEngine） | lumo-agent workflow |
| 每日任务流 + 番茄钟 | 纯本地（任务 CRUD + 计时 + 时长统计） | lumo-store |
| 打卡 + Streak | 纯本地 | lumo-store |
| 智能测验 | 出题、判分、AI 解析（WorkflowEngine） | lumo-agent workflow |
| 学习笔记 | AI 摘要生成 + 笔记 CRUD | lumo-agent + lumo-store |
| 学习统计 | 纯本地（数据聚合查询） | lumo-store |
| 模型配置 | provider 存储 + 连通性验证 | lumo-agent |

**Crate 变化**：runtime, agent, tools, store

### P1 — 重要功能（MVP 后迭代）

| 功能 | AI 能力 | 归属 |
|---|---|---|
| 三层记忆系统 | L1→L2→L3 记忆更新 workflow | lumo-agent workflow + lumo-tools |
| 智能复习调度 | AI 参与排复习（遗忘曲线 + 正确率） | lumo-agent workflow |
| 多端同步 | 纯本地（同步引擎） | 新增 lumo-sync crate |
| 本地知识库 RAG | 文件导入 + embedding + 检索 | 新增 lumo-rag crate |
| 推送提醒 | 纯平台（Android 通知 + WorkManager） | Kotlin 侧 |

**Crate 变化**：+lumo-sync, +lumo-rag

### P2 — 进阶功能

| 功能 | AI 能力 | 归属 |
|---|---|---|
| 多导师体系 | Soul 文件 + 记忆隔离 | lumo-agent 扩展 + lumo-store |
| 研究模式 | 搜索 + 整理 + 生成报告 | lumo-agent 新增 research workflow + lumo-tools 新增 web_search/web_fetch |
| 学习成就系统 | 纯本地（徽章/等级规则） | lumo-store + Kotlin（分享图片） |
| 专注模式 | 纯平台（通知屏蔽 + 白噪音 + 极简 UI） | Kotlin 侧 |

**Crate 变化**：lumo-tools 新增 web_search, web_fetch

### 演进总览

| 阶段 | crate 变化 | lumo-agent 新增 workflow | lumo-tools 新增 |
|---|---|---|---|
| P0 | runtime, agent, tools, store | 对话、计划生成、测验、笔记摘要 | read_memory, write_memory |
| P1 | +sync, +rag | 记忆更新(L1→L2→L3)、复习调度 | — |
| P2 | — | 多导师、研究模式 | +web_search, +web_fetch |

## 五、数据存储（概要）

全部走 SQLite，由 `lumo-store` 统一管理。P0 涉及的表：

- `sessions` / `messages`（+ FTS5 全文索引）— 对话历史，实现 `SessionRepo` trait
- `plans` / `tasks` — 学习计划和每日任务
- `notes` / `folders`（+ FTS5）— 笔记和文件夹
- `quiz_questions` / `quiz_answers` — 题库和答题记录
- `study_sessions` — 学习时长记录
- `checkins` — 打卡记录
- `knowledge_points` — 知识点掌握度
- `settings` — 模型配置和应用设置（key-value）
- `memory` — 记忆系统（P0 简版，P1 扩展 L1/L2/L3）

Schema 细节在实现阶段按需设计，此处只记录数据分类。

## 六、Kotlin 侧架构

MVVM + Repository：

- **UI 层**：Jetpack Compose，5 个 Tab（今日/对话/题库/笔记/我的）
- **ViewModel**：持有 UI 状态，通过 Repository 调用 Rust
- **Repository**：封装 uniffi 调用，返回 Flow
- **Foreground Service**：长任务（计划生成、深度研究）启动保活，通知栏显示进度

所有业务逻辑和存储在 Rust 层，Kotlin 侧只做 UI 渲染和交互。

## 七、后台执行策略

- 长任务运行时启动 Foreground Service，保持进程存活
- 通知栏显示任务进度（"正在生成计划..."）
- 任务完成自动停止 Foreground Service
- WorkflowEngine 支持状态持久化，异常中断后可从 checkpoint 恢复
