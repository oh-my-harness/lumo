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
| 模型配置 + 一键购买 Token | provider 存储 + 连通性验证 + DeepSeek 充值跳转引导 | lumo.agent + Kotlin |

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

## 七、设计系统（P0 必须到位）

P0 就要把 UI 做漂亮——不是"功能完成后再打磨"，而是 **视觉质量本身就是 PMF 验证的一部分**。用户对一个新 App 的信任感在前 3 秒就决定了。

### 7.1 设计原则

| 原则 | 含义 | 落地 |
|------|------|------|
| **克制** | 留白 > 装饰。每屏只有一个视觉焦点 | 大间距、少边框、不堆砌 |
| **温度感** | 不是冷冰冰的工具，是陪伴你的学习教练 | 暖色调 accent、圆角、柔和阴影 |
| **呼吸感** | 内容不贴边、不挤压 | 24dp 屏幕边距、卡片间距 12dp |
| **动效有意义** | 不为动而动，每个动效传达状态变化 | 番茄钟 → 呼吸；计划生成 → 进度脉冲 |
| **暗色优先** | 深夜学习是核心场景 | 暗色模式为默认，亮色为辅 |

### 7.2 色彩系统

不用 Material3 默认紫色。自定义品牌色：

```
亮色模式：
  primary    = #5B7FFF（信任蓝 — 知识感）
  secondary  = #8B5CF6（记忆紫 — AI 感）
  tertiary   = #FF8A5C（活力橙 — 学习动力感）
  background = #FAFAFB（暖白）
  surface    = #FFFFFF
  onSurface  = #1A1A2E
  outline    = #E5E5EA

暗色模式（默认）：
  primary    = #7B9FFF
  secondary  = #A78BFA
  tertiary   = #FFA873
  background = #0F0F1A（深邃黑蓝 — 深夜不刺眼）
  surface    = #1A1A2E
  surfaceVariant = #252540
  onSurface  = #E4E4F0
  outline    = #353550
```

- `background` 暗色用 #0F0F1A 而非纯黑——减少 OLED 对比度疲劳
- `surface` 比 `background` 亮一档——卡片有层次但不突兀
- accent 三色（蓝/紫/橙）用于不同语义：蓝=操作、紫=AI 生成内容、橙=学习动力/打卡/Streak

### 7.3 字体系统

不用系统默认 Roboto。P0 引入自定义字体 + 明确层级：

| 层级 | 用途 | 字号 | 字重 | 行高 |
|------|------|------|------|------|
| Display | 年度报告大标题 | 32sp | Bold | 40sp |
| Headline | 页面标题 | 24sp | SemiBold | 32sp |
| Title | 卡片标题/任务标题 | 16sp | SemiBold | 24sp |
| Body | 笔记正文/对话内容 | 15sp | Regular | 24sp |
| Label | 标签/按钮 | 13sp | Medium | 18sp |
| Caption | 时间/辅助信息 | 12sp | Regular | 16sp |

- 中文用 **思源黑体 / Noto Sans SC**（开源、免费、覆盖全）
- 英文/数字用 **Inter**（几何无衬线、现代感）
- 代码块用 **JetBrains Mono**
- 字体打包进 APK（不依赖系统字体，保证一致性）

### 7.4 形状与圆角

| 元素 | 圆角 | 说明 |
|------|------|------|
| 卡片 | 16dp | 主内容载体，柔和但不失结构 |
| 按钮 | 12dp | 可点击元素，比卡片小一档 |
| 输入框 | 12dp | 与按钮一致 |
| 标签 chip | 8dp | 小元素，微圆角 |
| 底部 Sheet | 24dp（顶部） | 模态弹出 |
| FAB | 50% | 圆形浮动按钮 |

不用 Material3 的 `Shapes` 默认值（4dp/8dp/16dp），整体圆角偏大——营造柔和、温暖的调性。

### 7.5 间距系统

统一用 4dp 基准网格：

| Token | 值 | 用途 |
|-------|---|------|
| `xs` | 4dp | chip 内间距、图标间距 |
| `sm` | 8dp | 列表项内元素间距 |
| `md` | 12dp | 卡片间距 |
| `lg` | 16dp | 卡片内 padding |
| `xl` | 24dp | 屏幕水平边距、section 间距 |
| `xxl` | 32dp | 页面顶部/底部留白 |

### 7.6 阴影与层次

暗色模式下阴影不可见，改用 `surfaceVariant` 背景色区分层次：

```
Level 0: background  (#0F0F1A) — 页面底
Level 1: surface     (#1A1A2E) — 卡片
Level 2: surfaceVariant (#252540) — 弹出层/选中态
Level 3: elevation + 微弱阴影 — 底部 Sheet / Dialog
```

亮色模式下用传统 elevation 阴影，但阴影颜色用 `primary` 的 10% 透明度（带色调的阴影，比灰色阴影更精致）。

### 7.7 动效规范

| 场景 | 动效 | 时长 | 缓动 |
|------|------|------|------|
| 页面切换 | 淡入淡出 | 200ms | FastOutSlowIn |
| 新任务出现 | 从下方淡入上滑 | 250ms | FastOutSlowIn |
| 番茄钟运行中 | 呼吸缩放 | 2000ms 循环 | FastOutSlowIn |
| 打卡成功 | 缩放弹跳 | 300ms | Overshoot |
| Streak 数字变化 | 数字滚动 | 400ms | FastOutSlowIn |
| AI 流式输出 | 光标闪烁 + 文字淡入 | 50ms/字 | Linear |
| 计划生成进度 | 脉冲呼吸 | 1200ms 循环 | Linear |
| 底部 Sheet 弹出 | 从下滑入 | 250ms | FastOutSlowIn |

用 Compose `AnimatedVisibility` / `animateContentSize` / `Crossfade` 实现，不引入 Lottie（P0 不需要复杂动画）。

### 7.8 关键页面视觉描述

**今日页（首页）**：
- 深色背景，顶部学习进度环形图（primary 色，圆角 16dp 卡片承载）
- 今日任务看板：按 plan 分组的任务卡片，每张卡片左侧有状态色条（待学=灰/进行中=蓝/已完成=绿/待复习=橙）
- 底部固定区：打卡 Streak 火焰图标（tertiary 橙色）+ 今日学习时长
- 番茄钟运行时：页面底部弹出迷你计时条，呼吸动画

**对话页**：
- 深色背景，AI 回复用 surface 色卡片（左对齐），用户消息用 primary 色卡片（右对齐）
- 流式输出时光标闪烁，文字逐字淡入
- 快捷提问按钮横排（讲解一下/举个例子/出几道题/我不理解），圆角 chip
- 底部输入框，圆角 12dp，发送按钮 primary 色

**题库答题页**：
- 深色背景，题目卡片居中，选项纵向排列
- 选中选项高亮 primary 色，判分后正确=绿/错误=红
- AI 解析用 secondary 色卡片区分
- 进度条在顶部，primary 色

**模型配置引导页**：
- 全屏居中布局，大标题"开始使用 Lumo"
- DeepSeek 购买按钮：primary 色实心按钮，带 arrow 图标
- 3 步引导：图标 + 文字纵向排列，步骤间用虚线连接
- API Key 输入框：检测到剪贴板有 `sk-` 开头时弹出"检测到 Key，是否粘贴"

### 7.9 图标

- 用 **Material Symbols Rounded**（圆角风格，匹配整体柔和调性）
- 不用默认 Material Icons（太方正）
- Tab 栏图标选中态填充色，未选中态轮廓线
- 自定义图标：番茄钟用沙漏 + 进度环

### 7.10 实现约束

- 主题定义在 `ui/theme/` 下：`Color.kt` / `Type.kt` / `Shape.kt` / `Theme.kt`（当前 `Theme.kt` 用 Material3 默认紫色，需替换）
- 间距用 `Dimensions.kt` 统一定义 Token，不硬编码 dp 值
- 暗色模式为默认（`isSystemInDarkTheme() = true` fallback）
- 字体打包进 `res/font/`，不依赖系统字体
- Material Symbols 字体打包进 APK

## 八、后台执行策略

- 长任务运行时启动 Foreground Service，保持进程存活
- 通知栏显示任务进度（"正在生成计划..."）
- 任务完成自动停止 Foreground Service
- Senza `WorkflowEngine` 支持 `with_task_store` 状态持久化，异常中断后可从 checkpoint 恢复
