package com.lumo.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumo.app.data.LumoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen() {
    val repo = LumoRepository.get()
    var config by remember { mutableStateOf<Map<String, String>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showConfig by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        try {
            config = repo.getProviderConfig()
        } catch (e: Exception) {}
        loading = false
    }

    if (showConfig) {
        ModelConfigScreen(
            currentConfig = config,
            onSave = { type, key, url, model ->
                scope.launch {
                    try {
                        repo.saveProviderConfig(type, key, url, model)
                        config = repo.getProviderConfig()
                    } catch (e: Exception) {}
                    showConfig = false
                }
            },
            onTest = { type, key, url, model ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { repo.testConnection(type, key, url, model) }
                    } catch (e: Exception) {}
                }
            },
            onBack = { showConfig = false }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("我的", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            SectionHeader("模型配置")
            Card(
                modifier = Modifier.fillMaxWidth().clickable { showConfig = true },
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(if (config != null) "已配置" else "未配置", fontWeight = FontWeight.Medium)
                        if (config != null) {
                            Text(
                                "${config!!["provider_type"]} / ${config!!["model"]}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ModelConfigScreen(
    currentConfig: Map<String, String>?,
    onSave: (String, String, String, String) -> Unit,
    onTest: (String, String, String, String) -> Unit,
    onBack: () -> Unit
) {
    var providerType by remember { mutableStateOf(currentConfig?.get("provider_type") ?: "openai") }
    var apiKey by remember { mutableStateOf(currentConfig?.get("api_key") ?: "") }
    var baseUrl by remember { mutableStateOf(currentConfig?.get("base_url") ?: "") }
    var model by remember { mutableStateOf(currentConfig?.get("model") ?: "gpt-4o") }
    var testResult by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("模型配置", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { onSave(providerType, apiKey, baseUrl, model) }) {
                Text("保存")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Provider type
        Text("Provider 类型", fontSize = 14.sp)
        Row {
            FilterChip(
                selected = providerType == "openai",
                onClick = { providerType = "openai" },
                label = { Text("OpenAI 兼容") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = providerType == "anthropic",
                onClick = { providerType = "anthropic" },
                label = { Text("Anthropic") }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API Key") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Base URL（可选）") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("模型名称") },
            singleLine = true
        )
        // Quick setup: DeepSeek one-click
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("没有 API Key？", fontWeight = FontWeight.Medium)
                Text(
                    "一键购买 DeepSeek Token，3 步完成配置",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://platform.deepseek.com/register")
                        )
                        context.startActivity(intent)
                    }) { Text("注册") }
                    OutlinedButton(onClick = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://platform.deepseek.com/usage")
                        )
                        context.startActivity(intent)
                    }) { Text("充值") }
                    OutlinedButton(onClick = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://platform.deepseek.com/api_keys")
                        )
                        context.startActivity(intent)
                    }) { Text("创建 Key") }
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = {
                    // Quick fill DeepSeek defaults
                    providerType = "openai"
                    baseUrl = "https://api.deepseek.com/"
                    model = "deepseek-v4-flash"
                }) { Text("填入 DeepSeek 默认配置") }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        testResult = "测试中..."
                        try {
                            val result = withContext(Dispatchers.IO) {
                                LumoRepository.get().testConnection(providerType, apiKey, baseUrl, model)
                            }
                            testResult = result
                        } catch (e: Exception) {
                            testResult = "错误: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("测试连接")
            }
            Button(
                onClick = { onSave(providerType, apiKey, baseUrl, model) },
                modifier = Modifier.weight(1f)
            ) {
                Text("保存配置")
            }
        }

        if (testResult.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            val isSuccess = testResult.contains("成功") || testResult.contains("success", ignoreCase = true)
            val isError = testResult.startsWith("错误") || testResult.contains("失败") || testResult.contains("error", ignoreCase = true)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isSuccess -> MaterialTheme.colorScheme.primaryContainer
                        isError -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (testResult == "测试中...") {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        testResult,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            isSuccess -> MaterialTheme.colorScheme.onPrimaryContainer
                            isError -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CreatePlanScreen(
    onSave: (String, String, Int, String, String) -> Unit,
    onBack: () -> Unit
) {
    var goal by remember { mutableStateOf("") }
    var dailyMinutes by remember { mutableStateOf("60") }
    var generating by remember { mutableStateOf(false) }
    var genResult by remember { mutableStateOf("") }
    val repo = LumoRepository.get()
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("新建学习计划", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            TextButton(
                onClick = {
                    if (goal.isNotBlank() && !generating) {
                        scope.launch {
                            generating = true
                            genResult = "AI 正在生成计划..."
                            try {
                                val mins = dailyMinutes.toIntOrNull() ?: 60
                                val result = withContext(Dispatchers.IO) {
                                    repo.generatePlan(goal, mins)
                                }
                                @Suppress("UNCHECKED_CAST")
                                val weekCount = (result["weeks"] as? List<*>)?.size ?: 0
                                genResult = "计划已生成！$weekCount 周大纲已创建"
                                generating = false
                                // 刷新列表
                                onSave("", goal, mins, "", "")
                            } catch (e: Exception) {
                                genResult = "错误: ${e.message}"
                                generating = false
                            }
                        }
                    }
                },
                enabled = !generating && goal.isNotBlank()
            ) {
                if (generating) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                else Text("生成")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = goal,
            onValueChange = { goal = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("学习目标") },
            placeholder = { Text("如：2 个月学会前端基础，每天 1 小时") },
            minLines = 2
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = dailyMinutes,
            onValueChange = { dailyMinutes = it.filter { c -> c.isDigit() } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("每日学习时长（分钟）") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))
        if (genResult.isNotEmpty()) {
            Text(genResult, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
