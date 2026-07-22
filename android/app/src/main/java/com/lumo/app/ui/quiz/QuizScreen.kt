package com.lumo.app.ui.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumo.app.data.LumoRepository

@Composable
fun QuizScreen() {
    val repo = LumoRepository.get()
    var questions by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
    var errors by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            questions = repo.getQuizQuestions()
            errors = repo.getQuizErrors()
        } catch (e: Exception) {}
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("题库", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))

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
                    Text(if (tab == 0) "暂无题目" else "暂无错题", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list) { item ->
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    item["question"] ?: "",
                                    fontWeight = FontWeight.Medium
                                )
                                if (tab == 1) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "你的答案: ${item["user_answer"] ?: ""}",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        "正确答案: ${item["correct_answer"] ?: item["answer"] ?: ""}",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (!item["error_reason"].isNullOrEmpty()) {
                                        Text(
                                            "原因: ${item["error_reason"]}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (!item["explanation"].isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        item["explanation"]!!,
                                        fontSize = 12.sp,
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
