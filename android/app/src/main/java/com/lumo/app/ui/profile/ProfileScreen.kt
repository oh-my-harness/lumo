package com.lumo.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumo.app.data.LumoRepository
import com.lumo.app.ui.plans.CreatePlanScreen
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen() {
    val vm: ProfileViewModel = viewModel { ProfileViewModel(LumoRepository.get()) }
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var testResult by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.load() }

    if (uiState.showConfig) {
        ModelConfigScreen(
            currentConfig = uiState.config,
            onSave = { type, key, url, model ->
                vm.saveConfig(type, key, url, model)
            },
            onTest = { type, key, url, model ->
                scope.launch {
                    testResult = "测试中..."
                    try {
                        testResult = vm.testConnection(type, key, url, model)
                    } catch (e: Exception) {
                        testResult = "错误: ${e.message}"
                    }
                }
            },
            testResult = testResult,
            onBack = { vm.showConfig(false) }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("我的", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            SectionHeader("模型配置")
            Card(
                modifier = Modifier.fillMaxWidth().clickable { vm.showConfig(true) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(if (uiState.config != null) "已配置" else "未配置", fontWeight = FontWeight.Medium)
                        if (uiState.config != null) {
                            Text(
                                "${uiState.config!!.provider_type} / ${uiState.config!!.model}",
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
    currentConfig: com.lumo.app.data.dto.ProviderConfigDto?,
    onSave: (String, String, String, String) -> Unit,
    onTest: (String, String, String, String) -> Unit,
    testResult: String,
    onBack: () -> Unit
) {
    var providerType by remember { mutableStateOf(currentConfig?.provider_type ?: "openai") }
    var apiKey by remember { mutableStateOf(currentConfig?.api_key ?: "") }
    var baseUrl by remember { mutableStateOf(currentConfig?.base_url ?: "") }
    var model by remember { mutableStateOf(currentConfig?.model ?: "gpt-4o") }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp)) {
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
                onClick = { onTest(providerType, apiKey, baseUrl, model) },
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
