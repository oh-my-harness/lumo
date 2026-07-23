package com.lumo.app.ui.notes

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
import com.lumo.app.data.LumoRepository
import com.lumo.app.ui.components.LumoGradientButton
import com.lumo.app.ui.markdown.MarkdownRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(navController: androidx.navigation.NavController) {
    val repo = LumoRepository.get()
    var notes by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var summarizing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        try { notes = repo.listNotes() } catch (e: Exception) {}
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    if (selectionMode) "已选 ${selectedIds.size} 篇" else "笔记",
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                if (selectionMode) {
                    TextButton(onClick = {
                        selectedIds.clear()
                        for (n in notes) selectedIds.add(n["id"]!!)
                    }) { Text("全选") }
                    IconButton(onClick = {
                        selectedIds.clear()
                        selectionMode = false
                    }) { Icon(Icons.Filled.Close, contentDescription = "退出选择") }
                } else {
                    IconButton(onClick = {
                        selectionMode = true
                        if (notes.isNotEmpty()) selectedIds.add(notes.first()["id"]!!)
                    }) { Icon(Icons.Filled.Merge, contentDescription = "汇总笔记") }
                    FloatingActionButton(
                        onClick = { navController.navigate("notes/editor") },
                        modifier = Modifier.size(40.dp)
                    ) { Icon(Icons.Filled.Add, contentDescription = "新建笔记") }
                }
            }
        )

        var searchQuery by remember { mutableStateOf("") }
        var searchResults by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }

        if (!selectionMode) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { query ->
                    searchQuery = query
                    scope.launch {
                        try {
                            searchResults = if (query.isBlank()) emptyList()
                            else withContext(Dispatchers.IO) { repo.searchNotes(query) }
                        } catch (e: Exception) {
                            searchResults = emptyList()
                        }
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
        }

        val displayNotes = if (searchQuery.isNotEmpty()) searchResults else notes

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (displayNotes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("点击 + 创建笔记", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayNotes) { note ->
                    val noteId = note["id"] ?: return@items
                    val isSelected = selectedIds.contains(noteId)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (selectionMode) {
                                    if (isSelected) selectedIds.remove(noteId)
                                    else selectedIds.add(noteId)
                                } else {
                                    navController.navigate("notes/editor/$noteId")
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        if (it) selectedIds.add(noteId)
                                        else selectedIds.remove(noteId)
                                    }
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(note["title"] ?: "", fontWeight = FontWeight.Medium)
                                Text(
                                    note["content"]?.take(100) ?: "",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                                Text(
                                    note["updated_at"]?.take(10) ?: "",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom bar for summarize action
        if (selectionMode && selectedIds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            LumoGradientButton(
                text = if (summarizing) "AI 汇总中..." else "汇总 ${selectedIds.size} 篇笔记",
                onClick = {
                    if (summarizing) return@LumoGradientButton
                    scope.launch {
                        summarizing = true
                        try {
                            withContext(Dispatchers.IO) {
                                repo.summarizeNotes(selectedIds.toList())
                            }
                            notes = repo.listNotes()
                            selectedIds.clear()
                            selectionMode = false
                        } catch (e: Exception) {}
                        summarizing = false
                    }
                },
                enabled = !summarizing && selectedIds.isNotEmpty(),
                loading = summarizing,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    navController: androidx.navigation.NavController,
    noteId: String? = null,
) {
    val repo = LumoRepository.get()
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var previewMode by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(noteId == null) }
    var summarizing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(noteId) {
        if (noteId != null) {
            try {
                val notes = repo.listNotes()
                val note = notes.find { it["id"] == noteId }
                if (note != null) {
                    title = note["title"] ?: ""
                    content = note["content"] ?: ""
                    previewMode = !content.isBlank()
                }
            } catch (e: Exception) {}
        }
        loaded = true
    }

    if (!loaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (noteId == null) "新建笔记" else "编辑笔记", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                if (noteId != null && !previewMode) {
                    IconButton(onClick = {
                        if (!summarizing) {
                            scope.launch {
                                summarizing = true
                                try {
                                    val summary = withContext(Dispatchers.IO) {
                                        repo.aiSummarizeNote(noteId)
                                    }
                                    if (summary.isNotBlank()) content = summary
                                } catch (e: Exception) {}
                                summarizing = false
                            }
                        }
                    }, enabled = !summarizing) {
                        if (summarizing) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        else Icon(Icons.Filled.AutoAwesome, contentDescription = "AI 摘要")
                    }
                }
                IconButton(onClick = { previewMode = !previewMode }) {
                    Icon(
                        if (previewMode) Icons.Filled.Edit else Icons.Filled.Visibility,
                        contentDescription = if (previewMode) "编辑" else "预览"
                    )
                }
                TextButton(onClick = {
                    if (noteId != null) {
                        repo.updateNote(noteId, title, content)
                    } else {
                        repo.createNote(title, content)
                    }
                    navController.popBackStack()
                }) { Text("保存") }
            }
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            label = { Text("标题") },
            singleLine = true
        )

        if (previewMode) {
            MarkdownRenderer(
                content = content,
                modifier = Modifier.fillMaxSize().padding(16.dp),
                isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme(),
            )
        } else {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                label = { Text("内容（支持 Markdown）") },
                maxLines = Int.MAX_VALUE
            )
        }
    }
}
