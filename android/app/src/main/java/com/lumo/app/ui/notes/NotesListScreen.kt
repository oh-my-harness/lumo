package com.lumo.app.ui.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumo.app.data.LumoRepository
import com.lumo.app.ui.markdown.MarkdownRenderer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesListScreen(navController: androidx.navigation.NavController) {
    val vm: NotesListViewModel = viewModel { NotesListViewModel(LumoRepository.get()) }
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    // Confirmation dialogs
    var showSummarizeDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSummarizeAndDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    // Result snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    val resultMessage = uiState.resultMessage
    LaunchedEffect(resultMessage) {
        resultMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeResultMessage()
        }
    }

    // ── Confirmation dialogs ──
    if (showSummarizeDialog) {
        AlertDialog(
            onDismissRequest = { showSummarizeDialog = false },
            title = { Text("汇总笔记") },
            text = { Text("将选中的 ${uiState.selectedIds.size} 篇笔记交给 AI 归纳为一篇新笔记？\n\n原笔记保留不变。") },
            confirmButton = {
                TextButton(onClick = {
                    showSummarizeDialog = false
                    vm.summarizeSelected()
                }) { Text("汇总") }
            },
            dismissButton = { TextButton(onClick = { showSummarizeDialog = false }) { Text("取消") } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除笔记") },
            text = { Text("确定删除选中的 ${uiState.selectedIds.size} 篇笔记？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    vm.deleteSelected()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
        )
    }

    if (showSummarizeAndDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showSummarizeAndDeleteDialog = false },
            title = { Text("汇总并删除原笔记") },
            text = { Text("将选中的 ${uiState.selectedIds.size} 篇笔记归纳为一篇新笔记，然后删除原笔记？\n\n此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showSummarizeAndDeleteDialog = false
                    vm.summarizeAndDeleteSelected()
                }) { Text("汇总并删除") }
            },
            dismissButton = { TextButton(onClick = { showSummarizeAndDeleteDialog = false }) { Text("取消") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.selectionMode) "已选 ${uiState.selectedIds.size} 篇" else "笔记",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (uiState.selectionMode) {
                        TextButton(onClick = { vm.selectAll() }) { Text("全选") }
                        IconButton(onClick = { vm.clearSelection() }) {
                            Icon(Icons.Filled.Close, contentDescription = "退出选择")
                        }
                    } else {
                        FloatingActionButton(
                            onClick = { navController.navigate("notes/editor") },
                            modifier = Modifier.size(40.dp)
                        ) { Icon(Icons.Filled.Add, contentDescription = "新建笔记") }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (!uiState.selectionMode) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { query -> vm.search(query) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("搜索笔记...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { vm.search("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "清除")
                            }
                        }
                    },
                    singleLine = true
                )
            }

            val displayNotes = if (uiState.searchQuery.isNotEmpty()) uiState.searchResults else uiState.notes

            if (uiState.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (displayNotes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (uiState.selectionMode) "没有可选的笔记" else "点击 + 创建笔记",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayNotes) { note ->
                        val isSelected = uiState.selectedIds.contains(note.id)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (uiState.selectionMode) {
                                            vm.toggleSelection(note.id)
                                        } else {
                                            navController.navigate("notes/editor/${note.id}")
                                        }
                                    },
                                    onLongClick = {
                                        if (!uiState.selectionMode) {
                                            vm.enterSelectionMode(note.id)
                                        } else if (!isSelected) {
                                            vm.toggleSelection(note.id)
                                        }
                                    }
                                ),
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
                                if (uiState.selectionMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { vm.toggleSelection(note.id) }
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(note.title, fontWeight = FontWeight.Medium)
                                    Text(
                                        note.content.take(100),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                    Text(
                                        note.updated_at.take(10),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom action bar in selection mode
            if (uiState.selectionMode && uiState.selectedIds.isNotEmpty()) {
                Surface(
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showSummarizeDialog = true },
                            enabled = !uiState.processing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("汇总", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { showSummarizeAndDeleteDialog = true },
                            enabled = !uiState.processing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Merge, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("汇总并删除", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            enabled = !uiState.processing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("删除", fontSize = 13.sp)
                        }
                    }
                    if (uiState.processing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    navController: androidx.navigation.NavController,
    noteId: String? = null,
) {
    val vm: NoteEditorViewModel = viewModel { NoteEditorViewModel(LumoRepository.get()) }
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(noteId) { vm.load(noteId) }

    if (!uiState.loaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(if (noteId == null) "新建笔记" else "编辑笔记", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                if (noteId != null && !uiState.previewMode) {
                    IconButton(
                        onClick = { vm.aiSummarize(noteId) },
                        enabled = !uiState.summarizing
                    ) {
                        if (uiState.summarizing) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        else Icon(Icons.Filled.AutoAwesome, contentDescription = "AI 摘要")
                    }
                }
                IconButton(onClick = { vm.togglePreview() }) {
                    Icon(
                        if (uiState.previewMode) Icons.Filled.Edit else Icons.Filled.Visibility,
                        contentDescription = if (uiState.previewMode) "编辑" else "预览"
                    )
                }
                TextButton(onClick = {
                    vm.save(noteId)
                    navController.popBackStack()
                }) { Text("保存") }
            }
        )

        OutlinedTextField(
            value = uiState.title,
            onValueChange = { vm.updateTitle(it) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            label = { Text("标题") },
            singleLine = true
        )

        if (uiState.previewMode) {
            MarkdownRenderer(
                content = uiState.content,
                modifier = Modifier.fillMaxSize().padding(16.dp),
                isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme(),
            )
        } else {
            OutlinedTextField(
                value = uiState.content,
                onValueChange = { vm.updateContent(it) },
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                label = { Text("内容（支持 Markdown）") },
                maxLines = Int.MAX_VALUE
            )
        }
    }
}
