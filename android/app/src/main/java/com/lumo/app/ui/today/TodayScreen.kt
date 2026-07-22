package com.lumo.app.ui.today

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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen() {
    val repo = LumoRepository.get()
    var tasks by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
    var streak by remember { mutableStateOf(0) }
    var totalStudy by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            tasks = repo.getTodayTasks()
            streak = repo.getStreak()
            totalStudy = repo.getTotalStudyTime()
        } catch (e: Exception) {
            // ignore
        }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header: streak + study time
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCard("连续打卡", "$streak 天", Icons.Filled.LocalFireDepartment)
            StatCard("学习时长", "${totalStudy / 60} 分钟", Icons.Filled.Schedule)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text("今日任务", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (tasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无任务\n去「我的」创建学习计划吧", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Group by plan_title
                val grouped = tasks.groupBy { it["plan_title"] ?: "未分组" }
                grouped.forEach { (planTitle, planTasks) ->
                    item {
                        Text(
                            planTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(planTasks) { task ->
                        TaskCard(task) { taskId ->
                            val newStatus = if (task["status"] == "completed") "pending" else "completed"
                            repo.updateTaskStatus(taskId, newStatus)
                            tasks = repo.getTodayTasks()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(modifier = Modifier.size(160.dp, 100.dp), shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TaskCard(task: Map<String, String?>, onComplete: (String) -> Unit) {
    val taskId = task["id"] ?: return
    val isCompleted = task["status"] == "completed"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isCompleted,
                onCheckedChange = { onComplete(taskId) }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task["title"] ?: "",
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                )
                if (!task["description"].isNullOrEmpty()) {
                    Text(
                        task["description"]!!,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
        }
    }
}
