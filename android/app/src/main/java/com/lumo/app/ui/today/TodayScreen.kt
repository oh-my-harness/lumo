package com.lumo.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumo.app.data.LumoRepository
import com.lumo.app.ui.components.LumoStatsCard
import com.lumo.app.ui.components.LumoSegmentedControl
import com.lumo.app.ui.components.LumoEmptyState
import com.lumo.app.ui.profile.CreatePlanScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen() {
    val repo = LumoRepository.get()
    var tasks by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
    var streak by remember { mutableStateOf(0) }
    var totalStudy by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    // Plans + stats loaded once
    var plans by remember { mutableStateOf<List<Map<String, String?>>>(emptyList()) }
    var stats by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    var showCreatePlan by remember { mutableStateOf(false) }

    // Pomodoro state
    var pomodoroRunning by remember { mutableStateOf(false) }
    var pomodoroSeconds by remember { mutableStateOf(25 * 60) }
    var pomodoroDuration by remember { mutableStateOf(25) }
    var pomodoroTaskId by remember { mutableStateOf<String?>(null) }
    var pomodoroPlanId by remember { mutableStateOf<String?>(null) }
    var pomodoroStartedAt by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        loading = true
        try {
            tasks = repo.getTodayTasks()
            streak = repo.getStreak()
            totalStudy = repo.getTotalStudyTime()
            plans = repo.listPlans()
            stats = repo.getStats()
        } catch (e: Exception) {}
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header: streak + study time
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LumoStatsCard(
                label = "连续打卡",
                value = "$streak 天",
                icon = Icons.Filled.LocalFireDepartment,
                accentColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
            LumoStatsCard(
                label = "学习时长",
                value = "${totalStudy / 60} 分钟",
                icon = Icons.Filled.Schedule,
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // 番茄钟
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val mins = pomodoroSeconds / 60
                val secs = pomodoroSeconds % 60
                Text(
                    String.format("%02d:%02d", mins, secs),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
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
                                pomodoroRunning = false
                            } else {
                                pomodoroRunning = true
                                pomodoroStartedAt = java.time.Instant.now().toString()
                                scope.launch {
                                    while (pomodoroRunning && pomodoroSeconds > 0) {
                                        delay(1000)
                                        if (pomodoroRunning) {
                                            pomodoroSeconds--
                                            if (pomodoroSeconds <= 0) {
                                                pomodoroRunning = false
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
        Spacer(modifier = Modifier.height(20.dp))
        // Tab switcher: 今日任务 / 学习计划 / 学习统计
        LumoSegmentedControl(
            options = listOf("今日任务", "学习计划", "学习统计"),
            selectedIndex = selectedTab,
            onSelectionChange = { selectedTab = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Tab content inside a card
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
            ) {
                when (selectedTab) {
                    0 -> TodayTasksTab(
                        loading = loading,
                        tasks = tasks,
                        onComplete = { taskId, task ->
                            val newStatus = if (task["status"] == "completed") "pending" else "completed"
                            repo.updateTaskStatus(taskId, newStatus)
                            tasks = repo.getTodayTasks()
                        },
                        onStartPomodoro = { taskId, planId ->
                            pomodoroTaskId = taskId
                            pomodoroPlanId = planId
                        },
                        onCheckin = {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        val completedTaskIds = tasks.filter { it["status"] == "completed" }.mapNotNull { it["id"] }
                                        repo.checkinToday(completedTaskIds)
                                        streak = repo.getStreak()
                                    }
                                } catch (e: Exception) {}
                            }
                        }
                    )
                    1 -> PlansTab(
                        plans = plans,
                        onToggleStatus = { plan ->
                            val newStatus = if (plan["status"] == "active") "paused" else "active"
                            scope.launch {
                                withContext(Dispatchers.IO) { repo.updatePlanStatus(plan["id"]!!, newStatus) }
                                plans = repo.listPlans()
                            }
                        },
                        onCreatePlan = { showCreatePlan = true }
                    )
                    2 -> StatsTab(
                        stats = stats,
                        plans = plans,
                        repo = repo,
                        scope = scope
                    )
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

// ── Tab: 今日任务 ──
@Composable
private fun TodayTasksTab(
    loading: Boolean,
    tasks: List<Map<String, String?>>,
    onComplete: (String, Map<String, String?>) -> Unit,
    onStartPomodoro: (String, String) -> Unit,
    onCheckin: () -> Unit,
) {
    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (tasks.isEmpty()) {
        LumoEmptyState(
            title = "暂无任务",
            message = "去「学习计划」创建一个吧",
            icon = Icons.Filled.Assignment,
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val grouped = tasks.groupBy { it["plan_title"] ?: "未分组" }
            grouped.forEach { (planTitle, planTasks) ->
                Text(
                    planTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
                planTasks.forEach { task ->
                    TaskCard(
                        task = task,
                        onComplete = { onComplete(it, task) },
                        onStartPomodoro = onStartPomodoro
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            val completedTaskIds = tasks.filter { it["status"] == "completed" }.mapNotNull { it["id"] }
            Button(
                onClick = onCheckin,
                modifier = Modifier.fillMaxWidth(),
                enabled = completedTaskIds.isNotEmpty()
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("今日打卡")
            }
        }
    }
}

// ── Tab: 学习计划 ──
@Composable
private fun PlansTab(
    plans: List<Map<String, String?>>,
    onToggleStatus: (Map<String, String?>) -> Unit,
    onCreatePlan: () -> Unit,
) {
    if (plans.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("暂无学习计划", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onCreatePlan) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("创建计划")
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCreatePlan) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("新建")
                }
            }
            plans.forEach { plan ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
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
                            onClick = { onToggleStatus(plan) },
                            label = { Text(plan["status"] ?: "") }
                        )
                    }
                }
            }
        }
    }
}

// ── Tab: 学习统计 ──
@Composable
private fun StatsTab(
    stats: Map<String, Any?>,
    plans: List<Map<String, String?>>,
    repo: LumoRepository,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val totalTime = stats["total_study_time"] as? Int ?: 0
        Text("总学习时长: ${totalTime / 60} 分钟", fontWeight = FontWeight.Medium)

        // 学习趋势（近 7 天）
        Spacer(modifier = Modifier.height(12.dp))
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
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
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
            if (data.isEmpty()) {
                Text("暂无学习记录", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 打卡热力图
        Spacer(modifier = Modifier.height(12.dp))
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
            Spacer(modifier = Modifier.height(12.dp))
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
            if (mastery.isEmpty()) {
                Text("暂无知识点数据", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else null
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
            if (!isCompleted) {
                val planId = task["plan_id"] ?: ""
                IconButton(onClick = { onStartPomodoro(taskId, planId) }) {
                    Icon(Icons.Filled.Timer, contentDescription = "番茄钟")
                }
            }
        }
    }
}
