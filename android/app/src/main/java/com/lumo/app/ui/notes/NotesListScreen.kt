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

@Composable
fun NotesListScreen() {
    val repo = LumoRepository.get()
    var notes by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showEditor by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Map<String, String?>?>(null) }

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
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("笔记", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            FloatingActionButton(onClick = { showEditor = true; editingNote = null }) {
                Icon(Icons.Filled.Add, contentDescription = "新建笔记")
            }
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (notes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("点击 + 创建笔记", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notes) { note ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editingNote = note
                                showEditor = true
                            },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
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
}

@Composable
private fun NoteEditorScreen(
    note: Map<String, String?>?,
    onSave: (String, String) -> Unit,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf(note?.get("title") ?: "") }
    var content by remember { mutableStateOf(note?.get("content") ?: "") }

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
            TextButton(onClick = { if (title.isNotBlank()) onSave(title, content) }) {
                Text("保存")
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
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            placeholder = { Text("内容（支持 Markdown）") }
        )
    }
}
