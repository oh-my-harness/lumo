package com.lumo.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumo.app.data.LumoRepository
import com.lumo.app.data.dto.CheckinDayDto
import com.lumo.app.data.dto.KnowledgePointDto
import com.lumo.app.data.dto.PlanDto
import com.lumo.app.data.dto.StatsDto
import com.lumo.app.data.dto.StudyTrendDto
import com.lumo.app.data.dto.TaskDto
import com.lumo.app.ui.components.LumoStatsCard
import com.lumo.app.ui.components.LumoSegmentedControl
import com.lumo.app.ui.components.LumoEmptyState
import com.lumo.app.ui.plans.CreatePlanScreen
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    navController: androidx.navigation.NavController,
    viewModel: TodayViewModel = viewModel { TodayViewModel(LumoRepository.get()) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pomodoro by viewModel.pomodoro.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    var showCreatePlan by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header: streak + study time
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LumoStatsCard(
                label = "连续打卡",
                value = "${uiState.streak} 天",
                icon = Icons.Filled.LocalFireDepartment,
                accentColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
            LumoStatsCard(
                label = "学习时长",
                value = "${uiState.totalStudyMinutes / 60} 分钟",
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
                val mins = pomodoro.seconds / 60
                val secs = pomodoro.seconds % 60
                Text(
                    String.format("%02d:%02d", mins, secs),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15 to "15+5", 25 to "25+5", 50 to "50+10").forEach { (mins, label) ->
                        FilterChip(
                            selected = pomodoro.duration == mins,
                            onClick = { viewModel.setPomodoroDuration(mins) },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.togglePomodoro() }
                    ) {
                        Icon(
                            if (pomodoro.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (pomodoro.running) "暂停" else "开始")
                    }
                    if (pomodoro.running) {
                        OutlinedButton(onClick = { viewModel.resetPomodoro() }) { Text("重置") }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        // Tab switcher: 今日任务 / 学习计划 / 学习统计
        LumoSegmentedControl(
            options = listOf("今日任务", "学习计划", "学习统计"),
            selectedIndex = selectedTab,
            onSelectionChange = { viewModel.selectTab(it) },
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
                        loading = uiState.loading,
                        tasks = uiState.tasks,
                        onComplete = { task ->
                            viewModel.toggleTaskComplete(task.id, task.status)
                        },
                        onStartPomodoro = { taskId, planId ->
                            viewModel.startPomodoro(taskId, planId)
                            // Create a chat session for this task and navigate
                            scope.launch {
                                val sessionId = viewModel.startChatForTask(taskId)
                                if (sessionId != null) {
                                    navController.navigate("chat/$sessionId")
                                }
                            }
                        },
                        onCheckin = { viewModel.checkin() }
                    )
                    1 -> PlansTab(
                        plans = uiState.plans,
                        onToggleStatus = { plan ->
                            viewModel.togglePlanStatus(plan.id, plan.status)
                        },
                        onCreatePlan = { showCreatePlan = true }
                    )
                    2 -> StatsTab(
                        stats = uiState.stats,
                        trend = uiState.studyTrend,
                        heatmap = uiState.checkinHeatmap,
                        plans = uiState.plans,
                        mastery = uiState.knowledgeMastery,
                    )
                }
            }
        }
    }

    // Create plan dialog
    if (showCreatePlan) {
        Dialog(onDismissRequest = { showCreatePlan = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                CreatePlanScreen(
                    onSave = {
                        viewModel.refreshPlans()
                        showCreatePlan = false
                    },
                    onBack = { showCreatePlan = false }
                )
            }
        }
    }
}

// ── Tab: 今日任务 ──
@Composable
private fun TodayTasksTab(
    loading: Boolean,
    tasks: List<TaskDto>,
    onComplete: (TaskDto) -> Unit,
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
            val grouped = tasks.groupBy { it.plan_title ?: "未分组" }
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
                        onComplete = { onComplete(task) },
                        onStartPomodoro = onStartPomodoro
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            val hasCompleted = tasks.any { it.status == "completed" }
            Button(
                onClick = onCheckin,
                modifier = Modifier.fillMaxWidth(),
                enabled = hasCompleted
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
    plans: List<PlanDto>,
    onToggleStatus: (PlanDto) -> Unit,
    onCreatePlan: () -> Unit,
) {
    val repo = LumoRepository.get()
    val scope = rememberCoroutineScope()
    var expandedPlanId by remember { mutableStateOf<String?>(null) }
    var planTasks by remember { mutableStateOf<List<TaskDto>>(emptyList()) }
    var loadingTasks by remember { mutableStateOf(false) }

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
                val planId = plan.id
                val isActive = plan.status == "active"
                val isExpanded = expandedPlanId == planId
                val completedCount = planTasks.count { it.status == "completed" }
                val totalCount = planTasks.size

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(plan.title, fontWeight = FontWeight.Medium)
                                Text(
                                    plan.goal,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                if (totalCount > 0) {
                                    Text(
                                        "$completedCount / $totalCount 任务完成",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            AssistChip(
                                onClick = { onToggleStatus(plan) },
                                label = { Text(if (isActive) "进行中" else "暂停") }
                            )
                            IconButton(onClick = {
                                if (isExpanded) {
                                    expandedPlanId = null
                                } else {
                                    expandedPlanId = planId
                                    loadingTasks = true
                                    scope.launch {
                                        try {
                                            planTasks = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                repo.getPlanTasks(planId)
                                            }
                                        } catch (e: Exception) {}
                                        loadingTasks = false
                                    }
                                }
                            }) {
                                Icon(
                                    if (isExpanded) Icons.Filled.ExpandLess
                                    else Icons.Filled.ExpandMore,
                                    contentDescription = if (isExpanded) "收起" else "展开"
                                )
                            }
                        }
                        if (isExpanded) {
                            if (totalCount > 0) {
                                LinearProgressIndicator(
                                    progress = { completedCount.toFloat() / totalCount },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                                )
                            }
                            if (loadingTasks) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                }
                            } else {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Group tasks by week
                                    val grouped = planTasks.groupBy { it.week_num.toString() }
                                    grouped.toSortedMap(compareBy { it.toIntOrNull() ?: 1 }).forEach { (weekNum, weekTasks) ->
                                        Text(
                                            "第 $weekNum 周",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                        )
                                        weekTasks.forEach { task ->
                                            val isCompleted = task.status == "completed"
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    if (isCompleted) Icons.Filled.CheckCircle
                                                    else Icons.Filled.RadioButtonUnchecked,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (isCompleted)
                                                        MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    task.title,
                                                    fontSize = 13.sp,
                                                    textDecoration = if (isCompleted)
                                                        TextDecoration.LineThrough else null,
                                                    color = if (isCompleted)
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                    if (planTasks.isEmpty()) {
                                        Text(
                                            "暂无任务",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 8.dp)
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

// ── Tab: 学习统计 ──
@Composable
private fun StatsTab(
    stats: StatsDto,
    trend: StudyTrendDto?,
    heatmap: List<CheckinDayDto>,
    plans: List<PlanDto>,
    mastery: List<KnowledgePointDto>,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("总学习时长: ${stats.total_study_time / 60} 分钟", fontWeight = FontWeight.Medium)

        // 学习趋势（近 7 天）
        Spacer(modifier = Modifier.height(12.dp))
        Text("近 7 天学习时长", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        trend?.let { t ->
            val data = t.data
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
        Text("打卡 ${heatmap.size} 天", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        // 知识点掌握度
        if (plans.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("知识点掌握度", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            mastery.forEach { kp ->
                val level = kp.mastery_level
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(kp.name, fontSize = 12.sp)
                    LinearProgressIndicator(
                        progress = { level.toFloat() / 100f },
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
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskDto,
    onComplete: (String) -> Unit,
    onStartPomodoro: (String, String) -> Unit = { _, _ -> }
) {
    val isCompleted = task.status == "completed"
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
                onCheckedChange = { onComplete(task.id) }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.title,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else null
                )
                if (!task.description.isNullOrEmpty()) {
                    Text(
                        task.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
            if (!isCompleted) {
                val planId = task.plan_id
                OutlinedButton(
                    onClick = { onStartPomodoro(task.id, planId) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("开始", fontSize = 12.sp)
                }
            }
        }
    }
}
