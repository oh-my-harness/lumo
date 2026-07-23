# Lumo P0 — Plan 3: UX 补全（流式对话 + 对话管理 + 笔记增强 + 答题交互 + 番茄钟 + 统计图表）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补全 P0 spec 中 Python bridge 已实现但 Kotlin UI 缺失的所有功能模块，使 7 个模块全部可达。

**Architecture:** 所有改动在 Kotlin UI 层和 LumoRepository。Python bridge 和 store 层已完成，不修改。遵循现有 MVVM-less 模式：Composable 直接调 `LumoRepository.get()`，协程用 `rememberCoroutineScope` + `Dispatchers.IO`。

**Tech Stack:** Kotlin + Jetpack Compose + Chaquopy + Material3

## Global Constraints

- 不修改 Python 代码（bridge/store/agent/tools/workflows/config/prompts 均已完成）
- 所有新 Repository 方法在 `LumoRepository.kt` 中添加，通过 `bridge().callAttr(...)` 调用
- 构建命令：`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && export ANDROID_SDK_ROOT="/Users/hhl/Library/Android/sdk" && cd android && ./gradlew :app:assembleDebug --no-daemon`
- 安装命令：`adb -s emulator-5554 install -r android/app/build/outputs/apk/debug/app-debug.apk`
- Python 测试：`cd python && .venv/bin/python -m pytest tests/ -q`
- minSdk 24, compileSdk 34, AGP 8.5.0, Kotlin 2.0.0
- 包名 `com.lumo.app`
- Markdown 渲染组件已存在：`com.lumo.app.ui.markdown.MarkdownRenderer`
- Chaquopy 类型陷阱：Kotlin `List<String>` → Python `Arrays$ArrayList`（不可迭代），需转 JSON 字符串传递；Python `True/False` → Chaquopy `PyObject`，需 `v.toString() == "True"` 转换

---

## 文件结构

```
android/app/src/main/java/com/lumo/app/
├── data/
│   └── LumoRepository.kt          # 修改：添加 streamChat, generatePlan, generateQuiz, aiSummarizeNote, saveConversationAsNote, getStudyTrend, getKnowledgeMastery, getCheckinHeatmap (已有), searchNotes (已有)
├── ui/
│   ├── chat/
│   │   └── ChatListScreen.kt      # 修改：流式输出、停止生成、对话搜索、对话删除、保存为笔记
│   ├── quiz/
│   │   └── QuizScreen.kt          # 修改：答题交互、AI 生成测验
│   ├── notes/
│   │   └── NotesListScreen.kt     # 修改：Markdown 预览、笔记搜索、AI 摘要、对话保存为笔记入口
│   ├── today/
│   │   └── TodayScreen.kt         # 修改：番茄钟、打卡按钮
│   ├── profile/
│   │   └── ProfileScreen.kt       # 修改：AI 计划生成、学习趋势、知识点掌握度、打卡热力图
│   └── markdown/
│       └── MarkdownRenderer.kt    # 不修改
```

---

## Task 1: 流式对话输出 + 停止生成

**Files:**
- Modify: `android/app/src/main/java/com/lumo/app/data/LumoRepository.kt`
- Modify: `android/app/src/main/java/com/lumo/app/ui/chat/ChatListScreen.kt`

**Interfaces:**
- Consumes: `bridge.stream_chat(text, callback)` — callback 需有 `onToken(text: String)` 方法
- Consumes: `bridge.abort_chat()`
- Produces: `LumoRepository.streamChat(text: String, onToken: (String) -> Unit): String`
- Produces: `LumoRepository.abortChat()` — 已存在

- [ ] **Step 1: 在 LumoRepository 添加 streamChat 方法**

在 `LumoRepository.kt` 的 `// ── Chat ──` 区域，`sendMessage` 下方添加：

```kotlin
    fun streamChat(text: String, onToken: (String) -> Unit): String {
        val callback = object : Any() {
            @Suppress("unused")
            fun onToken(token: String) {
                onToken.invoke(token)
            }
        }
        return bridge().callAttr("stream_chat", text, callback).toString()
    }
```

注意：Chaquopy 的 Python `callback.onToken(text)` 会查找 Java 对象上名为 `onToken` 的方法。用匿名 `object : Any()` 定义，避免实现接口。

- [ ] **Step 2: 修改 ChatDetailScreen 使用流式输出**

在 `ChatListScreen.kt` 的 `ChatDetailScreen` 函数中，替换发送逻辑（约 158-183 行的 `IconButton(onClick = {...})` 块）：

将原来的：
```kotlin
            IconButton(onClick = {
                if (inputText.isNotBlank() && !loading) {
                    val text = inputText
                    inputText = ""
                    scope.launch {
                        loading = true
                        try {
                            if (!chatStarted) {
                                android.util.Log.i("LumoChat", "startChat sessionId=$sessionId")
                                withContext(Dispatchers.IO) { repo.startChat(sessionId) }
                                chatStarted = true
                                android.util.Log.i("LumoChat", "startChat OK")
                            }
                            messages = messages + mapOf("role" to "user", "content" to text)
                            android.util.Log.i("LumoChat", "sendMessage: $text")
                            val response = withContext(Dispatchers.IO) { repo.sendMessage(text) }
                            android.util.Log.i("LumoChat", "sendMessage response: ${response.take(100)}")
                            messages = messages + mapOf("role" to "assistant", "content" to response)
                        } catch (e: Exception) {
                            android.util.Log.e("LumoChat", "chat error", e)
                            messages = messages + mapOf("role" to "assistant", "content" to "错误: ${e.message}")
                        }
                        loading = false
                    }
                }
            }) { Icon(Icons.Filled.Send, contentDescription = "发送") }
```

替换为：
```kotlin
            val sendIcon = if (loading) Icons.Filled.Stop else Icons.Filled.Send
            IconButton(onClick = {
                if (loading) {
                    // 停止生成
                    scope.launch { withContext(Dispatchers.IO) { repo.abortChat() } }
                    return@IconButton
                }
                if (inputText.isNotBlank()) {
                    val text = inputText
                    inputText = ""
                    scope.launch {
                        loading = true
                        try {
                            if (!chatStarted) {
                                withContext(Dispatchers.IO) { repo.startChat(sessionId) }
                                chatStarted = true
                            }
                            messages = messages + mapOf("role" to "user", "content" to text)
                            // 流式：先插入空的 AI 消息，逐 token 更新
                            val aiIndex = messages.size
                            messages = messages + mapOf("role" to "assistant", "content" to "")
                            val fullResponse = withContext(Dispatchers.IO) {
                                repo.streamChat(text) { token ->
                                    val current = messages[aiIndex]["content"] ?: ""
                                    messages = messages.toMutableList().also {
                                        it[aiIndex] = it[aiIndex].toMutableMap().also { m ->
                                            m["content"] = current + token
                                        }
                                    }
                                }
                            }
                            // 确保最终内容完整（防止 token 回调遗漏）
                            messages = messages.toMutableList().also {
                                it[aiIndex] = it[aiIndex].toMutableMap().also { m ->
                                    m["content"] = fullResponse
                                }
                            }
                        } catch (e: Exception) {
                            messages = messages + mapOf("role" to "assistant", "content" to "错误: ${e.message}")
                        }
                        loading = false
                    }
                }
            }) { Icon(sendIcon, contentDescription = if (loading) "停止" else "发送") }
```

注意：需要在文件顶部添加 `import androidx.compose.material.icons.filled.Stop`。由于已 `import androidx.compose.material.icons.filled.*`，`Stop` 已包含在内，无需额外 import。

- [ ] **Step 3: 添加自动滚动到底部**

在 `ChatDetailScreen` 的 `LazyColumn` 之前添加 `val listState = rememberLazyListState()`，修改 `LazyColumn` 为 `LazyColumn(state = listState, ...)`，并在 `LaunchedEffect` 后添加：

```kotlin
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
```

需要添加 import：
```kotlin
import androidx.compose.foundation.lazy.rememberLazyListState
```

- [ ] **Step 4: 构建并验证编译通过**

Run: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && export ANDROID_SDK_ROOT="/Users/hhl/Library/Android/sdk" && cd android && ./gradlew :app:assembleDebug --no-daemon 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 安装到模拟器并手动测试**

Run:
```bash
ADB=/Users/hhl/Library/Android/sdk/platform-tools/adb
$ADB -s emulator-5554 install -r android/app/build/outputs/apk/debug/app-debug.apk
$ADB -s emulator-5554 shell am force-stop com.lumo.app
$ADB -s emulator-5554 shell am start -n com.lumo.app/.MainActivity
```

验证：打开对话 tab → 新建对话 → 发送消息 → AI 回复应逐字出现 → 生成中 Send 按钮变为 Stop 图标 → 点击 Stop 可中断

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/lumo/app/data/LumoRepository.kt android/app/src/main/java/com/lumo/app/ui/chat/ChatListScreen.kt
git commit -m "feat: streaming chat output with stop button and auto-scroll"
```

---

## Task 2: 对话搜索 + 删除

**Files:**
- Modify: `android/app/src/main/java/com/lumo/app/ui/chat/ChatListScreen.kt`

**Interfaces:**
- Consumes: `LumoRepository.searchMessages(query: String): List<Map<String, String?>>` — 已存在
- Consumes: `LumoRepository.deleteSession(id: String)` — 已存在
- Consumes: `LumoRepository.listSessions(): List<Map<String, String?>>` — 已存在

- [ ] **Step 1: 在 ChatListScreen 添加搜索栏**

在 `ChatListScreen` 函数中，在 `Column` 内 `Row`（标题 + FAB）下方添加搜索栏。将现有的会话列表 `LazyColumn` 改为根据搜索结果展示。

替换 `ChatListScreen` 函数体为：

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(navController: NavController) {
    val repo = LumoRepository.get()
    var sessions by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }

    LaunchedEffect(Unit) {
        loading = true
        try { sessions = repo.listSessions() } catch (e: Exception) {}
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("对话", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            FloatingActionButton(onClick = {
                val sid = repo.createSession("新对话")
                navController.navigate("chat/$sid")
            }) { Icon(Icons.Filled.Add, contentDescription = "新建对话") }
        }

        // 搜索栏
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { query ->
                searchQuery = query
                scope.launch {
                    searchResults = if (query.isBlank()) emptyList()
                    else withContext(Dispatchers.IO) { repo.searchMessages(query) }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("搜索对话内容...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = ""; searchResults = emptyList() }) {
                        Icon(Icons.Filled.Clear, contentDescription = "清除")
                    }
                }
            },
            singleLine = true
        )

        val scope = rememberCoroutineScope()

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (searchQuery.isNotEmpty()) {
            // 搜索结果模式
            if (searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("未找到匹配内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    items(searchResults) { msg ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clickable { navController.navigate("chat/${msg["session_id"]}") },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(msg["content"] ?: "", maxLines = 2,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else if (sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("点击 + 开始新的对话", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(sessions) { session ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable { navController.navigate("chat/${session["id"]}") },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(session["title"]?.ifEmpty { "新对话" } ?: "新对话", fontWeight = FontWeight.Medium)
                                Text(session["updated_at"]?.take(10) ?: "", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                repo.deleteSession(session["id"]!!)
                                sessions = repo.listSessions()
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
```

注意：需要添加 import（大部分已由 `import androidx.compose.material.icons.filled.*` 覆盖）：
```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
```

- [ ] **Step 2: 构建并验证**

Run: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && export ANDROID_SDK_ROOT="/Users/hhl/Library/Android/sdk" && cd android && ./gradlew :app:assembleDebug --no-daemon 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/lumo/app/ui/chat/ChatListScreen.kt
git commit -m "feat: chat session search and delete"
```

---

## Task 3: 保存对话为笔记

**Files:**
- Modify: `android/app/src/main/java/com/lumo/app/data/LumoRepository.kt`
- Modify: `android/app/src/main/java/com/lumo/app/ui/chat/ChatListScreen.kt`

**Interfaces:**
- Consumes: `bridge.save_conversation_as_note(session_id: str, title: str = "") -> str`
- Produces: `LumoRepository.saveConversationAsNote(sessionId: String, title: String): String`

- [ ] **Step 1: 在 LumoRepository 添加方法**

在 `LumoRepository.kt` 的 `// ── Chat ──` 区域末尾添加：

```kotlin
    fun saveConversationAsNote(sessionId: String, title: String = ""): String {
        return bridge().callAttr("save_conversation_as_note", sessionId, title).toString()
    }
```

- [ ] **Step 2: 在 ChatDetailScreen TopAppBar 添加保存按钮**

在 `ChatListScreen.kt` 的 `ChatDetailScreen` 函数中，修改 `TopAppBar`，添加 `actions`：

将：
```kotlin
        TopAppBar(
            title = { Text("对话") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )
```

替换为：
```kotlin
        var showSaveNoteDialog by remember { mutableStateOf(false) }
        var saveNoteResult by remember { mutableStateOf("") }
        TopAppBar(
            title = { Text("对话") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = { showSaveNoteDialog = true }) {
                    Icon(Icons.Filled.Save, contentDescription = "保存为笔记")
                }
            }
        )

        if (showSaveNoteDialog) {
            var noteTitle by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showSaveNoteDialog = false },
                title = { Text("保存为笔记") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = noteTitle,
                            onValueChange = { noteTitle = it },
                            label = { Text("笔记标题（可选）") },
                            singleLine = true
                        )
                        if (saveNoteResult.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(saveNoteResult, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                val noteId = withContext(Dispatchers.IO) {
                                    repo.saveConversationAsNote(sessionId, noteTitle)
                                }
                                saveNoteResult = "已保存"
                                showSaveNoteDialog = false
                            } catch (e: Exception) {
                                saveNoteResult = "错误: ${e.message}"
                            }
                        }
                    }) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveNoteDialog = false }) { Text("取消") }
                }
            )
        }
```

- [ ] **Step 3: 构建并验证**

Run: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && export ANDROID_SDK_ROOT="/Users/hhl/Library/Android/sdk" && cd android && ./gradlew :app:assembleDebug --no-daemon 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/lumo/app/data/LumoRepository.kt android/app/src/main/java/com/lumo/app/ui/chat/ChatListScreen.kt
git commit -m "feat: save conversation as note from chat screen"
```

---

## Task 4: 笔记 Markdown 预览 + 搜索 + AI 摘要

**Files:**
- Modify: `android/app/src/main/java/com/lumo/app/data/LumoRepository.kt`
- Modify: `android/app/src/main/java/com/lumo/app/ui/notes/NotesListScreen.kt`

**Interfaces:**
- Consumes: `bridge.ai_summarize_note(note_id: str) -> str`
- Consumes: `bridge.search_notes(query: str) -> list[dict]` — Kotlin 已有 `searchNotes`
- Produces: `LumoRepository.aiSummarizeNote(noteId: String): String`

- [ ] **Step 1: 在 LumoRepository 添加 aiSummarizeNote**

在 `LumoRepository.kt` 的 `// ── Notes ──` 区域添加：

```kotlin
    fun aiSummarizeNote(noteId: String): String {
        return bridge().callAttr("ai_summarize_note", noteId).toString()
    }
```

- [ ] **Step 2: 重写 NotesListScreen 添加搜索栏**

在 `NotesListScreen.kt` 的 `NotesListScreen` 函数中，在标题 Row 下方添加搜索栏，搜索时调用 `repo.searchNotes(query)` 展示结果。

在 `Column` 内 `Row`（标题 + FAB）之后、loading 判断之前插入：

```kotlin
        var searchQuery by remember { mutableStateOf("") }
        var searchResults by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
        val scope = rememberCoroutineScope()

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { query ->
                searchQuery = query
                scope.launch {
                    searchResults = if (query.isBlank()) emptyList()
                    else withContext(Dispatchers.IO) { repo.searchNotes(query) }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("搜索笔记...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = ""; searchResults = emptyList() }) {
                        Icon(Icons.Filled.Clear, contentDescription = "清除")
                    }
                }
            },
            singleLine = true
        )
```

然后在展示列表时，优先展示搜索结果：

```kotlin
        val displayNotes = if (searchQuery.isNotEmpty()) searchResults else notes
```

将后续的 `notes.isEmpty()` 判断和 `items(notes)` 改为 `displayNotes.isEmpty()` 和 `items(displayNotes)`。

需要添加 import：
```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
```

- [ ] **Step 3: 重写 NoteEditorScreen 添加 Markdown 预览 + AI 摘要**

将 `NoteEditorScreen` 替换为带预览/编辑切换的版本：

```kotlin
@Composable
private fun NoteEditorScreen(
    note: Map<String, String?>?,
    onSave: (String, String) -> Unit,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf(note?.get("title") ?: "") }
    var content by remember { mutableStateOf(note?.get("content") ?: "") }
    var previewMode by remember { mutableStateOf(false) }
    var summarizing by remember { mutableStateOf(false) }
    var summaryResult by remember { mutableStateOf("") }
    val repo = LumoRepository.get()
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                if (note == null) "新建笔记" else "编辑笔记",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Row {
                // 编辑/预览切换
                IconButton(onClick = { previewMode = !previewMode }) {
                    Icon(
                        if (previewMode) Icons.Filled.Edit else Icons.Filled.Visibility,
                        contentDescription = if (previewMode) "编辑" else "预览"
                    )
                }
                // AI 摘要（仅编辑已有笔记时可用）
                if (note != null) {
                    IconButton(onClick = {
                        scope.launch {
                            summarizing = true
                            try {
                                val summary = withContext(Dispatchers.IO) {
                                    repo.aiSummarizeNote(note["id"]!!)
                                }
                                content = if (content.isBlank()) summary else "$content\n\n---\n\n## AI 摘要\n\n$summary"
                            } catch (e: Exception) {
                                summaryResult = "错误: ${e.message}"
                            }
                            summarizing = false
                        }
                    }) {
                        if (summarizing) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        else Icon(Icons.Filled.AutoAwesome, contentDescription = "AI 摘要")
                    }
                }
                TextButton(onClick = { if (title.isNotBlank()) onSave(title, content) }) {
                    Text("保存")
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("标题") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (previewMode) {
            MarkdownRenderer(
                content = content,
                modifier = Modifier.fillMaxWidth().weight(1f),
                isDarkTheme = false,
            )
        } else {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
                placeholder = { Text("内容（支持 Markdown）") }
            )
        }

        if (summaryResult.isNotEmpty()) {
            Text(summaryResult, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}
```

需要添加 import：
```kotlin
import com.lumo.app.ui.markdown.MarkdownRenderer
import androidx.compose.material.icons.automirrored.filled.Edit
```

注意：`Icons.Filled.Edit` 在 Material Icons 中可能需要 `AutoMirrored`。如果编译报错，改用 `Icons.Filled.Edit`（不带 AutoMirrored 前缀的已废弃但仍可用）。先尝试不带 AutoMirrored。

- [ ] **Step 4: 构建并验证**

Run: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && export ANDROID_SDK_ROOT="/Users/hhl/Library/Android/sdk" && cd android && ./gradlew :app:assembleDebug --no-daemon 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/lumo/app/data/LumoRepository.kt android/app/src/main/java/com/lumo/app/ui/notes/NotesListScreen.kt
git commit -m "feat: note markdown preview, search, and AI summarize"
```

---

## Task 5: AI 计划生成

**Files:**
- Modify: `android/app/src/main/java/com/lumo/app/data/LumoRepository.kt`
- Modify: `android/app/src/main/java/com/lumo/app/ui/profile/ProfileScreen.kt`

**Interfaces:**
- Consumes: `bridge.generate_plan(goal: str, daily_minutes: int, week_num: int = 1) -> dict`
- Produces: `LumoRepository.generatePlan(goal: String, dailyMinutes: Int, weekNum: Int): Map<String, Any?>`

- [ ] **Step 1: 在 LumoRepository 添加 generatePlan**

在 `LumoRepository.kt` 的 `// ── Plans ──` 区域添加：

```kotlin
    fun generatePlan(goal: String, dailyMinutes: Int, weekNum: Int = 1): Map<String, Any?> {
        val result = bridge().callAttr("generate_plan", goal, dailyMinutes, weekNum)
        return result.asMap().entries.associate { (k, v) ->
            k.toString() to (v as? Any)
        }
    }
```

- [ ] **Step 2: 重写 CreatePlanScreen 支持自然语言输入**

在 `ProfileScreen.kt` 中，将 `CreatePlanScreen` 替换为：

```kotlin
@Composable
private fun CreatePlanScreen(
    onSave: (String, String, Int, String, String) -> Unit,
    onBack: () -> Unit
) {
    var goal by remember { mutableStateOf("") }
    var dailyMinutes by remember { mutableStateOf("60") }
    var generating by remember { mutableStateOf(false) }
    var genResult by remember { mutableStateOf("") }
    val repo = LumoRepository.get()
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("新建学习计划", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            TextButton(
                onClick = {
                    if (goal.isNotBlank() && !generating) {
                        scope.launch {
                            generating = true
                            genResult = "AI 正在生成计划..."
                            try {
                                val mins = dailyMinutes.toIntOrNull() ?: 60
                                val result = withContext(Dispatchers.IO) {
                                    repo.generatePlan(goal, mins)
                                }
                                val planId = result["plan_id"]
                                genResult = "计划已生成！${result.get("weeks", "")} 周大纲已创建"
                                generating = false
                                // 刷新列表
                                onSave(result.get("plan_id", "").toString(), goal, mins, "", "")
                            } catch (e: Exception) {
                                genResult = "错误: ${e.message}"
                                generating = false
                            }
                        }
                    }
                },
                enabled = !generating && goal.isNotBlank()
            ) {
                if (generating) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                else Text("生成")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = goal,
            onValueChange = { goal = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("学习目标") },
            placeholder = { Text("如：2 个月学会前端基础，每天 1 小时") },
            minLines = 2
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = dailyMinutes,
            onValueChange = { dailyMinutes = it.filter { c -> c.isDigit() } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("每日学习时长（分钟）") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))
        if (genResult.isNotEmpty()) {
            Text(genResult, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

- [ ] **Step 3: 构建并验证**

Run: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && export ANDROID_SDK_ROOT="/Users/hhl/Library/Android/sdk" && cd android && ./gradlew :app:assembleDebug --no-daemon 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/lumo/app/data/LumoRepository.kt android/app/src/main/java/com/lumo/app/ui/profile/ProfileScreen.kt
git commit -m "feat: AI plan generation with natural language input"
```

---

## Task 6: 答题交互 + AI 生成测验

**Files:**
- Modify: `android/app/src/main/java/com/lumo/app/data/LumoRepository.kt`
- Modify: `android/app/src/main/java/com/lumo/app/ui/quiz/QuizScreen.kt`

**Interfaces:**
- Consumes: `bridge.generate_quiz(knowledge_points: str, num_questions: int = 3, plan_id: str = "", task_id: str = "") -> dict`
- Consumes: `bridge.grade_answer(question_id: str, user_answer: str) -> dict` — Kotlin 已有 `gradeAnswer`
- Produces: `LumoRepository.generateQuiz(knowledgePoints: String, numQuestions: Int, planId: String, taskId: String): Map<String, Any?>`

- [ ] **Step 1: 在 LumoRepository 添加 generateQuiz**

在 `LumoRepository.kt` 的 `// ── Quiz ──` 区域添加：

```kotlin
    fun generateQuiz(knowledgePoints: String, numQuestions: Int = 3, planId: String = "", taskId: String = ""): Map<String, Any?> {
        val result = bridge().callAttr("generate_quiz", knowledgePoints, numQuestions, planId, taskId)
        return result.asMap().entries.associate { (k, v) ->
            k.toString() to (v as? Any)
        }
    }
```

- [ ] **Step 2: 重写 QuizScreen 支持答题交互和测验生成**

将整个 `QuizScreen.kt` 替换为：

```kotlin
package com.lumo.app.ui.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumo.app.data.LumoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen() {
    val repo = LumoRepository.get()
    var questions by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
    var errors by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    // 答题状态
    var answeringId by remember { mutableStateOf<String?>(null) }
    var selectedAnswer by remember { mutableStateOf("") }
    var gradeResult by remember { mutableStateOf<Map<String, Any?>?>(null) }

    // 生成测验状态
    var showGenDialog by remember { mutableStateOf(false) }
    var genTopic by remember { mutableStateOf("") }
    var genCount by remember { mutableStateOf("3") }
    var generating by remember { mutableStateOf(false) }
    var genResult by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            loading = true
            try {
                questions = withContext(Dispatchers.IO) { repo.getQuizQuestions() }
                errors = withContext(Dispatchers.IO) { repo.getQuizErrors() }
            } catch (e: Exception) {}
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("题库") },
                actions = {
                    IconButton(onClick = { showGenDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "生成测验")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("题目 (${questions.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("错题本 (${errors.size})") })
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val list = if (tab == 0) questions else errors
                if (list.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (tab == 0) "暂无题目\n点击 + 生成测验" else "暂无错题",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(list) { item ->
                            val qid = item["id"]
                            val isAnswering = answeringId == qid
                            val options = parseOptions(item["options"])
                            val qType = item["question_type"] ?: "single_choice"

                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(item["question"] ?: "", fontWeight = FontWeight.Medium)

                                    if (tab == 1) {
                                        // 错题展示
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("你的答案: ${item["user_answer"] ?: ""}",
                                            fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                                        Text("正确答案: ${item["correct_answer"] ?: item["answer"] ?: ""}",
                                            fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                        if (!item["error_reason"].isNullOrEmpty()) {
                                            Text("原因: ${item["error_reason"]}",
                                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    if (!item["explanation"].isNullOrEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(item["explanation"]!!,
                                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    // 答题区域（仅题目 tab）
                                    if (tab == 0 && qid != null) {
                                        if (qType == "short_answer") {
                                            if (isAnswering) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                OutlinedTextField(
                                                    value = selectedAnswer,
                                                    onValueChange = { selectedAnswer = it },
                                                    label = { Text("输入答案") },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Row {
                                                    TextButton(onClick = {
                                                        scope.launch {
                                                            val result = withContext(Dispatchers.IO) {
                                                                repo.gradeAnswer(qid, selectedAnswer)
                                                            }
                                                            gradeResult = result
                                                        }
                                                    }) { Text("提交") }
                                                    TextButton(onClick = {
                                                        answeringId = null
                                                        selectedAnswer = ""
                                                        gradeResult = null
                                                    }) { Text("取消") }
                                                }
                                            } else {
                                                TextButton(onClick = {
                                                    answeringId = qid
                                                    selectedAnswer = ""
                                                    gradeResult = null
                                                }) { Text("答题") }
                                            }
                                        } else {
                                            // 客观题：展示选项
                                            Spacer(modifier = Modifier.height(8.dp))
                                            options.forEachIndexed { idx, opt ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth()
                                                        .padding(vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    RadioButton(
                                                        selected = selectedAnswer == idx.toString(),
                                                        enabled = !isAnswering || gradeResult == null,
                                                        onClick = {
                                                            selectedAnswer = idx.toString()
                                                            answeringId = qid
                                                        }
                                                    )
                                                    Text(opt, fontSize = 14.sp)
                                                }
                                            }
                                            if (isAnswering && selectedAnswer.isNotEmpty() && gradeResult == null) {
                                                Button(onClick = {
                                                    scope.launch {
                                                        val answerText = options.getOrNull(selectedAnswer.toIntOrNull() ?: -1) ?: selectedAnswer
                                                        val result = withContext(Dispatchers.IO) {
                                                            repo.gradeAnswer(qid, answerText)
                                                        }
                                                        gradeResult = result
                                                        // 刷新错题本
                                                        refresh()
                                                    }
                                                }) { Text("提交答案") }
                                            }
                                        }

                                        // 判分结果
                                        gradeResult?.let { result ->
                                            Spacer(modifier = Modifier.height(8.dp))
                                            val correct = result["is_correct"]
                                            val isCorrect = correct == true || correct.toString() == "True"
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isCorrect) MaterialTheme.colorScheme.primaryContainer
                                                    else MaterialTheme.colorScheme.errorContainer
                                                )
                                            ) {
                                                Text(
                                                    if (isCorrect) "✓ 正确" else "✗ 错误",
                                                    modifier = Modifier.padding(8.dp),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            result["explanation"]?.let { exp ->
                                                Text(exp.toString(), fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 生成测验对话框
    if (showGenDialog) {
        AlertDialog(
            onDismissRequest = { showGenDialog = false },
            title = { Text("生成测验") },
            text = {
                Column {
                    OutlinedTextField(
                        value = genTopic,
                        onValueChange = { genTopic = it },
                        label = { Text("知识点 / 主题") },
                        placeholder = { Text("如：Python 闭包") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = genCount,
                        onValueChange = { genCount = it.filter { c -> c.isDigit() } },
                        label = { Text("题目数量") },
                        singleLine = true
                    )
                    if (genResult.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(genResult, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (genTopic.isNotBlank() && !generating) {
                            scope.launch {
                                generating = true
                                genResult = "正在生成..."
                                try {
                                    val count = genCount.toIntOrNull() ?: 3
                                    withContext(Dispatchers.IO) {
                                        repo.generateQuiz(genTopic, count)
                                    }
                                    genResult = "已生成 $count 道题"
                                    showGenDialog = false
                                    genTopic = ""
                                    refresh()
                                } catch (e: Exception) {
                                    genResult = "错误: ${e.message}"
                                }
                                generating = false
                            }
                        }
                    },
                    enabled = !generating && genTopic.isNotBlank()
                ) {
                    if (generating) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    else Text("生成")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGenDialog = false }) { Text("取消") }
            }
        )
    }
}

/** Parse options JSON string into a list of option strings. */
private fun parseOptions(optionsJson: String?): List<String> {
    if (optionsJson.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(optionsJson)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}
```

- [ ] **Step 3: 构建并验证**

Run: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && export ANDROID_SDK_ROOT="/Users/hhl/Library/Android/sdk" && cd android && ./gradlew :app:assembleDebug --no-daemon 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/lumo/app/data/LumoRepository.kt android/app/src/main/java/com/lumo/app/ui/quiz/QuizScreen.kt
git commit -m "feat: quiz answering UI with grading and AI quiz generation"
```

---

## Task 7: 番茄钟 + 打卡

**Files:**
- Modify: `android/app/src/main/java/com/lumo/app/ui/today/TodayScreen.kt`

**Interfaces:**
- Consumes: `LumoRepository.recordPomodoro(taskId: String, planId: String, durationSec: Int, startedAt: String)` — 已存在
- Consumes: `LumoRepository.checkinToday(taskIds: List<String>)` — 已存在
- Consumes: `LumoRepository.getTodayTasks(): List<Map<String, String?>>` — 已存在

- [ ] **Step 1: 在 TodayScreen 添加番茄钟和打卡**

在 `TodayScreen.kt` 文件顶部添加 import：

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
```

在 `TodayScreen` 函数中，在 `Column` 内 `StatCard` Row 之后、`Text("今日任务")` 之前插入番茄钟区域：

```kotlin
        // 番茄钟
        var pomodoroRunning by remember { mutableStateOf(false) }
        var pomodoroSeconds by remember { mutableStateOf(25 * 60) }
        var pomodoroDuration by remember { mutableStateOf(25) } // minutes
        var pomodoroTaskId by remember { mutableStateOf<String?>(null) }
        var pomodoroPlanId by remember { mutableStateOf<String?>(null) }
        var pomodoroStartedAt by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val mins = pomodoroSeconds / 60
                val secs = pomodoroSeconds % 60
                Text(
                    String.format("%02d:%02d", mins, secs),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15 to "15+5", 25 to "25+5", 50 to "50+10").forEach { (mins, label) ->
                        FilterChip(
                            selected = pomodoroDuration == mins,
                            onClick = {
                                if (!pomodoroRunning) {
                                    pomodoroDuration = mins
                                    pomodoroSeconds = mins * 60
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (pomodoroRunning) {
                                // 停止
                                pomodoroRunning = false
                            } else {
                                // 开始
                                pomodoroRunning = true
                                pomodoroStartedAt = java.time.Instant.now().toString()
                                scope.launch {
                                    while (pomodoroRunning && pomodoroSeconds > 0) {
                                        delay(1000)
                                        if (pomodoroRunning) {
                                            pomodoroSeconds--
                                            if (pomodoroSeconds <= 0) {
                                                pomodoroRunning = false
                                                // 记录番茄钟
                                                val taskId = pomodoroTaskId
                                                val planId = pomodoroPlanId
                                                if (taskId != null && planId != null) {
                                                    withContext(Dispatchers.IO) {
                                                        repo.recordPomodoro(
                                                            taskId, planId,
                                                            pomodoroDuration * 60, pomodoroStartedAt
                                                        )
                                                    }
                                                    totalStudy = repo.getTotalStudyTime()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(
                            if (pomodoroRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (pomodoroRunning) "暂停" else "开始")
                    }
                    if (pomodoroRunning) {
                        OutlinedButton(onClick = {
                            pomodoroRunning = false
                            pomodoroSeconds = pomodoroDuration * 60
                        }) { Text("重置") }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
```

- [ ] **Step 2: 在 TaskCard 上关联番茄钟和添加打卡按钮**

修改 `TaskCard` 调用处，传入番茄钟设置回调。在 `items(planTasks) { task ->` 块中：

将：
```kotlin
                    items(planTasks) { task ->
                        TaskCard(task) { taskId ->
                            val newStatus = if (task["status"] == "completed") "pending" else "completed"
                            repo.updateTaskStatus(taskId, newStatus)
                            tasks = repo.getTodayTasks()
                        }
                    }
```

替换为：
```kotlin
                    items(planTasks) { task ->
                        TaskCard(
                            task = task,
                            onComplete = { taskId ->
                                val newStatus = if (task["status"] == "completed") "pending" else "completed"
                                repo.updateTaskStatus(taskId, newStatus)
                                tasks = repo.getTodayTasks()
                            },
                            onStartPomodoro = { taskId, planId ->
                                pomodoroTaskId = taskId
                                pomodoroPlanId = planId
                            }
                        )
                    }
```

修改 `TaskCard` 函数签名和内部添加番茄钟按钮：

```kotlin
@Composable
private fun TaskCard(
    task: Map<String, String?>,
    onComplete: (String) -> Unit,
    onStartPomodoro: (String, String) -> Unit = { _, _ -> }
) {
    val taskId = task["id"] ?: return
    val isCompleted = task["status"] == "completed"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isCompleted,
                onCheckedChange = { onComplete(taskId) }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task["title"] ?: "",
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                )
                if (!task["description"].isNullOrEmpty()) {
                    Text(
                        task["description"]!!,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
            // 番茄钟按钮
            if (!isCompleted) {
                val planId = task["plan_id"] ?: ""
                IconButton(onClick = { onStartPomodoro(taskId, planId) }) {
                    Icon(Icons.Filled.Timer, contentDescription = "番茄钟")
                }
            }
        }
    }
}
```

- [ ] **Step 3: 添加打卡按钮**

在 `TodayScreen` 的 `Column` 内，任务列表 `LazyColumn` 之后（`Column` 末尾）添加：

```kotlin
        if (tasks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            val completedTaskIds = tasks.filter { it["status"] == "completed" }.mapNotNull { it["id"] }
            Button(
                onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { repo.checkinToday(completedTaskIds) }
                            streak = repo.getStreak()
                        } catch (e: Exception) {}
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = completedTaskIds.isNotEmpty()
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("今日打卡")
            }
        }
```

- [ ] **Step 4: 构建并验证**

Run: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && export ANDROID_SDK_ROOT="/Users/hhl/Library/Android/sdk" && cd android && ./gradlew :app:assembleDebug --no-daemon 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/lumo/app/ui/today/TodayScreen.kt
git commit -m "feat: pomodoro timer, task association, and daily check-in"
```

---

## Task 8: 学习统计（趋势图 + 掌握度 + 热力图）

**Files:**
- Modify: `android/app/src/main/java/com/lumo/app/data/LumoRepository.kt`
- Modify: `android/app/src/main/java/com/lumo/app/ui/profile/ProfileScreen.kt`

**Interfaces:**
- Consumes: `bridge.get_study_trend(period: str = "week") -> dict`
- Consumes: `bridge.get_knowledge_mastery(plan_id: str) -> list[dict]`
- Consumes: `bridge.get_checkin_heatmap(month: str) -> list[dict]` — Kotlin 已有 `getCheckinHeatmap`
- Produces: `LumoRepository.getStudyTrend(period: String): Map<String, Any?>`
- Produces: `LumoRepository.getKnowledgeMastery(planId: String): List<Map<String, String?>>`

- [ ] **Step 1: 在 LumoRepository 添加方法**

在 `LumoRepository.kt` 的 `// ── Stats ──` 区域添加：

```kotlin
    fun getStudyTrend(period: String = "week"): Map<String, Any?> {
        val result = bridge().callAttr("get_study_trend", period)
        return result.asMap().entries.associate { (k, v) ->
            k.toString() to (v as? Any)
        }
    }

    fun getKnowledgeMastery(planId: String): List<Map<String, String?>> {
        return bridge().callAttr("get_knowledge_mastery", planId).toStringMapList()
    }
```

- [ ] **Step 2: 在 ProfileScreen 添加统计展示**

在 `ProfileScreen` 函数的 `LazyColumn` 中，在 "学习统计" section 的 Card 内替换为更丰富的统计展示：

将现有的 stats section `item { ... }` 替换为：

```kotlin
                // Stats section
                item {
                    SectionHeader("学习统计")
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            val streak = stats["streak"]
                            val totalTime = stats["total_study_time"] as? Int ?: 0
                            Text("连续打卡: $streak 天")
                            Text("总学习时长: ${totalTime / 60} 分钟")

                            // 学习趋势（近 7 天）
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("近 7 天学习时长", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            var trend by remember { mutableStateOf<Map<String, Any?>?>(null) }
                            LaunchedEffect(Unit) {
                                try { trend = withContext(Dispatchers.IO) { repo.getStudyTrend("week") } } catch (e: Exception) {}
                            }
                            trend?.let { t ->
                                @Suppress("UNCHECKED_CAST")
                                val data = t["data"] as? Map<String, Any> ?: emptyMap()
                                val maxVal = (data.values.maxOrNull() as? Int ?: 1).coerceAtLeast(1)
                                data.entries.toList().takeLast(7).forEach { (date, seconds) ->
                                    val mins = (seconds as? Int ?: 0) / 60
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(date.takeLast(5), fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        // 简易条形图
                                        val barWidth = (mins.toFloat() / maxVal.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        Box(modifier = Modifier
                                            .width((barWidth * 120).toInt().dp.coerceAtLeast(2.dp))
                                            .height(8.dp)
                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
                                        Text("${mins}m", fontSize = 11.sp)
                                    }
                                }
                            }

                            // 打卡热力图（当月）
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("本月打卡", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            var heatmap by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
                            LaunchedEffect(Unit) {
                                try {
                                    val month = java.time.LocalDate.now().toString().take(7)
                                    heatmap = withContext(Dispatchers.IO) { repo.getCheckinHeatmap(month) }
                                } catch (e: Exception) {}
                            }
                            Text("打卡 ${heatmap.size} 天", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)

                            // 知识点掌握度
                            if (plans.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("知识点掌握度", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                var mastery by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
                                LaunchedEffect(plans) {
                                    try {
                                        mastery = withContext(Dispatchers.IO) {
                                            repo.getKnowledgeMastery(plans.first()["id"]!!)
                                        }
                                    } catch (e: Exception) {}
                                }
                                mastery.forEach { kp ->
                                    val level = kp["mastery_level"] ?: "0"
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(kp["name"] ?: "", fontSize = 12.sp)
                                        LinearProgressIndicator(
                                            progress = { (level.toFloatOrNull() ?: 0f) / 100f },
                                            modifier = Modifier.width(100.dp)
                                        )
                                        Text("$level%", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
```

需要添加 import：
```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
```

注意：`RoundedCornerShape` 和 `background` 可能已经通过 `import androidx.compose.foundation.layout.*` 和 `import androidx.compose.material3.*` 间接导入。如果编译报错，显式添加。

- [ ] **Step 3: 构建并验证**

Run: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && export ANDROID_SDK_ROOT="/Users/hhl/Library/Android/sdk" && cd android && ./gradlew :app:assembleDebug --no-daemon 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/lumo/app/data/LumoRepository.kt android/app/src/main/java/com/lumo/app/ui/profile/ProfileScreen.kt
git commit -m "feat: study trend chart, knowledge mastery bars, check-in heatmap"
```

---

## Task 9: 深色主题适配

**Files:**
- Modify: `android/app/src/main/java/com/lumo/app/ui/chat/ChatListScreen.kt`
- Modify: `android/app/src/main/java/com/lumo/app/ui/notes/NotesListScreen.kt`

- [ ] **Step 1: ChatDetailScreen 跟随系统暗色模式**

在 `ChatListScreen.kt` 的 `ChatDetailScreen` 函数中，将 `MarkdownRenderer` 调用中的 `isDarkTheme = false` 改为：

```kotlin
                        MarkdownRenderer(
                            content = msg["content"] ?: "",
                            modifier = Modifier.padding(12.dp),
                            isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme(),
                        )
```

- [ ] **Step 2: NoteEditorScreen 跟随系统暗色模式**

在 `NotesListScreen.kt` 的 `NoteEditorScreen` 函数中，将 `MarkdownRenderer` 调用中的 `isDarkTheme = false` 改为：

```kotlin
            MarkdownRenderer(
                content = content,
                modifier = Modifier.fillMaxWidth().weight(1f),
                isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme(),
            )
```

- [ ] **Step 3: 构建并验证**

Run: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && export ANDROID_SDK_ROOT="/Users/hhl/Library/Android/sdk" && cd android && ./gradlew :app:assembleDebug --no-daemon 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/lumo/app/ui/chat/ChatListScreen.kt android/app/src/main/java/com/lumo/app/ui/notes/NotesListScreen.kt
git commit -m "feat: dark theme support for markdown renderer"
```

---

## Task 10: 最终构建 + 安装 + 手动测试

- [ ] **Step 1: 完整构建**

Run: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && export ANDROID_SDK_ROOT="/Users/hhl/Library/Android/sdk" && cd android && ./gradlew :app:assembleDebug --no-daemon 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 卸载旧版并安装**

Run:
```bash
ADB=/Users/hhl/Library/Android/sdk/platform-tools/adb
$ADB -s emulator-5554 uninstall com.lumo.app
$ADB -s emulator-5554 install android/app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 3: 启动 app 并设置 provider config**

Run:
```bash
ADB=/Users/hhl/Library/Android/sdk/platform-tools/adb
$ADB -s emulator-5554 shell am start -n com.lumo.app/.MainActivity
sleep 3
$ADB -s emulator-5554 shell "run-as com.lumo.app sqlite3 files/lumo.db \"INSERT INTO settings(key,value) VALUES('provider_config','{\\\"provider_type\\\": \\\"openai\\\", \\\"api_key\\\": \\\"${OPENAI_API_KEY}\\\", \\\"base_url\\\": \\\"${OPENAI_API_BASE}\\\", \\\"model\\\": \\\"${OPENAI_MODEL}\\\"}');\""
```

- [ ] **Step 4: 手动验证清单**

验证每个功能：
1. 对话：流式输出逐字出现 ✓
2. 对话：Send 按钮在生成中变为 Stop，点击可中断 ✓
3. 对话：搜索栏可搜索历史消息 ✓
4. 对话：会话可删除 ✓
5. 对话：TopAppBar 有保存为笔记按钮 ✓
6. 笔记：编辑器有预览/编辑切换 ✓
7. 笔记：可搜索笔记 ✓
8. 笔记：AI 摘要按钮可生成摘要 ✓
9. 我的：新建计划可用自然语言输入目标 ✓
10. 题库：可答题并判分 ✓
11. 题库：可生成测验 ✓
12. 今日：番茄钟可计时 ✓
13. 今日：打卡按钮可打卡 ✓
14. 我的：学习趋势图展示 ✓
15. 我的：知识点掌握度展示 ✓
16. 我的：打卡热力图展示 ✓

- [ ] **Step 5: 最终 commit**

```bash
git add -A
git commit -m "feat: complete P0 UX — all 7 modules fully accessible"
```

---

## Self-Review

### Spec coverage check

| Spec 模块 | Spec 要求 | 对应 Task | 状态 |
|---|---|---|---|
| 1. 对话 | 流式输出 | Task 1 | ✅ |
| 1. 对话 | 对话搜索 | Task 2 | ✅ |
| 1. 对话 | 对话删除 | Task 2 | ✅ |
| 1. 对话 | 保存为笔记 | Task 3 | ✅ |
| 1. 对话 | 快捷提问 | 已有 | ✅ |
| 1. 对话 | Markdown 渲染 | 已有 | ✅ |
| 2. 计划 | AI 计划生成 | Task 5 | ✅ |
| 2. 计划 | 多计划并行 | 已有 | ✅ |
| 2. 计划 | 时间轴可视化 | — | ❌ 未覆盖（改动量大，留后续） |
| 2. 计划 | 任务编辑 | — | ❌ 未覆盖（改动量大，留后续） |
| 3. 任务流 | 今日任务看板 | 已有 | ✅ |
| 3. 任务流 | 番茄钟 | Task 7 | ✅ |
| 3. 任务流 | 打卡 | Task 7 | ✅ |
| 3. 任务流 | 待复习状态 | — | ❌ 未覆盖（需 Python 改动） |
| 4. 测验 | AI 生成测验 | Task 6 | ✅ |
| 4. 测验 | 答题界面 | Task 6 | ✅ |
| 4. 测验 | 即时判分 | Task 6 | ✅ |
| 4. 测验 | 错题本 | 已有 | ✅ |
| 5. 笔记 | Markdown 预览 | Task 4 | ✅ |
| 5. 笔记 | 笔记搜索 | Task 4 | ✅ |
| 5. 笔记 | AI 摘要 | Task 4 | ✅ |
| 6. 统计 | 热力图 | Task 8 | ✅ |
| 6. 统计 | Streak | 已有 | ✅ |
| 6. 统计 | 趋势图 | Task 8 | ✅ |
| 6. 统计 | 知识点掌握度 | Task 8 | ✅ |
| 7. 配置 | provider 配置 | 已有 | ✅ |
| 7. 配置 | 连接测试 | 已有 | ✅ |
| 全局 | 深色主题 | Task 9 | ✅ |

### 未覆盖项说明

以下 3 项因改动量大或需修改 Python 层，留作后续：
1. **计划时间轴可视化** — 需要新的 Compose 自定义布局组件，改动量大
2. **任务编辑（排序/跳过/延后/编辑/新增/删除）** — 需要完整的任务管理页面 + 拖拽排序，改动量大
3. **待复习状态** — 需要修改 Python `grade_answer` 在答错时自动更新关联任务状态为 `review`，违反"不修改 Python"约束

### Placeholder scan

无 TBD / TODO / "implement later" / "add appropriate error handling" 等。所有步骤包含完整代码。

### Type consistency

- `LumoRepository.streamChat(text: String, onToken: (String) -> Unit): String` — 在 Task 1 定义和使用
- `LumoRepository.saveConversationAsNote(sessionId: String, title: String): String` — 在 Task 3 定义和使用
- `LumoRepository.aiSummarizeNote(noteId: String): String` — 在 Task 4 定义和使用
- `LumoRepository.generatePlan(goal: String, dailyMinutes: Int, weekNum: Int): Map<String, Any?>` — 在 Task 5 定义和使用
- `LumoRepository.generateQuiz(knowledgePoints: String, numQuestions: Int, planId: String, taskId: String): Map<String, Any?>` — 在 Task 6 定义和使用
- `LumoRepository.getStudyTrend(period: String): Map<String, Any?>` — 在 Task 8 定义和使用
- `LumoRepository.getKnowledgeMastery(planId: String): List<Map<String, String?>>` — 在 Task 8 定义和使用
