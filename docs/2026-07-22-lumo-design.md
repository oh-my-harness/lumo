# Lumo — 移动端优先的 AI 学习教练

> 设计日期：2026-07-22
> 状态：架构已确认，进入 MVP 实现

## 一、产品定位

跑在手机上的私人 AI 学习导师。数据全在本地，AI 用云端大模型。移动端优先做透，验证 PMF 后再扩展桌面端。

独立新产品，不复用 `llm-tutor` 的 crate。核心逻辑基于 Senza（`llm-harness-runtime` 的 Python SDK）。

## 二、技术架构

### 2.1 核心决策

| 决策点 | 选定方案 |
|---|---|
| 平台 | 先 Android（Chaquopy + Senza），iOS 后续（PyOxidizer 静态编译） |
| UI 层 | Kotlin + Jetpack Compose（原生） |
| 核心逻辑层 | Python（Senza），通过 Chaquopy 嵌入 CPython |
| 底层 runtime | Senza = `llm-harness-runtime` 的 PyO3 Python SDK |
| 与 llm-tutor | 独立新产品，参考实现模式，不复用代码 |
| 数据存储 | SQLite（Python 侧管理，`aiosqlite` 或 `sqlite3`） |
| 流式通信 | Python async generator → Chaquopy callback → Kotlin Flow |
| LLM 调用 | Senza provider 直连云端，API Key 存本地 |
| AI 工作流 | Senza `WorkflowEngine`（计划生成/测验/记忆） |
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
│  ┌──────────────▼──────────────────┐    │
│  │  Chaquopy (CPython 嵌入)        │    │
│  │  Python.getInstance()           │    │
│  └──────────────┬──────────────────┘    │
└─────────────────┼───────────────────────┘
                  │ Python FFI
┌─────────────────▼───────────────────────┐
│         Python Core (Senza)             │
│  ┌─────────────────────────────────┐    │
│  │  lumo.agent                     │    │
│  │  对话/计划/测验/笔记摘要         │    │
│  │  提示词 + provider 配置          │    │
│  └──────┬───────────────┬──────────┘    │
│  ┌──────▼──────┐  ┌─────▼─────────┐    │
│  │ lumo.tools  │  │ lumo.store    │    │
│  │ memory/搜索 │  │ SQLite 存储    │    │
│  └─────────────┘  └───────────────┘    │
│  ┌─────────────────────────────────┐    │
│  │  senza (PyO3 → Rust runtime)    │    │
│  │  AgentHarness / WorkflowEngine  │    │
│  └──────────────┬──────────────────┘    │
└─────────────────┼───────────────────────┘
                  │ HTTP
          ┌───────▼───────┐
          │  云端 LLM API  │
          │ (用户自配 Key) │
          └───────────────┘
```

### 2.3 单进程，无中间服务

- Kotlin UI 和 Python 核心在同一进程内，通过 Chaquopy FFI 直接调用
- Senza provider 直接调云端 LLM API，不经过任何中间服务
- 所有数据存储走 SQLite，由 Python 层统一管理（Kotlin 侧只做 UI 渲染和交互）

### 2.4 iOS 路线

- MVP 阶段只做 Android（Chaquopy）
- iOS 后续通过 PyOxidizer 静态编译 CPython + Senza，或参考 Pyto 方案
- 核心逻辑全用 Python，跨平台复用，只有嵌入方式不同

## 三、项目结构

```
lumo/
├── python/                # Python 核心逻辑
│   ├── lumo/              # 主包
│   │   ├── agent.py       # 对话、计划、测验、笔记摘要（调用 Senza API）
│   │   ├── tools.py       # read_memory / write_memory / search_notes / search_quiz_errors
│   │   ├── store.py       # SQLite 存储层
│   │   ├── workflows.py   # 计划生成/测验 workflow 定义
│   │   └── prompts.py     # 提示词模板
│   └── pyproject.toml
├── android/               # Kotlin + Jetpack Compose + Chaquopy
└── docs/                  # 设计文档
```

### 职责

- **lumo.agent**：对话能力（Senza `AgentHarness`）、workflow 定义（Senza `WorkflowEngine`）、提示词模板、LLM provider 配置。
- **lumo.tools**：Senza tool 实现。P0 含 `read_memory` / `write_memory` / `search_notes` / `search_quiz_errors`。
- **lumo.store**：SQLite schema 管理和迁移、全表 CRUD、对话历史持久化。
- **lumo.workflows**：计划生成（decompose → detail → verify → publish）、测验（generate → verify → publish）workflow 定义。

### 依赖关系

```
android (Kotlin) → Chaquopy → lumo.agent → senza
                                lumo.agent → lumo.tools → lumo.store
                                lumo.agent → lumo.workflows → senza
                                senza → llm-harness-runtime (Rust)
```

## 四、P0→P1→P2 路线图

### P0 — MVP 核心（7 个模块）

| 功能 | AI 能力 | 归属 |
|---|---|---|
| AI 导师对话 | 流式对话、跨会话记忆、搜索笔记/错题 | lumo.agent + lumo.tools |
| 学习计划生成 | 自然语言 → 周大纲 → 逐周细化（WorkflowEngine） | lumo.workflows |
| 每日任务流 + 番茄钟 | 纯本地（任务 CRUD + 计时 + 时长统计） | lumo.store |
| 打卡 + Streak | 纯本地 | lumo.store |
| 智能测验 | 出题、判分、AI 解析（WorkflowEngine） | lumo.workflows |
| 学习笔记 | AI 摘要 + 对话总结为笔记 + 粘贴导入 | lumo.agent + lumo.store |
| 学习统计 | 纯本地（数据聚合查询） | lumo.store |
| 模型配置 | provider 存储 + 连通性验证 | lumo.agent |

### P1 — 重要功能（MVP 后迭代）

| 功能 | AI 能力 | 归属 |
|---|---|---|
| 三层记忆系统 | L1→L2→L3 记忆更新 workflow | lumo.workflows + lumo.tools |
| 智能复习调度 | AI 参与排复习（遗忘曲线 + 正确率） | lumo.workflows |
| 多端同步 | 纯本地（同步引擎） | 新增 lumo.sync 模块 |
| 本地知识库 RAG | 文件导入 + embedding + 检索 | 新增 lumo.rag 模块 |
| 推送提醒 | 纯平台（Android 通知 + WorkManager） | Kotlin 侧 |

### P2 — 进阶功能

| 功能 | AI 能力 | 归属 |
|---|---|---|
| 多导师体系 | Soul 文件 + 记忆隔离 | lumo.agent 扩展 + lumo.store |
| 研究模式 | 搜索 + 整理 + 生成报告 | lumo.workflows + lumo.tools 新增 web_search/web_fetch |
| 学习成就系统 | 纯本地（徽章/等级规则） | lumo.store + Kotlin（分享图片） |
| 专注模式 | 纯平台（通知屏蔽 + 白噪音 + 极简 UI） | Kotlin 侧 |

### 演进总览

| 阶段 | 模块变化 | lumo.workflows 新增 | lumo.tools 新增 |
|---|---|---|---|
| P0 | agent, tools, store, workflows | 计划生成、测验 | read_memory, write_memory, search_notes, search_quiz_errors |
| P1 | +sync, +rag | 记忆更新(L1→L2→L3)、复习调度 | — |
| P2 | — | 多导师、研究模式 | +web_search, +web_fetch |

## 五、数据存储（概要）

全部走 SQLite，由 `lumo.store` 统一管理。P0 涉及的表：

- `sessions` / `messages`（+ FTS5 全文索引）— 对话历史
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
- **ViewModel**：持有 UI 状态，通过 Repository 调用 Python
- **Repository**：封装 Chaquopy 调用，返回 Flow
- **Foreground Service**：长任务（计划生成、测验生成）启动保活，通知栏显示进度

所有业务逻辑和存储在 Python 层，Kotlin 侧只做 UI 渲染和交互。

## 七、后台执行策略

- 长任务运行时启动 Foreground Service，保持进程存活
- 通知栏显示任务进度（"正在生成计划..."）
- 任务完成自动停止 Foreground Service
- Senza `WorkflowEngine` 支持 `with_task_store` 状态持久化，异常中断后可从 checkpoint 恢复
