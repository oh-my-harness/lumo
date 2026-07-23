# Lumo 开发者手册

本手册面向新加入的开发者，覆盖从零搭建环境到本地构建运行 Lumo Android 应用的全流程。如果你已有 Claude Code / Cursor 等 AI 编程助手，可以直接把手扔给它——把本手册喂给 AI，让它帮你完成 Android Studio 安装、SDK/NDK 配置、环境变量设置、模拟器创建等全部步骤，你只需在需要授权/输入密码时介入即可。

---

## 目录

- [技术栈概览](#技术栈概览)
- [仓库结构](#仓库结构)
- [环境准备](#环境准备)
  - [1. 安装 Android Studio](#1-安装-android-studio)
  - [2. 配置 Android SDK 与 NDK](#2-配置-android-sdk-与-ndk)
  - [3. 安装 Python 3](#3-安装-python-3)
  - [4. 安装 Rust（可选，重建 Senza wheel 时需要）](#4-安装-rust可选重建-senza-wheel-时需要)
- [Python 核心开发](#python-核心开发)
- [测试](#测试)
  - [Python 单元测试](#python-单元测试)
  - [Android 插桩测试（Instrumented Test）](#android-插桩测试instrumented-test)
  - [在模拟器上测试](#在模拟器上测试)
  - [在真机上测试](#在真机上测试)
  - [测试覆盖范围](#测试覆盖范围)
- [环境变量](#环境变量)
- [重建 Senza Android Wheel（可选）](#重建-senza-android-wheel可选)
- [常见问题排查](#常见问题排查)

---

## 技术栈概览

| 层 | 技术 | 说明 |
|---|---|---|
| UI | Kotlin + Jetpack Compose | Android 前端，namespace `com.lumo.app` |
| 桥接 | [Chaquopy](https://chaquo.com/chaquopy/) 17.0.0 | 在 Android 进程内嵌入 CPython 3.12 解释器 |
| 核心逻辑 | Python 3.9+（abi3 wheel） | `python/lumo/` 下的 agent / store / workflows / tools / prompts |
| LLM 运行时 | [Senza](https://github.com/oh-my-harness/Senza)（PyO3 → Rust `llm-harness-runtime`） | 预编译为 `arm64-v8a` + `x86_64` Android wheel，从 GitHub Releases 下载，随 APK 分发 |
| 存储 | SQLite | 本地全量存储 |
| 构建 | Gradle 8.9 + AGP 8.5.0 + Kotlin 2.0.0 | 通过 `gradlew` 包装器调用 |

关键约束：

- **ABI 支持**：`arm64-v8a`（真机 + Apple Silicon Mac 模拟器）+ `x86_64`（Intel/AMD PC 模拟器，含 Windows）。两个 wheel 均从 Senza GitHub Releases 下载，不进仓库。
- **Python 版本**：Chaquopy 运行 CPython 3.12，Senza wheel 使用 `abi3-py39`，二者兼容。
- **Java**：JDK 17（Android Studio 自带，无需单独安装）。

---

## 仓库结构

```
lumo/
├── android/                     # Android 工程（Kotlin + Compose + Chaquopy）
│   ├── app/
│   │   ├── build.gradle.kts     # app 模块配置：Compose、Chaquopy、依赖、BuildConfig
│   │   └── src/main/
│   │       ├── java/com/lumo/app/   # Kotlin 源码（MainActivity / ui / data）
│   │       ├── python/              # Android 专用 Python 入口（lumo_spike.py 等）
│   │       └── AndroidManifest.xml
│   ├── senza-wheel/             # Senza Android wheels（gitignore，用脚本下载）
│   ├── build.gradle.kts         # 根工程：插件版本声明
│   ├── settings.gradle.kts
│   ├── gradle/wrapper/          # Gradle 8.9 包装器
│   ├── gradlew / gradlew.bat
│   └── local.properties         # 本地 SDK 路径（不入版本控制，自动生成）
├── python/                      # Python 核心逻辑（被 Chaquopy srcDir 引入 APK）
│   ├── lumo/
│   │   ├── agent.py             # 对话、计划、测验、笔记摘要（调用 Senza）
│   │   ├── bridge.py            # Kotlin↔Python 桥接层
│   │   ├── store.py             # SQLite 存储层
│   │   ├── workflows.py         # 计划生成 / 测验 workflow 定义
│   │   ├── tools.py             # read_memory / write_memory / search_*
│   │   ├── prompts.py           # 提示词模板
│   │   └── config.py
│   ├── tests/                   # pytest 测试
│   ├── conftest.py
│   └── pyproject.toml
├── docs/                        # 设计文档、spec、spike 记录
├── scripts/                     # 辅助脚本（fetch_senza_wheels.sh 等）
└── README.md
```

注意：`android/app/build.gradle.kts` 的 `chaquopy.sourceSets` 同时引入了 `../python`（核心逻辑）和 `src/main/python`（Android 专用入口），两者都会打进 APK。

---

## 环境准备

### 1. 安装 Android Studio

Lumo 使用 AGP 8.5.0，要求 **Android Studio Koala (2024.1.1) 或更新版本**。建议直接安装最新稳定版。

#### macOS（Apple Silicon / Intel）

1. 访问官方下载页：<https://developer.android.com/studio>
2. 下载对应架构的 `.dmg`：
   - Apple Silicon（M 系列芯片）→ `android-studio-<版本>-mac_arm.dmg`
   - Intel → `android-studio-<版本>-mac.dmg`
3. 打开 `.dmg`，将 **Android Studio** 拖入 `Applications` 文件夹。
4. 首次启动时：
   - 若提示从旧版本迁移设置，选择 **Do not import settings**（新装）。
   - 跟随 Setup Wizard，它会自动下载 **Android SDK**、**SDK Platform** 与 **SDK Build-Tools**。
   - 建议在 "SDK Components Setup" 步骤保持默认勾选（Android SDK、Android SDK Platform、Android Virtual Device）。

#### Windows

1. 下载 `android-studio-<版本>-windows.exe`。
2. 运行安装程序，保持默认组件勾选（包含 Android Virtual Device）。
3. 安装路径避免含空格或中文（例如 `C:\Android\Android Studio`）。
4. 启动后跟随 Setup Wizard 完成 SDK 初始下载。

#### Linux

1. 下载 `android-studio-<版本>-linux.tar.gz`。
2. 解压到 `/usr/local/` 或 `~/android-studio/`：
   ```bash
   sudo tar -xzf android-studio-*.tar.gz -C /usr/local/
   ```
3. 启动：
   ```bash
   /usr/local/android-studio/bin/studio.sh
   ```
4. 可选：创建桌面图标（Tools → Create Desktop Entry）。

> 国内网络环境建议在 Setup Wizard 之前先配置代理（Android Studio → Settings → Appearance & Behavior → System Settings → HTTP Proxy），否则 SDK 下载可能极慢或失败。

#### 验证安装

启动 Android Studio，在欢迎页右下角应显示版本号 ≥ 2024.1.1。打开本项目（见下文）后 Android Studio 会自动识别 Gradle 工程。

---

### 2. 配置 Android SDK 与 NDK

打开 `android/` 工程后，进入 **Android Studio → Tools → SDK Manager**（或欢迎页的 More Actions → SDK Manager）：

1. **SDK Platforms** 标签：勾选 **Android 14.0 (API 34)**（`compileSdk = 34`，`targetSdk = 34`）。
2. **SDK Tools** 标签，确保勾选：
   - **Android SDK Build-Tools**（最新版）
   - **Android SDK Command-line Tools (latest)**
   - **Android Emulator**
   - **Android SDK Platform-Tools**
   - **NDK (Side by side)** —— 选一个较新版本（如 26.x 或 27.x）。Senza wheel 的交叉编译依赖 NDK；运行时不需要 NDK，但保留方便后续重建 wheel。
3. 点击 **Apply** 下载安装。

Android Studio 会自动生成 `android/local.properties`，内容形如：

```properties
sdk.dir=/Users/<你>/Library/Android/sdk
```

该文件已在 `.gitignore` 中，**不要提交**。

#### 设置环境变量（推荐）

将 SDK 与 NDK 路径加入 shell 配置（`~/.zshrc` / `~/.bashrc`），方便命令行构建和 wheel 重建：

```bash
# macOS 示例（路径以 SDK Manager 实际显示为准）
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"   # 改成你装的 NDK 版本
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin"
```

```bash
source ~/.zshrc   # 或重开终端
```

验证：

```bash
adb --version              # 应输出 Android Debug Bridge 版本
echo $ANDROID_NDK_HOME     # 应指向 NDK 目录
```

---

### 3. 安装 Python 3

Lumo 核心逻辑开发需要本地 Python 3.9+（推荐 3.12，与 Chaquopy 运行时一致）。

#### macOS

```bash
brew install python@3.12
```

#### Linux / WSL

```bash
sudo apt update && sudo apt install -y python3.12 python3.12-venv python3-pip
```

#### Windows

从 <https://www.python.org/downloads/> 下载 3.12 安装包，安装时勾选 "Add python.exe to PATH"。

#### 验证

```bash
python3 --version   # 应 >= 3.9
```

---

### 4. 安装 Rust（可选，重建 Senza wheel 时需要）

仅当你需要从源码重新编译 Senza 的 Android wheel（例如 Senza 升级后）时才需要。仓库已附带预编译 wheel：

```
android/senza-wheel/senza_sdk-1.0.0-cp39-abi3-android_24_arm64_v8a.whl
```

日常开发**无需**安装 Rust。

如确需重建：

```bash
# 1. 安装 Rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# 2. 添加 Android target
rustup target add aarch64-linux-android

# 3. 安装 maturin
pip install maturin

# 4. 详见「重建 Senza Android Wheel」一节
```

---

## Python 核心开发

Python 核心代码在 `python/lumo/`，可独立于 Android 进行开发与测试。

### 安装为可编辑包

```bash
cd python
python3 -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -e ".[dev]"
```

这会安装 `senza-sdk` 及开发依赖 `pytest`。`senza-sdk` 从 PyPI 获取（桌面端的纯 Python / 对应平台 wheel）。

### 运行测试

```bash
cd python
pytest                             # 运行全部测试
pytest tests/test_store.py -v      # 单个模块
```

测试用 `tmp_db_path` fixture（见 `python/tests/conftest.py`）创建临时 SQLite 文件，不污染本地数据。

### 模块职责

| 模块 | 职责 |
|---|---|
| `lumo.agent` | 对话、计划、测验、笔记摘要（调用 Senza API） |
| `lumo.bridge` | Kotlin↔Python 桥接入口，被 Chaquopy 调用 |
| `lumo.store` | SQLite 存储层（sessions / messages / plans / tasks / notes 等） |
| `lumo.workflows` | 计划生成、测验的 Senza WorkflowEngine 定义 |
| `lumo.tools` | `read_memory` / `write_memory` / `search_notes` / `search_quiz_errors` |
| `lumo.prompts` | 提示词模板 |
| `lumo.config` | 配置读取 |

> 修改 `python/lumo/` 后无需重新构建——Chaquopy 的 `srcDir("../python")` 会直接把源码打进 APK，下次 `./gradlew assembleDebug` 即生效。

---

## 构建与运行 Android 应用

### 前置检查

1. 已安装 Android Studio 并配置好 SDK（API 34）。
2. `android/local.properties` 存在且 `sdk.dir` 指向有效 SDK（用 Android Studio 打开一次工程即自动生成）。
3. Senza Android wheels 已下载到 `android/senza-wheel/`（见下方）。
4. （可选）设置 LLM 环境变量，见[环境变量](#环境变量)。

### 下载 Senza Android Wheels

Wheels 不在仓库中，从 [Senza GitHub Releases](https://github.com/oh-my-harness/Senza/releases) 下载：

```bash
# 下载最新 release 的 Android wheels（需要 gh CLI 或 curl）
./scripts/fetch_senza_wheels.sh

# 或指定版本
./scripts/fetch_senza_wheels.sh v1.0.1
```

下载后 `android/senza-wheel/` 应包含两个文件：

```
senza_sdk-1.0.0-cp39-abi3-android_24_arm64_v8a.whl   # 真机 + Apple Silicon 模拟器
senza_sdk-1.0.0-cp39-abi3-android_24_x86_64.whl      # Intel/AMD PC 模拟器
```

### 用 Android Studio 打开工程

1. Android Studio → **Open**（非 New Project）。
2. 选择 `lumo/android/` 目录（**不是**仓库根目录）。
3. 等待 Gradle Sync 完成（首次会下载 Gradle 8.9 及全部依赖，需联网，可能耗时较长）。

### 连接设备

**真机（arm64-v8a）**：

1. 手机开启 **开发者选项** → 启用 **USB 调试**。
2. USB 连接电脑，在手机弹窗中允许调试。
3. 验证：
   ```bash
   adb devices
   # 应列出设备，状态为 device
   ```

**模拟器**：支持 arm64-v8a 和 x86_64 两种镜像——根据你的 CPU 选：
- **Apple Silicon Mac** → arm64-v8a 镜像（硬件加速，流畅）
- **Intel/AMD PC（Windows/Linux）** → x86_64 镜像（硬件加速，流畅）

在 AVD Manager 中创建对应 ABI 的虚拟设备即可，两种架构 Senza wheel 都已包含。

### 运行 / 构建

- **IDE 内**：选择设备 → 点击 ▶ Run。
- **命令行**：
  ```bash
  cd android
  ./gradlew assembleDebug          # 构建 debug APK
  ./gradlew installDebug           # 构建并安装到已连接设备
  ```
  产物路径：`android/app/build/outputs/apk/debug/app-debug.apk`

### 签名

Debug 构建使用 Android 默认 debug keystore，无需额外配置。Release 构建需自备 keystore（`.jks`），相关文件已在 `.gitignore` 中，**不要提交密钥**。

---

## 测试

Lumo 的测试分两层：**Python 单元测试**（桌面端，纯逻辑）与 **Android 插桩测试**（设备/模拟器端，Kotlin→Chaquopy→Python 全链路）。两者互补，缺一不可。

| 层级 | 位置 | 运行环境 | 命令 | 验证内容 |
|---|---|---|---|---|
| Python 单元测试 | `python/tests/` | 本地 Python | `pytest` | store / agent / workflows / tools / prompts / bridge 的纯逻辑 |
| Android 插桩测试 | `android/app/src/androidTest/` | 设备/模拟器 | `./gradlew :app:connectedDebugAndroidTest` | Kotlin↔Chaquopy 桥接、真实 Android SQLite、类型转换 |

---

### Python 单元测试

测试文件位于 `python/tests/`，使用 `pytest`，依赖 `tmp_db_path` fixture（`python/tests/conftest.py`）创建临时 SQLite 文件，测试完自动清理，不污染本地数据。

```bash
cd python
pytest                              # 运行全部
pytest tests/test_store.py -v       # 单个模块
pytest -k "test_create" -v          # 按名称筛选
pytest --lf                         # 只跑上次失败的
```

覆盖模块：

| 测试文件 | 覆盖 |
|---|---|
| `test_store.py` / `test_store_edge.py` | SQLite CRUD、FTS5 搜索、边界情况 |
| `test_agent.py` | Senza 对话调用 |
| `test_workflows.py` | 计划生成 / 测验 workflow |
| `test_tools.py` | read_memory / write_memory / search_* |
| `test_bridge.py` / `test_bridge_edge.py` | Python 桥接层 |
| `test_prompts.py` | 提示词模板 |
| `test_config.py` | 配置读取 |

> Python 单元测试不需要 Android 设备、Chaquopy 或 LLM API Key，可在任何装了 Python 3.9+ 的机器上运行。

---

### Android 插桩测试（Instrumented Test）

测试文件：`android/app/src/androidTest/java/com/lumo/app/BridgeInstrumentedTest.kt`。

它通过 `AndroidJUnit4` runner 在真实 Android 运行时执行，`@Before` 中调用 `LumoRepository.init(ctx)` 初始化 Chaquopy Python 解释器，每个 `@Test` 方法都走完整的 Kotlin → Chaquopy → Python → SQLite 链路。这能捕获 Python 单元测试无法发现的问题：Chaquopy 的类型转换、JNI 开销、真实 Android 文件系统上的 SQLite 行为。

运行命令（需已连接设备或已启动模拟器）：

```bash
cd android
./gradlew :app:connectedDebugAndroidTest
```

测试报告：`android/app/build/reports/androidTests/connected/`（HTML 格式，浏览器打开 `index.html` 查看）。

在 Android Studio 中也可右键 `BridgeInstrumentedTest.kt` → **Run 'BridgeInstrumentedTest'**，选择目标设备后执行，结果在 Run 窗口实时显示。

覆盖范围（共 20+ 用例）：

- **Provider 配置**：`saveAndLoadProviderConfig`
- **会话 CRUD**：创建 / 列表 / 删除 / 改标题
- **计划 CRUD**：创建 / 列表 / 改状态 / 删除
- **笔记 CRUD + FTS 搜索**：创建 / 列表 / 搜索 / 改删
- **文件夹**：创建 / 列表
- **测验判分**：答对 / 答错 / 不存在的题目
- **统计**：`getStats` / `getStreak` / `getTotalStudyTime` 结构校验
- **快捷提问**：`getQuickPrompts` 非空校验
- **今日任务**：`getTodayTasks` 返回校验
- **错误路径**：未初始化 chat 就发消息应抛异常
- **类型边界**：`checkinTodayAcceptsList`（Kotlin `List<String>` → Python 的类型转换）

---

### 在模拟器上测试

#### 1. 创建模拟器（选择匹配你 CPU 的 ABI）

Lumo 支持 `arm64-v8a` 和 `x86_64` 两种 ABI，根据你的电脑 CPU 选择：

| 宿主机 CPU | 推荐 ABI | 原因 |
|---|---|---|
| Apple Silicon（M 系列） | `arm64-v8a` | 硬件加速，流畅 |
| Intel / AMD（Windows/Linux） | `x86_64` | 硬件加速，流畅 |

1. Android Studio → **Tools → Device Manager**（或欢迎页 More Actions → Virtual Device Manager）。
2. 点击 **Create Device**。
3. 选硬件：建议 **Pixel 7** 或 **Pixel 6**（系统资源充足）。
4. 选系统镜像：选 ABI 列匹配你 CPU 的镜像（`arm64-v8a` 或 `x86_64`）。
   - 若列表中没有对应镜像，点击 **Download** 下载对应 API level（推荐 API 34）的镜像。
5. 建议配置：
   - **AVD Name**：`Lumo_API34_<abi>`（如 `Lumo_API34_x86_64`）
   - **Startup orientation**：Portrait
   - **Advanced Settings → RAM**：≥ 2048 MB
   - 启用 **Internal Storage** 足够（默认即可）
6. 点击 **Finish** 创建。

#### 2. 启动模拟器

- Android Studio Device Manager 中点击 ▶ 启动，或命令行：
  ```bash
  # 列出已创建 AVD
  $ANDROID_HOME/emulator/emulator -list-avds

  # 启动（后台运行）
  $ANDROID_HOME/emulator/emulator -avd Lumo_API34_x86_64 -netdelay none -netspeed full &
  ```
- 等待模拟器完全启动（出现锁屏界面）。

#### 3. 在模拟器上运行测试

**插桩测试**（模拟器会被自动识别为目标设备）：

```bash
cd android
./gradlew :app:connectedDebugAndroidTest
```

若同时连接了真机和模拟器，Gradle 默认在**所有**已连接设备上跑。指定单设备可用 `android.serialNumber`：

```bash
# 先查设备序列号
adb devices
# emulator-5554 是模拟器
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.android.serialNumber=emulator-5554
```

**手动测试 App**：

```bash
cd android
./gradlew installDebug      # 安装到模拟器
# 或在 Android Studio 选模拟器后点 ▶ Run
```

安装后从模拟器应用抽屉打开 **Lumo**，手动验证 UI 交互、对话流、计划生成等功能。

#### 4. 模拟器测试注意事项

- **网络**：模拟器默认能访问宿主机 `10.0.2.2`。若 LLM API 在本机起的服务，用 `http://10.0.2.2:<port>` 而非 `localhost`。
- **首次启动较慢**：Chaquopy 首次加载 Python 解释器 + pip install wheel 需要几秒到十几秒，属正常现象。
- **快照**：AVD Manager 可保存快照，避免每次冷启动。但若修改了 Python 代码并重新构建，需**清除应用数据**或卸载重装，否则 Chaquopy 可能缓存旧 `.py`：
  ```bash
  adb -s emulator-5554 shell pm clear com.lumo.app
  ```
- **日志查看**：
  ```bash
  adb -s emulator-5554 logcat -s Python  # 只看 Chaquopy/Python 日志
  adb -s emulator-5554 logcat -s Lumo    # 只看 App 日志
  ```

---

### 在真机上测试

真机是 Lumo 的首选测试环境（原生 arm64，性能与真机行为一致）。

#### 1. 开启开发者模式与 USB 调试

1. 手机 **设置 → 关于手机** → 连续点击 **版本号** 7 次，出现"您已处于开发者模式"提示。
2. 返回 **设置 → 系统 → 开发者选项**（部分机型在"设置 → 其他设置 → 开发者选项"）。
3. 开启 **USB 调试**。
4. （可选但推荐）开启 **USB 安装**（允许通过 USB 安装应用），部分品牌手机还需登录账号才能开启。

#### 2. 连接并授权

1. 用**数据线**（非仅充电线）连接手机与电脑。
2. 手机弹窗"是否允许 USB 调试？"→ 勾选"始终允许"→ **确定**。
3. 验证连接：
   ```bash
   adb devices
   # 输出示例：
   # List of devices attached
   # 9a2b3c4d5e6f    device
   ```
   若状态为 `unauthorized`，检查手机上的授权弹窗是否未确认；拔插重连可重新触发。

#### 3. 无线调试（Android 11+，可选）

避免长期插线：

1. 开发者选项 → 开启 **无线调试**。
2. 选择 **使用配对码配对设备**，记下配对码与 IP:端口。
3. 电脑配对：
   ```bash
   adb pair 192.168.1.100:port    # 输入配对码
   adb connect 192.168.1.100:5555 # 连接（端口看无线调试页面显示）
   adb devices                    # 确认状态为 device
   ```
4. 之后 `./gradlew` 与 `adb` 命令与 USB 模式一致。

#### 4. 在真机上运行测试

**插桩测试**：

```bash
cd android
# 若只连了一台真机，直接运行
./gradlew :app:connectedDebugAndroidTest

# 若同时连了多台设备，指定序列号
adb devices
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.android.serialNumber=<序列号>
```

**手动测试 App**：

```bash
cd android
./gradlew installDebug      # 安装到真机
# 或 Android Studio 选真机后点 ▶ Run
```

#### 5. 真机测试注意事项

- **API Key**：真机访问 LLM 需要手机能联网到 API 地址。若用自建 API，确保手机与服务器在同一网络或服务器有公网 IP。构建时通过环境变量传入正确的 `OPENAI_API_BASE`（见[环境变量](#环境变量)）。
- **权限**：App 需要 `INTERNET` 权限（已在 `AndroidManifest.xml` 声明）。Android 6+ 运行时权限无需额外处理。
- **清除数据**：测试过程中若出现数据残留导致行为异常：
  ```bash
  adb shell pm clear com.lumo.app
  ```
- **查看日志**：
  ```bash
  adb logcat -s Python    # Chaquopy/Python 日志
  adb logcat -s Lumo      # App 日志
  adb logcat *:E          # 只看所有 Error 级别
  ```
- **卸载**：
  ```bash
  adb uninstall com.lumo.app
  ```

---

### 测试覆盖范围

当前测试矩阵：

```
python/tests/                          # 纯逻辑，桌面跑
├── test_store.py + test_store_edge.py
├── test_agent.py
├── test_workflows.py
├── test_tools.py
├── test_bridge.py + test_bridge_edge.py
├── test_prompts.py
└── test_config.py

android/app/src/androidTest/           # 全链路，设备/模拟器跑
└── BridgeInstrumentedTest.kt          # Kotlin→Chaquopy→Python→SQLite
```

**尚未覆盖**（未来补充）：

- UI 测试（Compose UI Test，`androidx.compose.ui:ui-test-junit4` 依赖已声明但未写用例）
- LLM 端到端测试（需真实 API Key，建议作为手动 smoke test）
- Senza `stream_prompt` 流式回调通过 Chaquopy 桥接的验证（spike 中以非流式 `events()` 迭代器验证）

建议的本地测试工作流：

1. 改完 Python 代码 → `cd python && pytest`
2. 改完 Kotlin 或桥接层 → 连真机/启动模拟器 → `./gradlew :app:connectedDebugAndroidTest`
3. 提交前 → 全量跑一遍 Python 单元测试 + 插桩测试

---

## 环境变量

`android/app/build.gradle.kts` 在 `defaultConfig` 中从环境变量读取 LLM 配置并注入 `BuildConfig`：

| 环境变量 | BuildConfig 字段 | 默认值 |
|---|---|---|
| `OPENAI_API_KEY` | `OPENAI_API_KEY` | `sk-placeholder` |
| `OPENAI_API_BASE` | `OPENAI_API_BASE` | `http://api.hyper-op.com/` |
| `OPENAI_MODEL` | `OPENAI_MODEL` | `glm-5.2` |

在命令行构建时传入：

```bash
cd android
OPENAI_API_KEY="sk-xxx" OPENAI_API_BASE="https://api.deepseek.com/" OPENAI_MODEL="deepseek-chat" \
  ./gradlew installDebug
```

在 Android Studio 中运行时，可在 **Run/Debug Configurations → app → Environment Variables** 中配置。

> 这些默认值仅用于 spike 验证。正式运行请务必替换为自有 API Key。

---

## 重建 Senza Android Wheel（可选）

Wheels 从 Senza GitHub Releases 下载（见上文）。**仅当 Senza 源码更新或需要升级时才执行本节**重建。详细步骤见 `docs/2026-07-22-senza-android-spike-plan.md`（Task 1）与 `docs/2026-07-22-senza-android-spike-results.md`。

简要流程（假设 Senza 源码在 `../Senza`）：

```bash
# 1. 确保 NDK 与 Rust target 已就绪
rustup target add aarch64-linux-android x86_64-linux-android
echo $ANDROID_NDK_HOME   # 必须指向有效 NDK

# 2. 在 Senza 仓库执行其构建脚本（同时构建 arm64 + x86_64）
cd ../Senza
NDK_HOME=$ANDROID_NDK_HOME ./scripts/build_android_wheel.sh

# 3. 将产出的 wheel 上传到 GitHub Release
gh release upload v1.0.1 dist/senza_sdk-*-android_*.whl --clobber

# 4. 回到 Lumo 仓库，重新下载
cd ../lumo
./scripts/fetch_senza_wheels.sh v1.0.1

# 5. 若 wheel 文件名变化，更新 android/app/build.gradle.kts 中 pip.install(...) 的路径
```

关键技术点（来自 spike 验证）：

- Senza 是 PyO3 `cdylib`，需用 `maturin build --release --target <target>` 交叉编译。`build_android_wheel.sh` 同时构建 `aarch64-linux-android` 和 `x86_64-linux-android` 两个 target。
- 运行时需让 Senza 的 `.so` 声明 `NEEDED libpython3.12.so`，否则 Android 动态链接器无法解析 CPython 符号。做法是在 Senza 的 `.cargo/config.toml` 加 `rustflags = ["-C", "link-arg=-lpython3.12"]`，并复制 Chaquopy 的 `libpython3.12.so` 到 NDK sysroot。
- `WorkflowEngine` 在 Android 上需传入 `session_base_dir` 指向应用私有存储（如 `/data/user/0/com.lumo.app/files/lumo/sessions`）。

---

## 常见问题排查

### Gradle Sync 失败 / 依赖下载超时

国内网络问题。配置代理或在 `android/gradle.properties` 末尾追加国内镜像（仅本地，勿提交）：

```properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7890
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7890
```

或使用镜像源替换 `settings.gradle.kts` 的仓库地址。

### `local.properties` 缺失 / `sdk.dir` 错误

用 Android Studio 打开一次 `android/` 目录会自动生成。或手动创建：

```properties
sdk.dir=/Users/<你>/Library/Android/sdk
```

### 安装到设备时报 `INSTALL_FAILED_NO_MATCHING_ABIS`

模拟器 ABI 与 Senza wheel 不匹配。确认 `android/senza-wheel/` 下同时有 `arm64_v8a` 和 `x86_64` 两个 wheel（运行 `./scripts/fetch_senza_wheels.sh` 下载），且模拟器使用的是这两种 ABI 之一。

### Chaquopy 报 `ModuleNotFoundError: senza`

检查 `android/senza-wheel/` 下是否存在 wheel 文件，且 `android/app/build.gradle.kts` 的 `pip.install(...)` 路径与文件名一致。

### Chaquopy 报 `dlopen failed: cannot locate symbol "PyExc_RuntimeError"`

Senza wheel 未正确链接 `libpython3.12.so`。需按[重建 wheel](#重建-senza-android-wheel可选) 一节重新编译，确保 `.cargo/config.toml` 包含 `link-arg=-lpython3.12`。

### `OPENAI_API_KEY` 为 placeholder

构建时未设置环境变量，App 内调用 LLM 会失败。见[环境变量](#环境变量)。

### Python 测试报 `ImportError: senza`

未安装开发依赖。执行 `cd python && pip install -e ".[dev]"`。

---

## 相关文档

- [设计总览](2026-07-22-lumo-design.md)
- [P0 MVP Spec](2026-07-22-p0-mvp-spec.md)
- [Senza + Chaquopy Android Spike 计划](2026-07-22-senza-android-spike-plan.md)
- [Senza + Chaquopy Android Spike 结果](2026-07-22-senza-android-spike-results.md)
