# Lumo 项目指南

## 构建命令

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_SDK_ROOT="/Users/hhl/Library/Android/sdk"
cd android && ./gradlew :app:assembleDebug --no-daemon 2>&1 | tail -10
```

APK 产物路径：`android/app/build/outputs/apk/debug/app-debug.apk`

## Senza Wheels

`android/senza-wheel/` 需要 arm64-v8a 和 x86_64 两个 wheel（gitignore）。下载：

```bash
gh release download --repo oh-my-harness/Senza v1.0.2 \
  --pattern 'senza_sdk-*-android_*.whl' \
  --dir android/senza-wheel --clobber
```

`build.gradle.kts` 中用 `install("senza-sdk")` + `--find-links` 按包名安装，不要硬编码 wheel 文件路径。

## 真机

- 设备 ID：`dc8a6246`
- 安装：`adb -s dc8a6246 install -r app/build/outputs/apk/debug/app-debug.apk`
- 启动：`adb -s dc8a6246 shell am start -n com.lumo.app/.MainActivity`
- 日志：`adb -s dc8a6246 logcat -d | grep -E "LumoQuiz|AndroidRuntime|Traceback"`

## 内测发布流程

1. 确认在 `main` 分支，最新代码已推送
2. 构建 APK（见上方构建命令）
3. 创建 GitHub Release（替换版本号和 notes）：

```bash
gh release create v0.1.0 \
  --repo oh-my-harness/lumo \
  --title "Lumo v0.1.0 内测版" \
  --notes "见下方模板" \
  android/app/build/outputs/apk/debug/app-debug.apk
```

4. Release notes 中包含国内加速下载链接（ghfast.top 镜像）：

```
### 下载链接
- 🚀 国内加速：https://ghfast.top/https://github.com/oh-my-harness/lumo/releases/download/v0.1.0/app-debug.apk
- 🐢 GitHub 直连：下方 Assets 中的 app-debug.apk
```

5. 把加速链接发给同事，手机浏览器直接打开下载安装

### 注意事项

- APK 是 debug 签名，安装时需允许「未知来源」
- 仅包含 arm64-v8a 和 x86_64 两个 ABI
- ghfast.top 镜像可能不稳定，备选：`gh-proxy.com`
- 更新 Release notes：`gh release edit v0.1.0 --repo oh-my-harness/lumo --notes "..."`
- Tag 不一定要在最新 commit 上——APK 是构建产物，同事下载的是文件不是代码

## Chaquopy 类型契约

**重构后（2026-07-23）**：所有 bridge 函数返回 JSON 字符串，Kotlin 侧用 `kotlinx.serialization` 反序列化为 data class。不再有 PyObject 手工转换或类型陷阱。

- Python `bridge/` 包是 Chaquopy 入口（`py.getModule("lumo.bridge")`）
- 所有 bridge 函数返回 `str`（JSON）或 `int`，不再返回 `dict`/`list`
- Kotlin `LumoRepository` 用 `Json { ignoreUnknownKeys = true }` 反序列化
- DTO 定义在 `com.lumo.app.data.dto.*`，字段名与 Python `snake_case` 对齐

## Python 层

```
python/lumo/
├── bridge/          # Chaquopy 入口，按业务域拆分
│   ├── __init__.py  # 统一 re-export
│   ├── _serialization.py  # JSON 序列化工具
│   ├── provider.py / chat.py / plans.py / quiz.py / notes.py / stats.py / daily.py
├── store/           # SQLite 存储层，按表域拆分
│   ├── __init__.py  # Store facade，委托给子 store
│   ├── schema.py    # SCHEMA_SQL + migration 框架（PRAGMA user_version）
│   ├── sessions.py / plans.py / notes.py / quiz.py / stats.py / memory.py
├── agent.py         # Senza ChatSession + LLM 调用
├── tools.py         # 4 个 Senza tool
├── workflows.py     # 计划/测验 workflow 定义
├── config.py        # ProviderConfig
└── prompts.py       # 提示词模板
```

修改后重新 `./gradlew assembleDebug` 即生效（Chaquopy `srcDir` 直接打包源码）。

## Kotlin 层

```
android/app/src/main/java/com/lumo/app/
├── data/
│   ├── LumoRepository.kt   # JSON 反序列化 + bridge 调用
│   ├── PythonBridge.kt     # 封装 callAttr → toString()
│   └── dto/                # 强类型 data class（@Serializable）
├── ui/
│   ├── today/    TodayScreen + TodayViewModel
│   ├── chat/     ChatListScreen + ChatListViewModel, ChatDetailScreen + ChatDetailViewModel
│   ├── quiz/     QuizScreen + QuizViewModel, QuizCardOverlay
│   ├── notes/    NotesListScreen + NotesListViewModel, NoteEditorScreen + NoteEditorViewModel
│   ├── profile/  ProfileScreen + ProfileViewModel
│   ├── plans/    CreatePlanScreen + CreatePlanViewModel
│   ├── theme/ / components/ / markdown/ / onboarding/  # 不变
```

每个 Screen 配一个 ViewModel，持有 `StateFlow<UiState>`。ViewModel 在 Composable 内用 `viewModel { XViewModel(LumoRepository.get()) }` 创建。

## 主题

- 主色：浅青 `#58B7B1`
- 辅色：浅紫 `#9688E8`
- 日间底：米白 `#F8F9FA` / 夜间底：深灰 `#1E2129`
- Markdown 渲染字体：13px（WebView 在高 DPI 屏幕会放大 CSS px）
