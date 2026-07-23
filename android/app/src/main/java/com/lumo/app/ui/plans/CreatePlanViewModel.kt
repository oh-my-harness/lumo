package com.lumo.app.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.lumo.app.data.LumoRepository
import com.lumo.app.data.dto.PlanGenerationResultDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CreatePlanUiState(
    val goal: String = "",
    val dailyMinutes: String = "60",
    val generating: Boolean = false,
    val resultMessage: String = "",
    val error: String? = null,
)

class CreatePlanViewModel(private val repo: LumoRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CreatePlanUiState())
    val uiState: StateFlow<CreatePlanUiState> = _uiState.asStateFlow()

    fun updateGoal(goal: String) {
        _uiState.update { it.copy(goal = goal) }
    }

    fun updateDailyMinutes(minutes: String) {
        _uiState.update { it.copy(dailyMinutes = minutes.filter { c -> c.isDigit() }) }
    }

    fun generatePlan(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.goal.isBlank() || state.generating) return
        viewModelScope.launch {
            _uiState.update { it.copy(generating = true, resultMessage = "AI 正在生成计划...") }
            try {
                val mins = state.dailyMinutes.toIntOrNull() ?: 60
                val result = withContext(Dispatchers.IO) {
                    repo.generatePlan(state.goal, mins)
                }
                val weekCount = result.weeks?.size ?: 0
                _uiState.update {
                    it.copy(
                        generating = false,
                        resultMessage = "计划已生成！$weekCount 周大纲已创建",
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        generating = false,
                        resultMessage = "错误: ${e.message}",
                        error = e.message,
                    )
                }
            }
        }
    }

    companion object {
        fun factory(repo: LumoRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { CreatePlanViewModel(repo) }
        }
    }
}
