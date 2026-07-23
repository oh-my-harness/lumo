package com.lumo.app.ui.quiz

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumo.app.data.LumoRepository
import com.lumo.app.data.dto.QuestionDto
import com.lumo.app.data.dto.GradeResultDto
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen() {
    val vm: QuizViewModel = viewModel { QuizViewModel(LumoRepository.get()) }
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val genDialog by vm.genDialog.collectAsStateWithLifecycle()

    if (uiState.quizMode && uiState.questions.isNotEmpty()) {
        QuizCardView(
            questions = uiState.questions,
            currentIndex = uiState.currentIndex,
            onIndexChange = { vm.setCurrentIndex(it) },
            selectedAnswers = uiState.selectedAnswers,
            gradeResults = uiState.gradeResults,
            onAnswerSelected = { qid, answer -> vm.onAnswerSelected(qid, answer) },
            onSubmit = { qid, answer -> vm.gradeAnswer(qid, answer) },
            onReset = { qid -> vm.resetAnswer(qid) },
            onExit = { vm.setQuizMode(false) }
        )
        return
    }

    val questions = uiState.questions
    val searchQuery = uiState.searchQuery
    val filterKp = uiState.filterKp

    // Extract all distinct knowledge points from questions
    val allKnowledgePoints = remember(questions) {
        val set = linkedSetOf<String>()
        for (q in questions) {
            parseKpList(q.knowledge_points).forEach { set.add(it) }
        }
        set.toList()
    }

    val filteredQuestions = remember(questions, searchQuery, filterKp) {
        questions.filter { q ->
            (filterKp == null || parseKpList(q.knowledge_points).contains(filterKp)) &&
            (searchQuery.isBlank() ||
             q.question.contains(searchQuery, ignoreCase = true) ||
             (q.knowledge_points?.contains(searchQuery, ignoreCase = true) == true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("题库", fontWeight = FontWeight.Bold) },
                actions = {
                    if (questions.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuizMode(true) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "开始测验")
                        }
                    }
                    IconButton(onClick = { vm.showGenDialog(true) }) {
                        Icon(Icons.Filled.Add, contentDescription = "生成测验")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (questions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无题目\n点击 + 生成测验",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            } else {
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { vm.setSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("搜索题目或知识点...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { vm.setSearchQuery("") }) {
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
                            onClick = { vm.setFilterKp(null); kpDropdownExpanded = false }
                        )
                        allKnowledgePoints.forEach { kp ->
                            DropdownMenuItem(
                                text = { Text(kp, fontSize = 12.sp) },
                                onClick = { vm.setFilterKp(kp); kpDropdownExpanded = false }
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
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(filteredQuestions) { question ->
                            val qid = question.id
                            val qType = question.question_type
                            val qTypeLabel = when (qType) {
                                "single_choice" -> "单选"
                                "multi_choice" -> "多选"
                                "true_false" -> "判断"
                                "short_answer" -> "简答"
                                else -> qType
                            }
                            val gradeResult = uiState.gradeResults[qid]
                            val isCorrect = gradeResult?.is_correct
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.startQuizAt(qid) },
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
                                                if (isCorrect) Icons.Filled.CheckCircle
                                                else Icons.Filled.Cancel,
                                                contentDescription = null,
                                                tint = if (isCorrect)
                                                    MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        question.question,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 3
                                    )
                                    val tags = parseKpList(question.knowledge_points)
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

    // Generation dialog
    if (genDialog.showGenDialog) {
        AlertDialog(
            onDismissRequest = { if (!genDialog.generating) vm.showGenDialog(false) },
            title = { Text("生成测验") },
            text = {
                Column {
                    OutlinedTextField(
                        value = genDialog.genTopic,
                        onValueChange = { vm.setGenTopic(it) },
                        label = { Text("知识点 / 主题") },
                        placeholder = { Text("如：Python 闭包") },
                        singleLine = true,
                        enabled = !genDialog.generating
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = genDialog.genCount,
                        onValueChange = { vm.setGenCount(it) },
                        label = { Text("题目数量") },
                        singleLine = true,
                        enabled = !genDialog.generating
                    )
                    if (genDialog.genResult.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(genDialog.genResult, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (genDialog.genTopic.isNotBlank() && !genDialog.generating) {
                            vm.generateQuiz(genDialog.genTopic, genDialog.genCount.toIntOrNull() ?: 3)
                        }
                    },
                    enabled = !genDialog.generating && genDialog.genTopic.isNotBlank()
                ) {
                    if (genDialog.generating) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    else Text("生成")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!genDialog.generating) vm.showGenDialog(false) },
                    enabled = !genDialog.generating
                ) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuizCardView(
    questions: List<QuestionDto>,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    selectedAnswers: Map<String, String>,
    gradeResults: Map<String, GradeResultDto>,
    onAnswerSelected: (String, String) -> Unit,
    onSubmit: (String, String) -> Unit,
    onReset: (String) -> Unit,
    onExit: () -> Unit,
) {
    val question = questions[currentIndex]
    val qid = question.id
    val qType = question.question_type
    val options = parseOptions(question.options)
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
                    val qGrade = gradeResults[q.id]
                    val isCorrect = qGrade?.is_correct
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
                val tags = parseKpList(question.knowledge_points)
                tags.take(3).forEach { tag ->
                    SuggestionChip(onClick = {}, label = { Text(tag, fontSize = 10.sp) })
                }
                SuggestionChip(
                    onClick = {},
                    label = { Text(qType, fontSize = 10.sp) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Question text
            Text(
                question.question,
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
                    val isCorrectAnswer = question.answer.let { answer ->
                        answer.trim().equals(opt.trim(), ignoreCase = true) ||
                        answer.trim().equals((idx + 1).toString(), ignoreCase = true) ||
                        answer.trim().equals(('A' + idx).toString(), ignoreCase = true)
                    }

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
                val isCorrect = result.is_correct
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
                        if (result.explanation.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                result.explanation,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!isCorrect && qType != "short_answer") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "正确答案: ${question.answer}",
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
                val correctCount = gradeResults.values.count { it.is_correct }
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
internal fun parseOptions(optionsJson: String?): List<String> {
    if (optionsJson.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(optionsJson)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        // Fallback: split by newline if not JSON
        optionsJson.lines().filter { it.isNotBlank() }
    }
}

/** Parse knowledge_points JSON string into a list of tag strings. */
internal fun parseKpList(kpJson: String?): List<String> {
    if (kpJson.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(kpJson)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        kpJson.lines().filter { it.isNotBlank() }
    }
}
