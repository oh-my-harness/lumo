package com.lumo.app.ui.quiz

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // Quiz mode state
    var quizMode by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableStateOf(0) }
    var selectedAnswers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var gradeResults by remember { mutableStateOf<Map<String, Map<String, Any?>?>>(emptyMap()) }

    // AI generation dialog
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
                if (currentIndex >= questions.size) currentIndex = 0
            } catch (e: Exception) {}
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    if (quizMode && questions.isNotEmpty()) {
        QuizCardView(
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
                    } catch (e: Exception) {}
                }
            },
            onReset = { qid ->
                selectedAnswers = selectedAnswers - qid
                gradeResults = gradeResults - qid
            },
            onExit = {
                quizMode = false
                selectedAnswers = emptyMap()
                gradeResults = emptyMap()
                currentIndex = 0
            }
        )
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    var filterKp by remember { mutableStateOf<String?>(null) }

    // Extract all distinct knowledge points from questions
    val allKnowledgePoints = remember(questions) {
        val set = linkedSetOf<String>()
        for (q in questions) {
            val kps = q["knowledge_points"]
            if (!kps.isNullOrEmpty()) {
                try {
                    JSONArray(kps).let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    }.forEach { set.add(it) }
                } catch (e: Exception) {}
            }
        }
        set.toList()
    }

    val filteredQuestions = remember(questions, searchQuery, filterKp) {
        questions.filter { q ->
            (filterKp == null || q["knowledge_points"]?.contains(filterKp!!) == true) &&
            (searchQuery.isBlank() ||
             (q["question"]?.contains(searchQuery, ignoreCase = true) == true) ||
             (q["knowledge_points"]?.contains(searchQuery, ignoreCase = true) == true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("题库", fontWeight = FontWeight.Bold) },
                actions = {
                    if (questions.isNotEmpty()) {
                        IconButton(onClick = {
                            quizMode = true
                            currentIndex = 0
                            selectedAnswers = emptyMap()
                            gradeResults = emptyMap()
                        }) { Icon(Icons.Filled.PlayArrow, contentDescription = "开始测验") }
                    }
                    IconButton(onClick = { showGenDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "生成测验")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (questions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无题目\n点击 + 生成测验",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else {
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("搜索题目或知识点...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "清除")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Knowledge point filter dropdown
                var kpDropdownExpanded by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    OutlinedButton(
                        onClick = { kpDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            filterKp ?: "全部分类",
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = kpDropdownExpanded,
                        onDismissRequest = { kpDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("全部") },
                            onClick = { filterKp = null; kpDropdownExpanded = false }
                        )
                        allKnowledgePoints.forEach { kp ->
                            DropdownMenuItem(
                                text = { Text(kp, fontSize = 12.sp) },
                                onClick = { filterKp = kp; kpDropdownExpanded = false }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (filteredQuestions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("没有匹配的题目",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                    ) {
                        items(filteredQuestions) { question ->
                            val qid = question["id"] ?: return@items
                            val qType = question["question_type"] ?: "single_choice"
                            val qTypeLabel = when (qType) {
                                "single_choice" -> "单选"
                                "multi_choice" -> "多选"
                                "true_false" -> "判断"
                                "short_answer" -> "简答"
                                else -> qType
                            }
                            val gradeResult = gradeResults[qid]
                            val isCorrect = gradeResult?.let { g ->
                                val c = g["is_correct"]
                                c == true || c.toString() == "True"
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val idx = questions.indexOfFirst { it["id"] == qid }
                                        if (idx >= 0) {
                                            currentIndex = idx
                                            quizMode = true
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isCorrect == true -> MaterialTheme.colorScheme.primaryContainer
                                        isCorrect == false -> MaterialTheme.colorScheme.errorContainer
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                )
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text(qTypeLabel, fontSize = 10.sp) }
                                        )
                                        if (isCorrect != null) {
                                            Icon(
                                                if (isCorrect == true) Icons.Filled.CheckCircle
                                                else Icons.Filled.Cancel,
                                                contentDescription = null,
                                                tint = if (isCorrect == true)
                                                    MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        question["question"] ?: "",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 3
                                    )
                                    val kps = question["knowledge_points"]
                                    if (!kps.isNullOrEmpty()) {
                                        val tags = try {
                                            JSONArray(kps).let { arr ->
                                                (0 until arr.length()).map { arr.getString(it) }
                                            }
                                        } catch (e: Exception) { emptyList() }
                                        if (tags.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                tags.joinToString(" · "),
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
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

    // Generation dialog
    if (showGenDialog) {
        AlertDialog(
            onDismissRequest = { if (!generating) showGenDialog = false },
            title = { Text("生成测验") },
            text = {
                Column {
                    OutlinedTextField(
                        value = genTopic,
                        onValueChange = { genTopic = it },
                        label = { Text("知识点 / 主题") },
                        placeholder = { Text("如：Python 闭包") },
                        singleLine = true,
                        enabled = !generating
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = genCount,
                        onValueChange = { genCount = it.filter { c -> c.isDigit() } },
                        label = { Text("题目数量") },
                        singleLine = true,
                        enabled = !generating
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
                                    currentIndex = 0
                                    selectedAnswers = emptyMap()
                                    gradeResults = emptyMap()
                                    refresh()
                                } catch (e: Exception) {
                                    Log.e("LumoQuiz", "generateQuiz failed", e)
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
                TextButton(
                    onClick = { if (!generating) showGenDialog = false },
                    enabled = !generating
                ) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuizCardView(
    questions: List<Map<String, String?>>,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    selectedAnswers: Map<String, String>,
    gradeResults: Map<String, Map<String, Any?>?>,
    onAnswerSelected: (String, String) -> Unit,
    onSubmit: (String, String) -> Unit,
    onReset: (String) -> Unit,
    onExit: () -> Unit,
) {
    val question = questions[currentIndex]
    val qid = question["id"] ?: return
    val qType = question["question_type"] ?: "single_choice"
    val options = parseOptions(question["options"])
    val selected = selectedAnswers[qid] ?: ""
    val gradeResult = gradeResults[qid]
    val answered = gradeResult != null
    val total = questions.size
    val completedCount = gradeResults.size

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("测验 ${currentIndex + 1} / $total") },
            navigationIcon = {
                IconButton(onClick = onExit) {
                    Icon(Icons.Filled.Close, contentDescription = "退出测验")
                }
            }
        )
        // Progress bar + dots
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { if (currentIndex > 0) onIndexChange(currentIndex - 1) },
                enabled = currentIndex > 0
            ) { Icon(Icons.Filled.ChevronLeft, contentDescription = "上一题") }

            // Question dots
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
            // Tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val kps = question["knowledge_points"]
                if (!kps.isNullOrEmpty()) {
                    val tags = try {
                        JSONArray(kps).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                    } catch (e: Exception) { emptyList() }
                    tags.take(3).forEach { tag ->
                        SuggestionChip(onClick = {}, label = { Text(tag, fontSize = 10.sp) })
                    }
                }
                SuggestionChip(
                    onClick = {},
                    label = { Text(qType, fontSize = 10.sp) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Question text
            Text(
                question["question"] ?: "",
                fontSize = 13.sp,
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
                // single_choice / multi_choice
                options.forEachIndexed { idx, opt ->
                    val isSelected = selected == idx.toString()
                    val isCorrectAnswer = question["answer"]?.let { answer ->
                        answer.trim().equals(opt.trim(), ignoreCase = true) ||
                        answer.trim().equals((idx + 1).toString(), ignoreCase = true) ||
                        answer.trim().equals(('A' + idx).toString(), ignoreCase = true)
                    } ?: false

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
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
                                fontSize = 14.sp
                            )
                        }
                        result["explanation"]?.let { exp ->
                            if (exp.toString().isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    exp.toString(),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (!isCorrect && qType != "short_answer") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "正确答案: ${question["answer"] ?: ""}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
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

            // Summary when all done
            if (completedCount == total) {
                Spacer(modifier = Modifier.height(8.dp))
                val correctCount = gradeResults.values.count { g ->
                    val c = g?.get("is_correct")
                    c == true || c.toString() == "True"
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        "测验完成！$correctCount / $total 正确",
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
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
        // Fallback: split by newline if not JSON
        optionsJson.lines().filter { it.isNotBlank() }
    }
}
