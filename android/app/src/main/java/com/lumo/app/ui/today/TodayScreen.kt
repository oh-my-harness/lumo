package com.lumo.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.lumo.app.ui.profile.CreatePlanScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // Header: streak + study time
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCard("连续打卡", "$streak 天", Icons.Filled.LocalFireDepartment)
            StatCard("学习时长", "${totalStudy / 60} 分钟", Icons.Filled.Schedule)
        }
        Spacer(modifier = Modifier.height(16.dp))

        // 番茄钟
        var pomodoroRunning by remember { mutableStateOf(false) }
        var pomodoroSeconds by remember { mutableStateOf(25 * 60) }
        var pomodoroDuration by remember { mutableStateOf(25) } // minutes
        var pomodoroTaskId by remember { mutableStateOf<String?>(null) }
        var pomodoroPlanId by remember { mutableStateOf<String?>(null) }
        var pomodoroStartedAt by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val mins = pomodoroSeconds / 60
                val secs = pomodoroSeconds % 60
                Text(
                    String.format("%02d:%02d", mins, secs),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15 to "15+5", 25 to "25+5", 50 to "50+10").forEach { (mins, label) ->
                        FilterChip(
                            selected = pomodoroDuration == mins,
                            onClick = {
                                if (!pomodoroRunning) {
                                    pomodoroDuration = mins
                                    pomodoroSeconds = mins * 60
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (pomodoroRunning) {
                                // 停止
                                pomodoroRunning = false
                            } else {
                                // 开始
                                pomodoroRunning = true
                                pomodoroStartedAt = java.time.Instant.now().toString()
                                scope.launch {
                                    while (pomodoroRunning && pomodoroSeconds > 0) {
                                        delay(1000)
                                        if (pomodoroRunning) {
                                            pomodoroSeconds--
                                            if (pomodoroSeconds <= 0) {
                                                pomodoroRunning = false
                                                // 记录番茄钟
                                                val taskId = pomodoroTaskId
                                                val planId = pomodoroPlanId
                                                if (taskId != null && planId != null) {
                                                    withContext(Dispatchers.IO) {
                                                        repo.recordPomodoro(
                                                            taskId, planId,
                                                            pomodoroDuration * 60, pomodoroStartedAt
                                                        )
                                                        totalStudy = repo.getTotalStudyTime()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(
                            if (pomodoroRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (pomodoroRunning) "暂停" else "开始")
                    }
                    if (pomodoroRunning) {
                        OutlinedButton(onClick = {
                            pomodoroRunning = false
                            pomodoroSeconds = pomodoroDuration * 60
                        }) { Text("重置") }
                    }
                }
            }
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
                Text("暂无任务\n在下方「学习计划」创建一个吧", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Group by plan_title
                val grouped = tasks.groupBy { it["plan_title"] ?: "未分组" }
                grouped.forEach { (planTitle, planTasks) ->
                    Text(
                        planTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    planTasks.forEach { task ->
                        TaskCard(
                            task = task,
                            onComplete = { taskId ->
                                val newStatus = if (task["status"] == "completed") "pending" else "completed"
                                repo.updateTaskStatus(taskId, newStatus)
                                tasks = repo.getTodayTasks()
                            },
                            onStartPomodoro = { taskId, planId ->
                                pomodoroTaskId = taskId
                                pomodoroPlanId = planId
                            }
                        )
                    }
                }
            }
        }
        if (tasks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            val completedTaskIds = tasks.filter { it["status"] == "completed" }.mapNotNull { it["id"] }
            Button(
                onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                repo.checkinToday(completedTaskIds)
                                streak = repo.getStreak()
                            }
                        } catch (e: Exception) {}
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = completedTaskIds.isNotEmpty()
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("今日打卡")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === 学习计划 section ===
        var plans by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
        var showCreatePlan by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            try { plans = repo.listPlans() } catch (e: Exception) {}
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("学习计划", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { showCreatePlan = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("新建")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        plans.forEach { plan ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(plan["title"] ?: "", fontWeight = FontWeight.Medium)
                        Text(
                            plan["goal"] ?: "",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    AssistChip(
                        onClick = {
                            val newStatus = if (plan["status"] == "active") "paused" else "active"
                            scope.launch {
                                withContext(Dispatchers.IO) { repo.updatePlanStatus(plan["id"]!!, newStatus) }
                                plans = repo.listPlans()
                            }
                        },
                        label = { Text(plan["status"] ?: "") }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === 学习统计 section ===
        Text("学习统计", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        var stats by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
        LaunchedEffect(Unit) {
            try { stats = repo.getStats() } catch (e: Exception) {}
        }
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                val totalTime = stats["total_study_time"] as? Int ?: 0
                Text("总学习时长: ${totalTime / 60} 分钟")

                // 学习趋势（近 7 天）
                Spacer(modifier = Modifier.height(8.dp))
                Text("近 7 天学习时长", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                var trend by remember { mutableStateOf<Map<String, Any?>?>(null) }
                LaunchedEffect(Unit) {
                    try { trend = withContext(Dispatchers.IO) { repo.getStudyTrend("week") } } catch (e: Exception) {}
                }
                trend?.let { t ->
                    @Suppress("UNCHECKED_CAST")
                    val data = t["data"] as? Map<String, Int> ?: emptyMap()
                    val maxVal = (data.values.maxOrNull() ?: 1).coerceAtLeast(1)
                    data.entries.toList().takeLast(7).forEach { (date, seconds) ->
                        val mins = seconds / 60
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(date.takeLast(5), fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val barWidth = (mins.toFloat() / maxVal.coerceAtLeast(1)).coerceIn(0f, 1f)
                            Box(modifier = Modifier
                                .width((barWidth * 120).toInt().dp.coerceAtLeast(2.dp))
                                .height(8.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
                            Text("${mins}m", fontSize = 11.sp)
                        }
                    }
                }

                // 打卡热力图
                Spacer(modifier = Modifier.height(8.dp))
                Text("本月打卡", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                var heatmap by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
                LaunchedEffect(Unit) {
                    try {
                        val month = LocalDate.now().toString().take(7)
                        heatmap = withContext(Dispatchers.IO) { repo.getCheckinHeatmap(month) }
                    } catch (e: Exception) {}
                }
                Text("打卡 ${heatmap.size} 天", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                // 知识点掌握度
                if (plans.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("知识点掌握度", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    var mastery by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
                    LaunchedEffect(plans) {
                        try {
                            mastery = withContext(Dispatchers.IO) {
                                repo.getKnowledgeMastery(plans.first()["id"]!!)
                            }
                        } catch (e: Exception) {}
                    }
                    mastery.forEach { kp ->
                        val level = kp["mastery_level"] ?: "0"
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(kp["name"] ?: "", fontSize = 12.sp)
                            LinearProgressIndicator(
                                progress = { (level.toFloatOrNull() ?: 0f) / 100f },
                                modifier = Modifier.width(100.dp)
                            )
                            Text("$level%", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Create plan dialog
        if (showCreatePlan) {
            CreatePlanScreen(
                onSave = { _, _, _, _, _ ->
                    scope.launch {
                        try { plans = repo.listPlans() } catch (e: Exception) {}
                        showCreatePlan = false
                    }
                },
                onBack = { showCreatePlan = false }
            )
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
private fun TaskCard(
    task: Map<String, String?>,
    onComplete: (String) -> Unit,
    onStartPomodoro: (String, String) -> Unit = { _, _ -> }
) {
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
            // 番茄钟按钮
            if (!isCompleted) {
                val planId = task["plan_id"] ?: ""
                IconButton(onClick = { onStartPomodoro(taskId, planId) }) {
                    Icon(Icons.Filled.Timer, contentDescription = "番茄钟")
                }
            }
        }
    }
}
