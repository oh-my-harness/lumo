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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lumo.app.data.LumoRepository
import com.lumo.app.ui.markdown.MarkdownRenderer
import com.lumo.app.ui.quiz.QuizCardOverlay
import com.lumo.app.ui.components.LumoChatBubble
import com.lumo.app.ui.components.LumoTypingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(navController: NavController) {
    val vm: ChatListViewModel = viewModel(factory = ChatListViewModel.factory(LumoRepository.get()))
    val state by vm.uiState.collectAsState()

    LaunchedEffect(Unit) { vm.load() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("对话", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            FloatingActionButton(onClick = {
                val sid = vm.createSession()
                if (sid.isNotEmpty()) navController.navigate("chat/$sid")
            }) { Icon(Icons.Filled.Add, contentDescription = "新建对话") }
        }

        // 搜索栏
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { query -> vm.search(query) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("搜索对话内容...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { vm.search("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "清除")
                    }
                }
            },
            singleLine = true
        )

        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (state.searchQuery.isNotEmpty()) {
            // 搜索结果模式
            if (state.searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("未找到匹配内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    items(state.searchResults) { msg ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clickable { navController.navigate("chat/${msg.session_id}") },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(msg.content, maxLines = 2,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else if (state.sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("点击 + 开始新的对话", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(state.sessions) { session ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable { navController.navigate("chat/${session.id}") },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(session.title.ifEmpty { "新对话" }, fontWeight = FontWeight.Medium)
                                Text(session.updated_at.take(10), fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.deleteSession(session.id) }) {
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
    val vm: ChatDetailViewModel = viewModel(factory = ChatDetailViewModel.factory(LumoRepository.get()))
    val state by vm.uiState.collectAsState()

    LaunchedEffect(sessionId) { vm.load(sessionId) }

    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text("对话") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = { vm.setShowQuizGenDialog(true) }) {
                    Icon(Icons.Filled.Quiz, contentDescription = "出题")
                }
                IconButton(
                    onClick = { if (!state.savingNote) vm.setShowSaveNoteDialog(true) },
                    enabled = !state.savingNote
                ) {
                    Icon(
                        if (state.noteSaved) Icons.Filled.CheckCircle else Icons.Filled.NoteAdd,
                        contentDescription = "保存为笔记",
                        tint = if (state.noteSaved) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )

        if (state.showSaveNoteDialog) {
            AlertDialog(
                onDismissRequest = { if (!state.savingNote) vm.setShowSaveNoteDialog(false) },
                title = { Text("保存为笔记") },
                text = {
                    if (state.savingNote) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI 正在总结对话内容...")
                        }
                    } else {
                        Text(if (state.noteSaved) "该对话已有笔记，是否更新内容？" else "是否让 AI 总结这段对话并保存为笔记？")
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (state.savingNote) return@TextButton
                            if (!state.chatStarted) {
                                vm.startChatIfNeeded(sessionId) { vm.saveConversationAsNote(sessionId) }
                            } else {
                                vm.saveConversationAsNote(sessionId)
                            }
                        },
                        enabled = !state.savingNote
                    ) {
                        if (state.savingNote) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        else Text(if (state.noteSaved) "更新" else "保存")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { if (!state.savingNote) vm.setShowSaveNoteDialog(false) },
                        enabled = !state.savingNote
                    ) { Text("取消") }
                }
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.messages) { msg ->
                val isUser = msg.role == "user"
                val content = msg.content
                if (isUser) {
                    LumoChatBubble(
                        message = content,
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
                            content = content,
                            modifier = Modifier.padding(12.dp),
                            isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme(),
                        )
                    }
                }
            }
            if (state.loading) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        LumoTypingIndicator()
                    }
                }
            }
        }

        if (state.messages.isNotEmpty() && !state.loading) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(state.quickPrompts) { prompt ->
                    AssistChip(
                        onClick = { vm.updateInputText(prompt.prompt) },
                        label = { Text(prompt.label) }
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.inputText, onValueChange = { vm.updateInputText(it) },
                modifier = Modifier.weight(1f), placeholder = { Text("输入消息...", fontSize = 13.sp) }, maxLines = 2, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            val sendIcon = if (state.loading) Icons.Filled.Stop else Icons.Filled.Send
            IconButton(onClick = {
                if (state.loading) {
                    vm.abortChat()
                    return@IconButton
                }
                if (state.inputText.isNotBlank()) {
                    val text = state.inputText
                    vm.updateInputText("")
                    if (!state.chatStarted) {
                        vm.startChatIfNeeded(sessionId) { vm.streamMessage(text) }
                    } else {
                        vm.streamMessage(text)
                    }
                }
            }) { Icon(sendIcon, contentDescription = if (state.loading) "停止" else "发送") }
        }

        // Quiz generation dialog
        if (state.showQuizGenDialog) {
            AlertDialog(
                onDismissRequest = { if (!state.generatingQuiz) vm.setShowQuizGenDialog(false) },
                title = { Text("出题") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = state.quizTopic,
                            onValueChange = { vm.setQuizTopic(it) },
                            label = { Text("知识点 / 主题") },
                            placeholder = { Text("如：Python 闭包") },
                            singleLine = true,
                            enabled = !state.generatingQuiz
                        )
                        if (state.generatingQuiz) {
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
                            if (state.quizTopic.isNotBlank() && !state.generatingQuiz) {
                                vm.generateQuiz(state.quizTopic, 3)
                            }
                        },
                        enabled = !state.generatingQuiz && state.quizTopic.isNotBlank()
                    ) {
                        if (state.generatingQuiz) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        else Text("出题")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { if (!state.generatingQuiz) vm.setShowQuizGenDialog(false) },
                        enabled = !state.generatingQuiz
                    ) { Text("取消") }
                }
            )
        }

        // Full-screen quiz overlay
        if (state.showQuiz && state.quizQuestions.isNotEmpty()) {
            QuizCardOverlay(
                questions = state.quizQuestions,
                onExit = { vm.setShowQuiz(false) }
            )
        }
    }
}
