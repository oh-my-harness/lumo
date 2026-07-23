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
fun NotesListScreen() {
    val repo = LumoRepository.get()
    var notes by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showEditor by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Map<String, String?>?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var summarizing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        try { notes = repo.listNotes() } catch (e: Exception) {}
        loading = false
    }

    if (showEditor) {
        NoteEditorScreen(
            note = editingNote,
            onSave = { title, content ->
                try {
                    if (editingNote != null) {
                        repo.updateNote(editingNote!!["id"]!!, title, content)
                    } else {
                        repo.createNote(title, content)
                    }
                    notes = repo.listNotes()
                } catch (e: Exception) {}
                showEditor = false
                editingNote = null
            },
            onBack = {
                showEditor = false
                editingNote = null
            }
        )
        return
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
                        onClick = { showEditor = true; editingNote = null },
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
                                    editingNote = note
                                    showEditor = true
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

@Composable
private fun NoteEditorScreen(
    note: Map<String, String?>?,
    onSave: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val repo = LumoRepository.get()
    var title by remember { mutableStateOf(note?.get("title") ?: "") }
    var content by remember { mutableStateOf(note?.get("content") ?: "") }
    var previewMode by remember { mutableStateOf(!(note?.get("content").isNullOrEmpty())) }
    var aiSummary by remember { mutableStateOf("") }
    var summarizing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
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
                fontWeight = FontWeight.Bold
            )
            Row {
                if (note != null && !previewMode) {
                    IconButton(onClick = {
                        if (!summarizing) {
                            scope.launch {
                                summarizing = true
                                try {
                                    aiSummary = withContext(Dispatchers.IO) {
                                        repo.aiSummarizeNote(note["id"]!!)
                                    }
                                    if (aiSummary.isNotBlank()) {
                                        content = aiSummary
                                    }
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
                TextButton(onClick = { onSave(title, content) }) {
                    Text("保存")
                }
            }
        }

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
