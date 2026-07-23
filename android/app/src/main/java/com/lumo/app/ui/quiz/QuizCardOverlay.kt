package com.lumo.app.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumo.app.data.LumoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Full-screen quiz card overlay for in-chat quiz mode.
 *
 * Flow: chat triggers quiz generation → this overlay shows one question at a time
 * as a flashcard → after all questions answered, asks whether to save to 题库.
 *
 * @param questions List of question maps (from generate_quiz bridge call)
 * @param onExit Called when user finishes or exits
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizCardOverlay(
    questions: List<Map<String, String?>>,
    onExit: () -> Unit,
) {
    val repo = LumoRepository.get()
    val scope = rememberCoroutineScope()
    var currentIndex by remember { mutableStateOf(0) }
    var selectedAnswers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var gradeResults by remember { mutableStateOf<Map<String, Map<String, Any?>?>>(emptyMap()) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val total = questions.size
    val completedCount = gradeResults.size
    val allDone = completedCount == total

    if (questions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text("没有题目")
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("做题 ($completedCount/$total)") },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.Filled.Close, contentDescription = "退出")
                    }
                }
            )
        }
    ) { padding ->
        if (allDone) {
            // Summary view
            val correctCount = gradeResults.values.count { g ->
                val c = g?.get("is_correct")
                c == true || c.toString() == "True"
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    if (correctCount == total) Icons.Filled.EmojiEvents
                    else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "$correctCount / $total",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("正确", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            currentIndex = 0
                            selectedAnswers = emptyMap()
                            gradeResults = emptyMap()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("重新做") }
                    Button(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("保存到题库") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onExit) { Text("不保存，返回对话") }
            }
        } else {
            QuizCardContent(
                questions = questions,
                currentIndex = currentIndex,
                onIndexChange = { currentIndex = it },
                selectedAnswers = selectedAnswers,
                gradeResults = gradeResults,
                onAnswerSelected = { qid, answer ->
                    selectedAnswers = selectedAnswers + (qid to answer)
                },
                onSubmit = { qid, answer ->
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                repo.gradeAnswer(qid, answer)
                            }
                            gradeResults = gradeResults + (qid to result)
                            // Auto-advance after a short delay
                            if (currentIndex < total - 1) {
                                kotlinx.coroutines.delay(1500)
                                currentIndex++
                            }
                        } catch (e: Exception) {}
                    }
                },
                onReset = { qid ->
                    selectedAnswers = selectedAnswers - qid
                    gradeResults = gradeResults - qid
                },
                modifier = Modifier.padding(padding)
            )
        }
    }

    // Save to quiz bank dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { if (!saving) showSaveDialog = false },
            title = { Text("保存到题库") },
            text = {
                if (saving) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("正在保存...")
                    }
                } else {
                    Text("将 $total 道题（含答题记录）保存到题库？\n\n错题会自动收录到错题本。")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (saving) return@TextButton
                        scope.launch {
                            saving = true
                            // gradeAnswer already persisted answers during grading.
                            // Questions are already persisted by generate_quiz.
                            // Nothing extra to save — just dismiss.
                            saving = false
                            showSaveDialog = false
                            onExit()
                        }
                    },
                    enabled = !saving
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!saving) { showSaveDialog = false; onExit() } },
                    enabled = !saving
                ) { Text("不保存") }
            }
        )
    }
}

@Composable
private fun QuizCardContent(
    questions: List<Map<String, String?>>,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    selectedAnswers: Map<String, String>,
    gradeResults: Map<String, Map<String, Any?>?>,
    onAnswerSelected: (String, String) -> Unit,
    onSubmit: (String, String) -> Unit,
    onReset: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val question = questions[currentIndex]
    val qid = question["id"] ?: return
    val qType = question["question_type"] ?: "single_choice"
    val options = parseOptions(question["options"])
    val selected = selectedAnswers[qid] ?: ""
    val gradeResult = gradeResults[qid]
    val answered = gradeResult != null
    val total = questions.size

    Column(modifier = modifier.fillMaxSize()) {
        // Progress dots
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { if (currentIndex > 0) onIndexChange(currentIndex - 1) },
                enabled = currentIndex > 0
            ) { Icon(Icons.Filled.ChevronLeft, contentDescription = "上一题") }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center
            ) {
                questions.forEachIndexed { i, q ->
                    val qGrade = gradeResults[q["id"]]
                    val isCorrect = qGrade?.let { g ->
                        val c = g["is_correct"]
                        c == true || c.toString() == "True"
                    }
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(28.dp)
                            .background(
                                color = when {
                                    i == currentIndex -> MaterialTheme.colorScheme.primary
                                    isCorrect == true -> MaterialTheme.colorScheme.primaryContainer
                                    isCorrect == false -> MaterialTheme.colorScheme.errorContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${i + 1}",
                            fontSize = 11.sp,
                            fontWeight = if (i == currentIndex) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                i == currentIndex -> MaterialTheme.colorScheme.onPrimary
                                isCorrect == true -> MaterialTheme.colorScheme.onPrimaryContainer
                                isCorrect == false -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            IconButton(
                onClick = { if (currentIndex < total - 1) onIndexChange(currentIndex + 1) },
                enabled = currentIndex < total - 1
            ) { Icon(Icons.Filled.ChevronRight, contentDescription = "下一题") }
        }

        // Progress bar
        LinearProgressIndicator(
            progress = { (currentIndex + 1f) / total },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        // Question card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
        ) {
            // Type tag
            SuggestionChip(
                onClick = {},
                label = { Text(qType, fontSize = 10.sp) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Question text
            Text(
                question["question"] ?: "",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Answer area
            if (qType == "short_answer") {
                OutlinedTextField(
                    value = selected,
                    onValueChange = { if (!answered) onAnswerSelected(qid, it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("输入答案") },
                    enabled = !answered
                )
            } else if (qType == "true_false") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("正确" to "true", "错误" to "false").forEach { (label, value) ->
                        FilterChip(
                            selected = selected == value,
                            onClick = { if (!answered) onAnswerSelected(qid, value) },
                            label = { Text(label) },
                            enabled = !answered
                        )
                    }
                }
            } else {
                options.forEachIndexed { idx, opt ->
                    val isSelected = selected == idx.toString()
                    val isCorrectAnswer = question["answer"]?.let { answer ->
                        answer.trim().equals(opt.trim(), ignoreCase = true) ||
                        answer.trim().equals((idx + 1).toString(), ignoreCase = true) ||
                        answer.trim().equals(('A' + idx).toString(), ignoreCase = true)
                    } ?: false

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                answered && isCorrectAnswer -> MaterialTheme.colorScheme.primaryContainer
                                answered && isSelected -> MaterialTheme.colorScheme.errorContainer
                                isSelected -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                enabled = !answered,
                                onClick = { onAnswerSelected(qid, idx.toString()) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(opt, fontSize = 14.sp)
                            if (answered && isCorrectAnswer) {
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Filled.Check, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                            if (answered && isSelected && !isCorrectAnswer) {
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Filled.Close, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // Grade result
            gradeResult?.let { result ->
                Spacer(modifier = Modifier.height(16.dp))
                val correct = result["is_correct"]
                val isCorrect = correct == true || correct.toString() == "True"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCorrect) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isCorrect) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                                contentDescription = null,
                                tint = if (isCorrect) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isCorrect) "正确！" else "答错了",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        result["explanation"]?.let { exp ->
                            if (exp.toString().isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(exp.toString(), fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (!isCorrect && qType != "short_answer") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("正确答案: ${question["answer"] ?: ""}",
                                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!answered && selected.isNotEmpty()) {
                    Button(
                        onClick = { onSubmit(qid, selected) },
                        modifier = Modifier.weight(1f)
                    ) { Text("提交答案") }
                }
                if (answered) {
                    OutlinedButton(
                        onClick = { onReset(qid) },
                        modifier = Modifier.weight(1f)
                    ) { Text("重做") }
                    if (currentIndex < total - 1) {
                        Button(
                            onClick = { onIndexChange(currentIndex + 1) },
                            modifier = Modifier.weight(1f)
                        ) { Text("下一题") }
                    }
                }
            }
        }
    }
}

/** Parse options JSON string into a list of option strings. */
private fun parseOptions(optionsJson: String?): List<String> {
    if (optionsJson.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(optionsJson)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        optionsJson.lines().filter { it.isNotBlank() }
    }
}
