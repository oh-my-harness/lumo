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
