# Senza + Chaquopy Android 技术验证结果

> 日期：2026-07-22
> 状态：✅ 全部通过

## 验证结果

| 验证项 | 状态 | 结果 |
|---|---|---|
| Senza wheel 交叉编译到 aarch64-linux-android | ✅ | maturin + NDK，生成 `senza_sdk-1.0.0-cp39-abi3-android_24_arm64_v8a.whl` |
| Chaquopy 加载 Senza wheel | ✅ | pip install 本地 wheel 成功 |
| `import senza` | ✅ | `senza.version()` 返回 `1.0.0` |
| `HarnessBuilder` + provider 构造 | ✅ | phase 正常 |
| LLM 对话调用（`prompt_and_collect`） | ✅ | 30 in / 128 out tokens |
| `WorkflowEngine` 两步 workflow | ✅ | writer → reviewer，92 in / 261 out tokens |

## 关键技术发现

### 1. 交叉编译 Senza wheel

Senza 是 PyO3 `cdylib`，需要交叉编译到 `aarch64-linux-android`。

**步骤：**
- `rustup target add aarch64-linux-android`
- 安装 Android NDK
- 设置 `CC_aarch64_linux_android` 指向 NDK 的 `aarch64-linux-android24-clang`
- 在 NDK sysroot 创建空的 `libpython3.a` stub（让编译时链接通过）
- 用 `maturin build --release --target aarch64-linux-android` 构建

**构建脚本：** `../Senza/scripts/build_android_wheel.sh`

### 2. 运行时符号解析

Senza 的 `.so` 引用 `PyExc_RuntimeError` 等 CPython 符号，但 Android 动态链接器在 `dlopen` 扩展模块时不会自动搜索 app 的 `libpython3.12.so`。

**解决方案：**
- 复制 Chaquopy 的 `libpython3.12.so`（从 APK 中提取）到 NDK sysroot
- 在 `.cargo/config.toml` 中添加 `rustflags = ["-C", "link-arg=-lpython3.12"]`
- 这样 Senza 的 `.so` 会声明 `NEEDED libpython3.12.so`，运行时自动加载

### 3. WorkflowEngine session 目录

Android 上默认路径不可写，`WorkflowEngine` 需要传入 `session_base_dir` 参数指向应用私有存储：

```python
engine = senza.WorkflowEngine(
    workflow, provider, model, judge_obj,
    session_base_dir="/data/user/0/com.lumo.spike/files/lumo/sessions"
).with_task_store("/data/user/0/com.lumo.spike/files/lumo/tasks")
```

### 4. Chaquopy 配置

- Chaquopy 17.0.0 + Python 3.12 + Senza abi3-py39 wheel 兼容
- 用 `pip { install("${rootProject.projectDir}/senza-wheel/senza_sdk-*.whl") }` 安装本地 wheel
- AndroidManifest 中 `android:name="com.chaquo.python.android.PyApplication"` 自动初始化 Python

## 对 P0 MVP 的影响

技术验证通过，Senza + Chaquopy 方案可行。P0 MVP 可以按原设计推进：

- 核心逻辑用 Python（Senza），通过 Chaquopy 嵌入 Android
- 对话、workflow、provider 全部可用
- 需要注意 Android 上的可写目录路径（应用私有存储）
- 流式 callback（Task 3）尚未完全验证，但 `events()` 迭代器在非流式模式下工作正常
