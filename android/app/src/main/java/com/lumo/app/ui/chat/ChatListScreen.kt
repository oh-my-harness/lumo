package com.lumo.app.ui.chat

import androidx.compose.foundation.clickable
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
import androidx.navigation.NavController
import com.lumo.app.data.LumoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(navController: NavController) {
    val repo = LumoRepository.get()
    var sessions by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

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

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(session["title"]?.ifEmpty { "新对话" } ?: "新对话", fontWeight = FontWeight.Medium)
                            Text(session["updated_at"]?.take(10) ?: "", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("对话") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )

        LazyColumn(
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
                    Text(
                        msg["content"] ?: "",
                        modifier = Modifier.padding(12.dp),
                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
            IconButton(onClick = {
                if (inputText.isNotBlank() && !loading) {
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
                            val response = withContext(Dispatchers.IO) { repo.sendMessage(text) }
                            messages = messages + mapOf("role" to "assistant", "content" to response)
                        } catch (e: Exception) {
                            messages = messages + mapOf("role" to "assistant", "content" to "错误: ${e.message}")
                        }
                        loading = false
                    }
                }
            }) { Icon(Icons.Filled.Send, contentDescription = "发送") }
        }
    }
}
