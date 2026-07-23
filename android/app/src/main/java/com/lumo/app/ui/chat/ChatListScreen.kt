package com.lumo.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    var quickPrompts by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var chatStarted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sessionId) {
        try {
            messages = repo.getMessages(sessionId)
            quickPrompts = repo.getQuickPrompts()
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
                                withContext(Dispatchers.IO) {
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

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                val isUser = msg["role"] == "user"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    if (isUser) {
                        Text(
                            msg["content"] ?: "",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
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
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        if (messages.isNotEmpty() && !loading) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                quickPrompts.take(4).forEach { prompt ->
                    AssistChip(onClick = { inputText = prompt["prompt"] ?: "" },
                        label = { Text(prompt["label"] ?: "", fontSize = 12.sp) })
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
    }
}
