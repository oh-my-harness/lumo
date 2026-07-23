package com.lumo.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.navigation.NavController
import com.lumo.app.data.LumoRepository
import com.lumo.app.ui.markdown.MarkdownRenderer
import com.lumo.app.ui.quiz.QuizCardOverlay
import com.lumo.app.ui.components.LumoChatBubble
import com.lumo.app.ui.components.LumoTypingIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(navController: NavController) {
    val repo = LumoRepository.get()
    var sessions by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
    val scope = rememberCoroutineScope()

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
            Text("对话", fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                                scope.launch {
                                    withContext(Dispatchers.IO) { repo.deleteSession(session["id"]!!) }
                                    sessions = withContext(Dispatchers.IO) { repo.listSessions() }
                                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(sessionId: String, navController: NavController) {
    val repo = LumoRepository.get()
    var messages by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var chatStarted by remember { mutableStateOf(false) }
    var quickPrompts by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    // Quiz overlay state
    var quizQuestions by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
    var showQuiz by remember { mutableStateOf(false) }
    var generatingQuiz by remember { mutableStateOf(false) }
    var quizTopic by remember { mutableStateOf("") }
    var showQuizGenDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sessionId) {
        try {
            messages = repo.getMessages(sessionId)
            quickPrompts = repo.getQuickPrompts()
            // If messages exist, chat was already started (e.g. via task context)
            if (messages.isNotEmpty()) chatStarted = true
        } catch (e: Exception) {}
    }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, messages.lastOrNull()?.get("content")) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
    var showSaveNoteDialog by remember { mutableStateOf(false) }
    var savingNote by remember { mutableStateOf(false) }
    var noteSaved by remember { mutableStateOf(false) }

        TopAppBar(
            title = { Text("对话") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = { showQuizGenDialog = true }) {
                    Icon(Icons.Filled.Quiz, contentDescription = "出题")
                }
                IconButton(
                    onClick = { if (!savingNote) showSaveNoteDialog = true },
                    enabled = !savingNote
                ) {
                    Icon(
                        if (noteSaved) Icons.Filled.CheckCircle else Icons.Filled.NoteAdd,
                        contentDescription = "保存为笔记",
                        tint = if (noteSaved) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )

        if (showSaveNoteDialog) {
            AlertDialog(
                onDismissRequest = { if (!savingNote) showSaveNoteDialog = false },
                title = { Text("保存为笔记") },
                text = {
                    if (savingNote) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI 正在总结对话内容...")
                        }
                    } else {
                        Text(if (noteSaved) "该对话已有笔记，是否更新内容？" else "是否让 AI 总结这段对话并保存为笔记？")
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (savingNote) return@TextButton
                            scope.launch {
                                savingNote = true
                                try {
                                    withContext(Dispatchers.IO) {
                                        repo.saveConversationAsNote(sessionId)
                                    }
                                    noteSaved = true
                                    showSaveNoteDialog = false
                                } catch (e: Exception) {
                                    // show error in dialog
                                }
                                savingNote = false
                            }
                        },
                        enabled = !savingNote
 ) {
                        if (savingNote) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        else Text(if (noteSaved) "更新" else "保存")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { if (!savingNote) showSaveNoteDialog = false },
                        enabled = !savingNote
                    ) { Text("取消") }
                }
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                val isUser = msg["role"] == "user"
                if (isUser) {
                    LumoChatBubble(
                        message = msg["content"] ?: "",
                        isSent = true,
                    )
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(0.85f),
                        shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        MarkdownRenderer(
                            content = msg["content"] ?: "",
                            modifier = Modifier.padding(12.dp),
                            isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme(),
                        )
                    }
                }
            }
            if (loading) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        LumoTypingIndicator()
                    }
                }
            }
        }

        if (messages.isNotEmpty() && !loading) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(quickPrompts) { prompt ->
                    AssistChip(
                        onClick = { inputText = prompt["prompt"] ?: "" },
                        label = { Text(prompt["label"] ?: "") }
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = inputText, onValueChange = { inputText = it },
                modifier = Modifier.weight(1f), placeholder = { Text("输入消息...") }, maxLines = 3
            )
            Spacer(modifier = Modifier.width(8.dp))
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
                        var aiIndex = -1
                        try {
                            if (!chatStarted) {
                                withContext(Dispatchers.IO) { repo.startChat(sessionId) }
                                chatStarted = true
                            }
                            messages = messages + mapOf("role" to "user", "content" to text)
                            // 流式：先插入空的 AI 抂点，逐 token 更新
                            aiIndex = messages.size
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
                            if (aiIndex >= 0 && aiIndex < messages.size) {
                                messages = messages.toMutableList().also {
                                    it[aiIndex] = it[aiIndex].toMutableMap().also { m ->
                                        m["content"] = "错误: ${e.message}"
                                    }
                                }
                            } else {
                                messages = messages + mapOf("role" to "assistant", "content" to "错误: ${e.message}")
                            }
                        }
                        loading = false
                    }
                }
            }) { Icon(sendIcon, contentDescription = if (loading) "停止" else "发送") }
        }

        // Quiz generation dialog
        if (showQuizGenDialog) {
            AlertDialog(
                onDismissRequest = { if (!generatingQuiz) showQuizGenDialog = false },
                title = { Text("出题") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = quizTopic,
                            onValueChange = { quizTopic = it },
                            label = { Text("知识点 / 主题") },
                            placeholder = { Text("如：Python 闭包") },
                            singleLine = true,
                            enabled = !generatingQuiz
                        )
                        if (generatingQuiz) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("AI 正在出题...", fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (quizTopic.isNotBlank() && !generatingQuiz) {
                                scope.launch {
                                    generatingQuiz = true
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            repo.generateQuiz(quizTopic, 3)
                                        }
                                        // Extract questions from result
                                        @Suppress("UNCHECKED_CAST")
                                        val qList = (result["questions"] as? List<*>)?.mapNotNull { q ->
                                            @Suppress("UNCHECKED_CAST")
                                            (q as? Map<String, Any?>)?.map { (k, v) ->
                                                k to v?.toString()
                                            }?.toMap()
                                        } ?: emptyList()
                                        if (qList.isNotEmpty()) {
                                            quizQuestions = qList
                                            showQuiz = true
                                            showQuizGenDialog = false
                                            quizTopic = ""
                                        }
                                    } catch (e: Exception) {
                                        // Fallback: load from quiz bank
                                        try {
                                            val allQ = withContext(Dispatchers.IO) { repo.getQuizQuestions() }
                                            if (allQ.isNotEmpty()) {
                                                quizQuestions = allQ.take(3)
                                                showQuiz = true
                                                showQuizGenDialog = false
                                                quizTopic = ""
                                            }
                                        } catch (e2: Exception) {}
                                    }
                                    generatingQuiz = false
                                }
                            }
                        },
                        enabled = !generatingQuiz && quizTopic.isNotBlank()
                    ) {
                        if (generatingQuiz) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        else Text("出题")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { if (!generatingQuiz) showQuizGenDialog = false },
                        enabled = !generatingQuiz
                    ) { Text("取消") }
                }
            )
        }

        // Full-screen quiz overlay
        if (showQuiz && quizQuestions.isNotEmpty()) {
            QuizCardOverlay(
                questions = quizQuestions,
                onExit = { showQuiz = false; quizQuestions = emptyList() }
            )
        }
    }
}
