package com.lumo.app.ui.quiz
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumo.app.data.LumoRepository
import com.lumo.app.data.dto.GradeResultDto
import com.lumo.app.data.dto.QuestionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuizViewModel(private val repo: LumoRepository) : ViewModel() {

    data class QuizUiState(
        val loading: Boolean = true,
        val questions: List<QuestionDto> = emptyList(),
        val searchQuery: String = "",
        val filterKp: String? = null,
        val quizMode: Boolean = false,
        val currentIndex: Int = 0,
        val selectedAnswers: Map<String, String> = emptyMap(),
        val gradeResults: Map<String, GradeResultDto> = emptyMap(),
    )

    data class GenDialogState(
        val showGenDialog: Boolean = false,
        val genTopic: String = "",
        val genCount: String = "3",
        val generating: Boolean = false,
        val genResult: String = "",
    )

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private val _genDialog = MutableStateFlow(GenDialogState())
    val genDialog: StateFlow<GenDialogState> = _genDialog.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val questions = withContext(Dispatchers.IO) { repo.getQuizQuestions() }
                _uiState.update { st ->
                    st.copy(
                        questions = questions,
                        loading = false,
                        currentIndex = if (st.currentIndex >= questions.size) 0 else st.currentIndex,
                    )
                }
            } catch (e: Exception) {
                Log.e("LumoQuiz", "refresh failed", e)
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun generateQuiz(topic: String, count: Int) {
        viewModelScope.launch {
            _genDialog.update { it.copy(generating = true, genResult = "正在生成...") }
            try {
                withContext(Dispatchers.IO) {
                    repo.generateQuiz(topic, count)
                }
                _genDialog.update {
                    it.copy(
                        generating = false,
                        genResult = "已生成 $count 道题",
                        showGenDialog = false,
                        genTopic = "",
                    )
                }
                _uiState.update {
                    it.copy(
                        currentIndex = 0,
                        selectedAnswers = emptyMap(),
                        gradeResults = emptyMap(),
                    )
                }
                refresh()
            } catch (e: Exception) {
                Log.e("LumoQuiz", "generateQuiz failed", e)
                _genDialog.update {
                    it.copy(generating = false, genResult = "错误: ${e.message}")
                }
            }
        }
    }

    fun gradeAnswer(qid: String, answer: String) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { repo.gradeAnswer(qid, answer) }
                _uiState.update { it.copy(gradeResults = it.gradeResults + (qid to result)) }
            } catch (e: Exception) {
                Log.e("LumoQuiz", "gradeAnswer failed", e)
            }
        }
    }

    fun resetAnswer(qid: String) {
        _uiState.update {
            it.copy(
                selectedAnswers = it.selectedAnswers - qid,
                gradeResults = it.gradeResults - qid,
            )
        }
    }

    fun setQuizMode(enabled: Boolean) {
        _uiState.update {
            if (enabled) {
                it.copy(
                    quizMode = true,
                    currentIndex = 0,
                    selectedAnswers = emptyMap(),
                    gradeResults = emptyMap(),
                )
            } else {
                it.copy(
                    quizMode = false,
                    selectedAnswers = emptyMap(),
                    gradeResults = emptyMap(),
                    currentIndex = 0,
                )
            }
        }
    }

    fun setFilterKp(kp: String?) {
        _uiState.update { it.copy(filterKp = kp) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setCurrentIndex(index: Int) {
        _uiState.update { it.copy(currentIndex = index) }
    }

    fun startQuizAt(qid: String) {
        _uiState.update { st ->
            val idx = st.questions.indexOfFirst { it.id == qid }
            if (idx >= 0) st.copy(currentIndex = idx, quizMode = true) else st
        }
    }

    fun onAnswerSelected(qid: String, answer: String) {
        _uiState.update { it.copy(selectedAnswers = it.selectedAnswers + (qid to answer)) }
    }

    fun showGenDialog(show: Boolean) {
        _genDialog.update { it.copy(showGenDialog = show) }
    }

    fun setGenTopic(topic: String) {
        _genDialog.update { it.copy(genTopic = topic) }
    }

    fun setGenCount(count: String) {
        _genDialog.update { it.copy(genCount = count.filter { c -> c.isDigit() }) }
    }

    companion object {
        fun factory(repo: LumoRepository) = viewModelFactory {
            initializer { QuizViewModel(repo) }
        }
    }
}
