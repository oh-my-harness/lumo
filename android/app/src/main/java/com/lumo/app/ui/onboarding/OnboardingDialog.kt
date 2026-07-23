package com.lumo.app.ui.onboarding

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEEPSEEK_TOPUP_URL = "https://platform.deepseek.com/usage"
private const val DEEPSEEK_API_KEYS_URL = "https://platform.deepseek.com/api_keys"

/**
 * First-run onboarding dialog shown when no provider config is set.
 * Guides the user through 3 steps: register → top up → copy API key.
 */
@Composable
fun OnboardingDialog(
    onDismiss: () -> Unit,
    onConfigSaved: () -> Unit,
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(0) }
    var detectedKey by remember { mutableStateOf<String?>(null) }

    // Check clipboard for API key when returning from browser
    LaunchedEffect(step) {
        if (step == 2) {
            detectedKey = detectApiKeyFromClipboard(context)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("欢迎使用 Lumo", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Progress indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    (0..2).forEach { i ->
                        Box(
                            modifier = Modifier.size(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (i < step) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else if (i == step) {
                                Surface(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "${i + 1}",
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "${i + 1}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                        if (i < 2) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                thickness = 2.dp,
                                color = if (i < step) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                when (step) {
                    0 -> StepRegister(context)
                    1 -> StepTopUp(context)
                    2 -> StepApiKey(context, detectedKey, onConfigSaved)
                }
            }
        },
        confirmButton = {
            when (step) {
                0 -> TextButton(onClick = { step = 1 }) { Text("下一步") }
                1 -> TextButton(onClick = { step = 2 }) { Text("下一步") }
                2 -> TextButton(onClick = onDismiss) { Text("跳过") }
            }
        },
        dismissButton = {
            if (step > 0) {
                TextButton(onClick = { step-- }) { Text("上一步") }
            } else {
                TextButton(onClick = onDismiss) { Text("跳过") }
            }
        }
    )
}

@Composable
private fun StepRegister(context: Context) {
    Column {
        Text("第 1 步：注册 DeepSeek 账号", fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "DeepSeek 是国内领先的 AI 大模型平台，价格低、无需翻墙。\n\n" +
            "点击下方按钮注册账号（支持手机号/邮箱注册）。",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { openUrl(context, "https://platform.deepseek.com/register") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("前往注册")
        }
    }
}

@Composable
private fun StepTopUp(context: Context) {
    Column {
        Text("第 2 步：充值 Token", fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "注册完成后，充值任意金额即可获得 Token 额度。\n" +
            "推荐充值 ¥10（约可用数百次对话）。",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { openUrl(context, DEEPSEEK_TOPUP_URL) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("前往充值")
        }
    }
}

@Composable
private fun StepApiKey(
    context: Context,
    detectedKey: String?,
    onConfigSaved: () -> Unit,
) {
    var apiKey by remember { mutableStateOf(detectedKey ?: "") }
    val repo = com.lumo.app.data.LumoRepository.get()
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saveResult by remember { mutableStateOf("") }

    Column {
        Text("第 3 步：创建并粘贴 API Key", fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "在 DeepSeek 平台创建 API Key，复制后粘贴到下方。\n" +
            "也可以直接点击「前往创建」按钮。",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Auto-detected key from clipboard
        if (detectedKey != null && apiKey.isEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("检测到剪贴板中的 API Key", fontSize = 12.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { apiKey = detectedKey }) { Text("填入") }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API Key") },
            placeholder = { Text("sk-...") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { openUrl(context, DEEPSEEK_API_KEYS_URL) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("前往创建 API Key")
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                if (apiKey.isNotBlank()) {
                    scope.launch {
                        saving = true
                        try {
                            withContext(Dispatchers.IO) {
                                // Save with DeepSeek defaults
                                repo.saveProviderConfig(
                                    "openai",
                                    apiKey.trim(),
                                    "https://api.deepseek.com/",
                                    "deepseek-v4-flash"
                                )
                            }
                            saveResult = "配置已保存！"
                            onConfigSaved()
                        } catch (e: Exception) {
                            saveResult = "错误: ${e.message}"
                        }
                        saving = false
                    }
                }
            },
            enabled = apiKey.isNotBlank() && !saving,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (saving) CircularProgressIndicator(modifier = Modifier.size(16.dp))
            else Text("保存并开始使用")
        }

        if (saveResult.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(saveResult, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Open a URL in the system browser. */
private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

/** Detect if the clipboard contains an API key (starts with "sk-"). */
private fun detectApiKeyFromClipboard(context: Context): String? {
    return try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0).coerceToText(context).toString().trim()
        if (text.startsWith("sk-") && text.length > 10) text else null
    } catch (e: Exception) {
        null
    }
}
