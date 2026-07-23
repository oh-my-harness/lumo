package com.lumo.app.ui.plans

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumo.app.data.LumoRepository

@Composable
fun CreatePlanScreen(
    onSave: () -> Unit,
    onBack: () -> Unit,
    viewModel: CreatePlanViewModel = viewModel { CreatePlanViewModel(LumoRepository.get()) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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
                onClick = { viewModel.generatePlan(onSuccess = onSave) },
                enabled = !uiState.generating && uiState.goal.isNotBlank()
            ) {
                if (uiState.generating) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                else Text("生成")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.goal,
            onValueChange = { viewModel.updateGoal(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("学习目标") },
            placeholder = { Text("如：2 个月学会前端基础，每天 1 小时") },
            minLines = 2
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.dailyMinutes,
            onValueChange = { viewModel.updateDailyMinutes(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("每日学习时长（分钟）") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))
        if (uiState.resultMessage.isNotEmpty()) {
            Text(uiState.resultMessage, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
