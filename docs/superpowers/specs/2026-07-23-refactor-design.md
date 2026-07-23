# Lumo 全仓库重构设计

> 日期：2026-07-23
> 状态：已确认，进入实现

## 一、目标

对整个 Lumo 仓库进行系统性代码重构，覆盖三个维度：
1. **可维护性** — 拆分大文件、引入 ViewModel 分层、统一数据模型
2. **类型安全** — 消除 `Map<String,String?>` 和 PyObject 手工转换，引入强类型 data class + JSON 序列化
3. **性能与稳定性** — 优化错误处理、数据库 migration 框架、崩溃防护

## 二、边界

- 纯结构重构 + 修复实现缺陷 + 允许调整 Python bridge API 形状和数据库 schema
- 不新增业务功能，不改 UI 主题/设计系统
- 不改 `agent.py` / `tools.py` / `workflows.py` / `prompts.py`（Senza 业务逻辑层）
- 不引入 Hilt/Dagger DI 或 Room 缓存层

## 三、执行方案

方案 A：分层增量重构，自底向上 6 个阶段，每层独立可验证。

## 四、Python 侧重构

### 4.1 目录结构

```
python/lumo/
├── bridge/
│   ├── __init__.py          # 统一入口，re-export 所有公共函数
│   ├── _serialization.py    # JSON 序列化工具
│   ├── provider.py          # provider config
│   ├── chat.py              # 对话相关
│   ├── plans.py             # 计划/任务 CRUD
│   ├── quiz.py              # 测验生成/判分
│   ├── notes.py             # 笔记 AI + CRUD
│   ├── stats.py             # 统计
│   └── daily.py             # 今日任务/番茄钟/打卡
├── store/
│   ├── __init__.py          # Store 类组合各子 store
│   ├── schema.py            # SCHEMA_SQL + migration 框架
│   ├── sessions.py          # sessions/messages + FTS
│   ├── plans.py             # plans/tasks
│   ├── notes.py             # notes/folders + FTS
│   ├── quiz.py              # quiz_questions/quiz_answers
│   ├── stats.py             # study_sessions/checkins/knowledge_points
│   └── memory.py            # memory + settings
├── agent.py                 # 不变
├── tools.py                 # 不变
├── workflows.py             # 不变
├── config.py                # 微调
└── prompts.py               # 不变
```

### 4.2 序列化契约

所有 bridge 函数返回 JSON 字符串。Python 侧用标准库 `json.dumps`，Kotlin 侧用 `kotlinx.serialization` 反序列化。

### 4.3 Store 拆分

`Store` 类保留为组合入口，内部按表域委托给子 store：

```python
class Store:
    def __init__(self, db_path):
        conn = init_db(db_path)
        self.sessions = SessionStore(conn)
        self.plans = PlanStore(conn)
        self.notes = NoteStore(conn)
        self.quiz = QuizStore(conn)
        self.stats = StatsStore(conn)
        self.memory = MemoryStore(conn)
```

原有 `Store` 的所有方法签名保持不变（向后兼容现有测试），通过 `__getattr__` 或显式委托转发到子 store。

### 4.4 Migration 框架

```python
SCHEMA_VERSION = 2

MIGRATIONS = {
    1: _migrate_v1_to_v2,
}

def init_db(db_path):
    conn = sqlite3.connect(db_path)
    _create_tables(conn)
    _run_migrations(conn)  # PRAGMA user_version
    return conn
```

## 五、Kotlin 侧重构

### 5.1 目录结构

```
android/app/src/main/java/com/lumo/app/
├── data/
│   ├── LumoRepository.kt      # 精简：JSON 反序列化 + bridge 调用
│   ├── dto/                   # 强类型 data class
│   │   ├── ChatDto.kt
│   │   ├── PlanDto.kt
│   │   ├── QuizDto.kt
│   │   ├── NoteDto.kt
│   │   └── StatsDto.kt
│   └── PythonBridge.kt        # 封装 callAttr → toString()
├── ui/
│   ├── today/
│   │   ├── TodayScreen.kt
│   │   └── TodayViewModel.kt
│   ├── chat/
│   │   ├── ChatListScreen.kt
│   │   ├── ChatListViewModel.kt
│   │   ├── ChatDetailScreen.kt
│   │   └── ChatDetailViewModel.kt
│   ├── quiz/
│   │   ├── QuizScreen.kt
│   │   ├── QuizViewModel.kt
│   │   └── QuizCardOverlay.kt
│   ├── notes/
│   │   ├── NotesListScreen.kt
│   │   ├── NotesListViewModel.kt
│   │   ├── NoteEditorScreen.kt
│   │   └── NoteEditorViewModel.kt
│   ├── profile/
│   │   ├── ProfileScreen.kt
│   │   └── ProfileViewModel.kt
│   ├── plans/
│   │   └── CreatePlanScreen.kt
│   └── ...（theme/components/markdown/onboarding 不变）
```

### 5.2 DTO

所有 DTO 用 `@Serializable`，字段名与 Python snake_case 对齐。`kotlinx.serialization` 的 `Json { ignoreUnknownKeys = true }` 确保前向兼容。

### 5.3 Repository 精简

```kotlin
class LumoRepository(private val bridge: PythonBridge) {
    private val json = Json { ignoreUnknownKeys = true }
    fun listSessions(): List<SessionDto> =
        json.decodeFromString(bridge.callString("list_sessions"))
}
```

### 5.4 ViewModel 模式

每个 Screen 配一个 ViewModel，持有 `StateFlow<UiState>`。ViewModel 在 Composable 内用 `viewModel { }` 创建，传入 Repository。不引入 Hilt。

## 六、执行顺序

| 阶段 | 内容 | 验证 |
|---|---|---|
| 1 | Python 模型/序列化基础设施 | pytest |
| 2 | Python store 拆分 + migration | pytest 全部 store 测试 |
| 3 | Python bridge 拆分 + JSON 返回 | pytest 全部 bridge 测试 |
| 4 | Kotlin DTO + Repository | 编译 + 单测 |
| 5 | Kotlin ViewModel 层 | ViewModel 单测 |
| 6 | Kotlin UI 适配 + 拆分大文件 | APK 构建 + 真机回归 |

## 七、测试策略

- Python：更新现有 pytest 测试适配新返回格式（JSON 字符串）
- Kotlin：ViewModel 单测用 fake repository + StateFlow 断言；Repository 单测用 fake JSON 字符串
- 最终：构建 APK + 真机回归全部 P0 功能
