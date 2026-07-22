# Senza + Chaquopy Android 技术验证（Spike）计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 验证 Senza（PyO3 Python SDK）能通过 Chaquopy 在 Android 上运行，包括 import、基础对话、流式输出和 WorkflowEngine 执行。

**Architecture:** Android App (Kotlin + Chaquopy) → CPython 解释器 → Senza wheel（PyO3 → Rust `llm-harness-runtime`）→ 云端 LLM API。Senza wheel 需要交叉编译到 `aarch64-linux-android` target。

**Tech Stack:** Kotlin, Jetpack Compose, Chaquopy, Senza (PyO3), Rust cross-compile to Android, `aarch64-linux-android`

## Global Constraints

- Senza 源码在 `../Senza`，wheel 构建脚本为 `../Senza/scripts/build_wheel.sh`
- Senza CI 目前只构建 linux/mac/windows wheel，没有 Android target
- Senza 是 `cdylib`（PyO3 extension module），不是独立 Rust 库
- Chaquopy 自带 CPython 3.x 解释器，Android arm64-v8a
- Senza `pyproject.toml` 使用 `pyo3/abi3-py39`，与 Chaquopy 的 CPython 兼容性需验证
- LLM API Key 通过环境变量或用户输入传入，不硬编码

---

## Task 1: 交叉编译 Senza wheel 到 aarch64-linux-android

**Files:**
- Create: `../Senza/scripts/build_android_wheel.sh`
- Modify: `../Senza/.cargo/config.toml`（可能需要添加 Android linker 配置）

**Interfaces:**
- Consumes: `../Senza/senza-pkg/runtime.lock`（runtime SHA）
- Produces: `../Senza/dist/senza_sdk-*-android_arm64.whl`（Android arm64 wheel）

**验证目标：** Senza 的 PyO3 扩展能编译为 `aarch64-linux-android` 目标的 `.so` 文件，打包成 wheel。

- [ ] **Step 1: 安装 Android NDK 和 Rust Android target**

```bash
# 安装 Rust Android targets
rustup target add aarch64-linux-android

# 确认 NDK 已安装（通常通过 Android Studio）
# 设置 NDK 环境变量
echo "NDK_HOME=$ANDROID_NDK_HOME"
```

Expected: `rustup target list --installed` 包含 `aarch64-linux-android`

- [ ] **Step 2: 创建 Cargo Android 交叉编译配置**

Create `../Senza/.cargo/config.toml`（如果已有则合并）:

```toml
[target.aarch64-linux-android]
linker = "<NDK_PATH>/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android24-clang"
ar = "<NDK_PATH>/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-ar"
```

注意：`<NDK_PATH>` 需要替换为实际 NDK 路径。`android24` = API level 24（Android 7.0+）。

- [ ] **Step 3: 创建 Android wheel 构建脚本**

Create `../Senza/scripts/build_android_wheel.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

# Build Senza wheel for Android aarch64.
#
# Prerequisites:
#   - rustup target add aarch64-linux-android
#   - Android NDK installed
#   - NDK_HOME env var set
#
# Usage:
#   NDK_HOME=/path/to/ndk ./scripts/build_android_wheel.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

if [ -z "${NDK_HOME:-}" ]; then
    echo "ERROR: NDK_HOME is not set"
    exit 1
fi

NDK_TOOLCHAIN="$NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64"

LOCK_FILE="$REPO_ROOT/senza-pkg/runtime.lock"
CARGO_TOML="$REPO_ROOT/Cargo.toml"

SHA=$(cat "$LOCK_FILE")
echo "==> Runtime pin: $SHA"

# Inject SHA into Cargo.toml
cp "$CARGO_TOML" "$CARGO_TOML.bak"
trap 'mv "$CARGO_TOML.bak" "$CARGO_TOML" 2>/dev/null || true' EXIT
perl -pi -e "s/PLACEHOLDER/$SHA/g" "$CARGO_TOML"

# Ensure .cargo/config.toml has Android linker config
mkdir -p "$REPO_ROOT/.cargo"
cat > "$REPO_ROOT/.cargo/config.toml" << EOF
[target.aarch64-linux-android]
linker = "$NDK_TOOLCHAIN/bin/aarch64-linux-android24-clang"
ar = "$NDK_TOOLCHAIN/bin/llvm-ar"
EOF

cd "$REPO_ROOT"
echo "==> Building Android aarch64 wheel..."

# Use maturin to build for Android target
# PYO3_PYTHON points to a Python 3.9+ for abi3 compatibility
export PYO3_PYTHON="${PYO3_PYTHON:-python3}"

"$PYO3_PYTHON" -m maturin build --release --target aarch64-linux-android

WHEEL=$(ls -t "$REPO_ROOT"/target/wheels/senza*android*.whl 2>/dev/null | head -1)
if [ -z "$WHEEL" ]; then
    # Fallback: check for any wheel
    WHEEL=$(ls -t "$REPO_ROOT"/target/wheels/senza*.whl 2>/dev/null | head -1)
fi

if [ -z "$WHEEL" ]; then
    echo "ERROR: No wheel found in $REPO_ROOT/target/wheels/"
    ls -la "$REPO_ROOT/target/wheels/" 2>/dev/null || true
    exit 1
fi

mkdir -p "$REPO_ROOT/dist"
cp "$WHEEL" "$REPO_ROOT/dist/"
echo "==> Built: $WHEEL"
echo "==> Copied to: $REPO_ROOT/dist/$(basename $WHEEL)"
```

- [ ] **Step 4: 运行构建脚本**

```bash
cd ../Senza
NDK_HOME=$ANDROID_NDK_HOME ./scripts/build_android_wheel.sh
```

Expected: 在 `dist/` 目录下生成 `senza_sdk-*.whl`，文件名包含 `android` 或 `aarch64`。

**如果失败：** 记录编译错误。常见问题：
- Rust 依赖链中某个 crate 不支持 `aarch64-linux-android`
- NDK 版本不兼容
- PyO3 的 `extension-module` feature 在 Android 上的行为差异

- [ ] **Step 5: 验证 wheel 内容**

```bash
# 解压 wheel 检查 .so 文件
cd dist
unzip -l senza*.whl | grep '\.so'
```

Expected: 包含 `senza.cpython-3xx-abi3-linux-android.so` 或类似文件。

- [ ] **Step 6: Commit（在 Senza 仓库）**

```bash
cd ../Senza
git add scripts/build_android_wheel.sh
git commit -m "build: add Android aarch64 wheel cross-compile script"
```

注意：`.cargo/config.toml` 可能已在 `.gitignore` 中，不要提交 NDK 路径。

---

## Task 2: 创建最小 Android 测试 App 集成 Chaquopy + Senza

**Files:**
- Create: `android/build.gradle.kts`
- Create: `android/settings.gradle.kts`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/java/com/lumo/spike/MainActivity.kt`
- Create: `android/app/src/main/python/lumo_spike.py`
- Create: `android/app/src/main/res/layout/activity_main.xml`

**Interfaces:**
- Consumes: Task 1 产出的 Senza Android wheel
- Produces: 一个能 `import senza` 并调用基础 API 的 Android App

**验证目标：** Chaquopy 能加载 Senza wheel，`import senza` 成功，能创建 `HarnessBuilder`。

- [ ] **Step 1: 创建 Android 项目骨架**

Create `android/settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "lumo-spike"
include(":app")
```

Create `android/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("com.chaquo.python") version "16.0.0" apply false
}
```

- [ ] **Step 2: 配置 app 模块的 Gradle**

Create `android/app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.lumo.spike"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lumo.spike"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }
}

chaquopy {
    defaultVersion = "4.2"  // CPython 版本
    productFlavors { }
    sourceSets {
        main {
            python {
                srcDir("src/main/python")
            }
        }
    }
    pip {
        // Install Senza wheel from local file
        install("../senza-wheel/senza_sdk-1.0.0-cp39-abi3-linux_android.so")
        // Senza dependencies that are pure Python
        install("aiohttp")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
```

注意：wheel 路径需要根据 Task 1 实际产出的文件名调整。Chaquopy 对含原生扩展的 wheel 的安装方式可能需要特殊处理——如果 `pip install` 不行，尝试手动将 `.so` 放入 Chaquopy 的 Python site-packages 目录。

- [ ] **Step 3: 创建 Python 测试模块**

Create `android/app/src/main/python/lumo_spike.py`:

```python
"""Spike: verify Senza can be imported and basic APIs work on Android."""

import senza


def check_import():
    """Verify Senza can be imported and version() works."""
    version = senza.version()
    return f"Senza version: {version}"


def check_harness_builder():
    """Verify HarnessBuilder can be constructed."""
    harness = (
        senza.HarnessBuilder("gpt-4o")
        .system_prompt("You are a helpful tutor.")
        .max_tokens(128)
        .build()
    )
    return f"HarnessBuilder OK, phase: {harness.phase()}"


def check_provider(api_key: str, base_url: str = ""):
    """Verify provider creation works."""
    kwargs = {"api_key": api_key}
    if base_url:
        kwargs["base_url"] = base_url
    provider = senza.create_openai_provider(**kwargs)
    return f"Provider created OK"
```

- [ ] **Step 4: 创建 MainActivity 调用 Python**

Create `android/app/src/main/java/com/lumo/spike/MainActivity.kt`:

```kotlin
package com.lumo.spike

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.textSize = 16f
        tv.setPadding(32, 64, 32, 32)
        setContentView(tv)

        val py = Python.getInstance()
        val module = py.getModule("lumo_spike")

        val results = mutableListOf<String>()

        // Test 1: import senza
        results.add("Test 1 (import): " + module.callAttr("check_import").toString())

        // Test 2: HarnessBuilder
        results.add("Test 2 (harness): " + module.callAttr("check_harness_builder").toString())

        // Test 3: provider creation (use dummy key, don't actually call LLM)
        results.add("Test 3 (provider): " + module.callAttr("check_provider", "sk-test").toString())

        tv.text = results.joinToString("\n\n")
    }
}
```

- [ ] **Step 5: 配置 AndroidManifest**

Create `android/app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:label="Lumo Spike"
        android:theme="@style/Theme.AppCompat.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 6: 构建 APK 并安装到设备/模拟器**

```bash
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.lumo.spike/.MainActivity
```

Expected: App 启动，显示三个测试结果。如果 Senza 成功 import，Test 1 显示版本号。

**如果失败：** 记录错误日志：
- `adb logcat | grep -i "python\|senza\|chaquopy\|ImportError\|ModuleNotFoundError"`
- 常见问题：Chaquopy 无法加载 `.so`（ABI 不匹配）、Python 版本不兼容、缺少依赖

- [ ] **Step 7: Commit**

```bash
cd ..
git add android/
git commit -m "spike: add Android test app for Senza + Chaquopy integration"
```

---

## Task 3: 验证 Senza 流式对话通过 Chaquopy 回调到 Kotlin

**Files:**
- Modify: `android/app/src/main/python/lumo_spike.py`（添加流式测试函数）
- Modify: `android/app/src/main/java/com/lumo/spike/MainActivity.kt`（添加流式回调）

**Interfaces:**
- Consumes: Task 2 的 Chaquopy + Senza 环境
- Produces: 流式 token 从 Python 推送到 Kotlin 的验证结果

**验证目标：** Senza 的 `harness.events()` 迭代器能在 Android 上工作，token 通过 Chaquopy callback 实时推送到 Kotlin。

- [ ] **Step 1: 添加 Python 流式测试函数**

在 `lumo_spike.py` 末尾添加:

```python
import threading


def stream_chat(api_key: str, model: str, question: str, callback):
    """Stream a chat response, calling callback(token) for each text delta.

    Args:
        api_key: LLM API key
        model: Model name (e.g. "gpt-4o")
        question: User question
        callback: Chaquopy PythonCallback object with onToken(str) method
    """
    provider = senza.create_openai_provider(api_key=api_key)
    harness = (
        senza.HarnessBuilder(model)
        .provider("*", provider)
        .system_prompt("You are a helpful tutor.")
        .max_tokens(256)
        .build()
    )

    done = threading.Event()

    def stream_events():
        for event in harness.events(timeout_ms=30000):
            t = event["type"]
            if t == "text_delta":
                text = event.get("text", "")
                callback.onToken(text)
            elif t in ("settled", "aborted", "error"):
                done.set()
                break

    stream_thread = threading.Thread(target=stream_events)
    stream_thread.start()

    harness.prompt(question)
    stream_thread.join(timeout=60)

    return harness.phase()
```

- [ ] **Step 2: 添加 Kotlin 流式回调接口**

在 `MainActivity.kt` 中添加:

```kotlin
import com.chaquo.python.PyObject
import com.chaquo.python.android.PyApplication

// Callback interface for streaming tokens
interface TokenCallback {
    fun onToken(token: String)
}

// In MainActivity, add streaming test:
private fun testStreaming(module: PyObject) {
    val apiKey = "sk-your-key"  // 用户输入
    val callback = object : TokenCallback {
        override fun onToken(token: String) {
            runOnUiThread {
                // Append token to TextView
                findViewById<TextView>(R.id.text_view).append(token)
            }
        }
    }

    // Chaquopy: pass Kotlin callback to Python
    val pyCallback = Python.getInstance().getModule("__main__").callAttr(
        "kotlin_callback_adapter", callback
    )

    Thread {
        val result = module.callAttr("stream_chat", apiKey, "gpt-4o", "用一句话解释闭包。", pyCallback)
        runOnUiThread {
            findViewById<TextView>(R.id.text_view).append("\n\nPhase: $result")
        }
    }.start()
}
```

注意：Chaquopy 的 callback 机制具体实现可能需要调整。Chaquopy 支持 `PyCallback` 接口，Python 侧可以直接调用 Kotlin 对象的方法。如果 Chaquopy 的 callback 机制不直接支持，替代方案是用 Python 的 `queue.Queue` 做中转，Kotlin 侧轮询。

- [ ] **Step 3: 构建、安装、测试**

```bash
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.lumo.spike/.MainActivity
```

Expected: App 显示流式文本输出，token 逐个出现。

**如果失败：** 记录错误，尝试替代方案（queue.Queue 轮询）。

- [ ] **Step 4: Commit**

```bash
git add android/
git commit -m "spike: test Senza streaming via Chaquopy callback"
```

---

## Task 4: 验证 Senza WorkflowEngine 在 Android 上执行

**Files:**
- Modify: `android/app/src/main/python/lumo_spike.py`（添加 workflow 测试函数）

**Interfaces:**
- Consumes: Task 2 的 Chaquopy + Senza 环境
- Produces: WorkflowEngine 能在 Android 上运行多步 workflow 的验证结果

**验证目标：** Senza `WorkflowEngine` 能在 Android 上定义、构建、执行一个简单的两步 workflow。

- [ ] **Step 1: 添加 Python workflow 测试函数**

在 `lumo_spike.py` 末尾添加:

```python
def test_workflow(api_key: str, model: str) -> str:
    """Run a minimal two-step workflow on Android.

    Step 1 (writer): Write a one-sentence story.
    Step 2 (reviewer): Rate it 1-5.
    """
    provider = senza.create_openai_provider(api_key=api_key)

    workflow = {
        "entry_step": "writer",
        "steps": [
            {
                "id": "writer",
                "name": "Writer",
                "prompt": "Write a one-sentence story about a cat.",
                "allowed_tools": [],
            },
            {
                "id": "reviewer",
                "name": "Reviewer",
                "prompt": "Rate this story 1-5. Just give the number.",
                "allowed_tools": [],
            },
        ],
        "edges": [{"from": "writer", "to": "reviewer"}],
    }

    def judge(ctx):
        step = ctx.get("step_id", "")
        if step == "writer":
            return "to:reviewer"
        return "done"

    judge_obj = senza.create_judge(judge)
    engine = senza.WorkflowEngine(workflow, provider, model, judge_obj)

    task_id = engine.task_id()
    engine.run()

    history = engine.step_history()
    results = []
    for record in history:
        step_id = record["step_id"]
        result = record.get("result", {})
        output = result.get("output", "(no result)")[:80]
        results.append(f"{step_id}: {output}")

    cost = engine.total_cost()
    return f"Task: {task_id}\nSteps: {len(history)}\n" + "\n".join(results) + f"\nTokens: {cost['total_input_tokens']} in / {cost['total_output_tokens']} out"
```

- [ ] **Step 2: 在 MainActivity 中调用 workflow 测试**

在 `MainActivity.kt` 的测试列表中添加:

```kotlin
// Test 4: Workflow (需要真实 API Key)
Thread {
    val result = module.callAttr("test_workflow", apiKey, "gpt-4o").toString()
    runOnUiThread {
        findViewById<TextView>(R.id.text_view).append("\n\nTest 4 (workflow):\n$result")
    }
}.start()
```

- [ ] **Step 3: 构建、安装、测试**

```bash
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.lumo.spike/.MainActivity
```

Expected: App 显示 workflow 执行结果，包含两个 step 的输出和 token 用量。

- [ ] **Step 4: Commit**

```bash
git add android/
git commit -m "spike: test Senza WorkflowEngine on Android"
```

---

## Task 5: 记录验证结果和已知限制

**Files:**
- Create: `docs/2026-07-22-senza-android-spike-results.md`

- [ ] **Step 1: 撰写验证结果文档**

记录以下内容：
1. Senza wheel 能否成功交叉编译到 `aarch64-linux-android`
2. Chaquopy 能否加载 Senza wheel 并 `import senza`
3. Senza 流式对话能否通过 Chaquopy callback 推送到 Kotlin
4. Senza WorkflowEngine 能否在 Android 上执行
5. 已知限制和后续需要解决的问题
6. 对 P0 MVP 实现计划的影响

- [ ] **Step 2: Commit**

```bash
git add docs/
git commit -m "docs: record Senza Android spike results"
```

---

## 验证通过标准

- ✅ `import senza` 在 Android 上成功
- ✅ `senza.HarnessBuilder` 能构造
- ✅ `senza.create_openai_provider` 能创建 provider
- ✅ `harness.events()` 流式 token 能推送到 Kotlin
- ✅ `senza.WorkflowEngine` 能执行多步 workflow

## 验证失败后的备选方案

如果 Chaquopy 无法加载 Senza 的 PyO3 `.so`：

1. **尝试 PyOxidizer** — 将 CPython + Senza 编译为单一原生库
2. **尝试 BeeWare/Briefcase** — 自带 CPython 嵌入
3. **回退到 Rust 直连** — 放弃 Senza，用 uniffi + `llm-harness-runtime` Rust API（原始架构）
4. **Python sidecar** — Python 作为独立进程（仅 Android，通过 local socket 通信）
