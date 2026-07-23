package com.lumo.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.lumo.app.data.LumoRepository
import com.lumo.app.data.dto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

data class TodayUiState(
    val loading: Boolean = true,
    val tasks: List<TaskDto> = emptyList(),
    val streak: Int = 0,
    val totalStudyMinutes: Int = 0,
    val plans: List<PlanDto> = emptyList(),
    val stats: StatsDto = StatsDto(),
    val error: String? = null,
    val studyTrend: StudyTrendDto? = null,
    val checkinHeatmap: List<CheckinDayDto> = emptyList(),
    val knowledgeMastery: List<KnowledgePointDto> = emptyList(),
)

data class PomodoroState(
    val running: Boolean = false,
    val seconds: Int = 25 * 60,
    val duration: Int = 25,
    val taskId: String? = null,
    val planId: String? = null,
    val startedAt: String = "",
)

class TodayViewModel(private val repo: LumoRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    private val _pomodoro = MutableStateFlow(PomodoroState())
    val pomodoro: StateFlow<PomodoroState> = _pomodoro.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private var pomodoroJob: Job? = null

    init {
        load()
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val tasks = withContext(Dispatchers.IO) { repo.getTodayTasks() }
                val streak = withContext(Dispatchers.IO) { repo.getStreak() }
                val totalStudy = withContext(Dispatchers.IO) { repo.getTotalStudyTime() }
                val plans = withContext(Dispatchers.IO) { repo.listPlans() }
                val stats = withContext(Dispatchers.IO) { repo.getStats() }
                val trend = withContext(Dispatchers.IO) { repo.getStudyTrend("week") }
                val month = LocalDate.now().toString().take(7)
                val heatmap = withContext(Dispatchers.IO) { repo.getCheckinHeatmap(month) }
                val mastery = if (plans.isNotEmpty()) {
                    withContext(Dispatchers.IO) { repo.getKnowledgeMastery(plans.first().id) }
                } else emptyList()

                _uiState.update {
                    it.copy(
                        loading = false,
                        tasks = tasks,
                        streak = streak,
                        totalStudyMinutes = totalStudy,
                        plans = plans,
                        stats = stats,
                        studyTrend = trend,
                        checkinHeatmap = heatmap,
                        knowledgeMastery = mastery,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun toggleTaskComplete(taskId: String, currentStatus: String) {
        val newStatus = if (currentStatus == "completed") "pending" else "completed"
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.updateTaskStatus(taskId, newStatus) }
            val tasks = withContext(Dispatchers.IO) { repo.getTodayTasks() }
            _uiState.update { it.copy(tasks = tasks) }
        }
    }

    fun startPomodoro(taskId: String?, planId: String?) {
        pomodoroJob?.cancel()
        val now = Instant.now().toString()
        _pomodoro.update { it.copy(
            running = true,
            seconds = it.duration * 60,
            taskId = taskId,
            planId = planId,
            startedAt = now,
        ) }
        startTimerLoop()
    }

    fun togglePomodoro() {
        if (_pomodoro.value.running) {
            stopPomodoro()
        } else {
            _pomodoro.update { it.copy(
                running = true,
                startedAt = Instant.now().toString(),
            ) }
            if (_pomodoro.value.seconds <= 0) {
                _pomodoro.update { it.copy(seconds = it.duration * 60) }
            }
            startTimerLoop()
        }
    }

    fun stopPomodoro() {
        pomodoroJob?.cancel()
        _pomodoro.update { it.copy(running = false) }
    }

    fun resetPomodoro() {
        pomodoroJob?.cancel()
        _pomodoro.update { it.copy(running = false, seconds = it.duration * 60) }
    }

    fun setPomodoroDuration(mins: Int) {
        if (!_pomodoro.value.running) {
            _pomodoro.update { it.copy(duration = mins, seconds = mins * 60) }
        }
    }

    private fun startTimerLoop() {
        pomodoroJob = viewModelScope.launch {
            while (_pomodoro.value.running && _pomodoro.value.seconds > 0) {
                delay(1000)
                if (_pomodoro.value.running) {
                    _pomodoro.update { it.copy(seconds = it.seconds - 1) }
                    if (_pomodoro.value.seconds <= 0) {
                        _pomodoro.update { it.copy(running = false) }
                        val p = _pomodoro.value
                        if (p.taskId != null && p.planId != null) {
                            withContext(Dispatchers.IO) {
                                repo.recordPomodoro(p.taskId, p.planId, p.duration * 60, p.startedAt)
                            }
                            val total = withContext(Dispatchers.IO) { repo.getTotalStudyTime() }
                            _uiState.update { it.copy(totalStudyMinutes = total) }
                        }
                    }
                }
            }
        }
    }

    fun checkin() {
        viewModelScope.launch {
            try {
                val completedTaskIds = _uiState.value.tasks
                    .filter { it.status == "completed" }
                    .map { it.id }
                withContext(Dispatchers.IO) { repo.checkinToday(completedTaskIds) }
                val streak = withContext(Dispatchers.IO) { repo.getStreak() }
                _uiState.update { it.copy(streak = streak) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun togglePlanStatus(planId: String, currentStatus: String) {
        val newStatus = if (currentStatus == "active") "paused" else "active"
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.updatePlanStatus(planId, newStatus) }
            refreshPlans()
        }
    }

    fun refreshPlans() {
        viewModelScope.launch {
            try {
                val plans = withContext(Dispatchers.IO) { repo.listPlans() }
                _uiState.update { it.copy(plans = plans) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /** Creates a chat session for the given task and returns the session id. */
    suspend fun startChatForTask(taskId: String): String? = withContext(Dispatchers.IO) {
        try {
            val sid = repo.createSession("新对话")
            repo.startChatWithTask(sid, taskId)
            sid
        } catch (e: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        pomodoroJob?.cancel()
    }

    companion object {
        fun factory(repo: LumoRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { TodayViewModel(repo) }
        }
    }
}
