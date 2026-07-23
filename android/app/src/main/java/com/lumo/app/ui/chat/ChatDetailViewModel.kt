package com.lumo.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lumo.app.data.LumoRepository
import com.lumo.app.data.dto.MessageDto
import com.lumo.app.data.dto.QuestionDto
import com.lumo.app.data.dto.QuickPromptDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatDetailUiState(
    val messages: List<MessageDto> = emptyList(),
    val inputText: String = "",
    val loading: Boolean = false,
    val chatStarted: Boolean = false,
    val quickPrompts: List<QuickPromptDto> = emptyList(),
    val error: String? = null,
    // Quiz overlay state
    val quizQuestions: List<QuestionDto> = emptyList(),
    val showQuiz: Boolean = false,
    val generatingQuiz: Boolean = false,
    val quizTopic: String = "",
    val showQuizGenDialog: Boolean = false,
    // Save note state
    val showSaveNoteDialog: Boolean = false,
    val savingNote: Boolean = false,
    val noteSaved: Boolean = false,
)

class ChatDetailViewModel(
    private val repo: LumoRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatDetailUiState())
    val uiState: StateFlow<ChatDetailUiState> = _uiState.asStateFlow()

    fun load(sessionId: String) {
        viewModelScope.launch {
            try {
                val msgs = withContext(Dispatchers.IO) { repo.getMessages(sessionId) }
                val prompts = withContext(Dispatchers.IO) { repo.getQuickPrompts() }
                _uiState.update {
                    it.copy(
                        messages = msgs,
                        quickPrompts = prompts,
                        chatStarted = msgs.isNotEmpty(),
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "加载失败") }
            }
        }
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun startChatIfNeeded(sessionId: String, onStarted: () -> Unit = {}) {
        if (_uiState.value.chatStarted) {
            onStarted()
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.startChat(sessionId) }
                _uiState.update { it.copy(chatStarted = true) }
                onStarted()
            } catch (e: Exception) {
                // Surface the error as an assistant message so the user sees it
                // (matches pre-refactor behavior).
                val errMsg = MessageDto(id = "a-err-${nextLocalId()}", session_id = "", role = "assistant", content = "错误: ${e.message}")
                _uiState.update { it.copy(messages = it.messages + errMsg, error = e.message ?: "无法开始对话") }
            }
        }
    }

    /**
     * Streams a chat message. Adds the user message + an empty assistant message,
     * then updates the assistant content as tokens arrive. The full response is
     * committed at the end to guard against any missed token callback.
     */
    fun streamMessage(text: String) {
        if (text.isBlank()) return
        _uiState.update { it.copy(loading = true, error = null) }

        // Append user + empty assistant message synchronously.
        val userMsg = MessageDto(id = "u-${nextLocalId()}", session_id = "", role = "user", content = text)
        val aiMsg = MessageDto(id = "a-${nextLocalId()}", session_id = "", role = "assistant", content = "")
        val base = _uiState.value.messages + listOf(userMsg, aiMsg)
        val aiIndex = base.size - 1
        _uiState.update { it.copy(messages = base) }

        viewModelScope.launch {
            try {
                val fullResponse = withContext(Dispatchers.IO) {
                    repo.streamChat(text) { token ->
                        val current = _uiState.value.messages.getOrNull(aiIndex)?.content ?: return@streamChat
                        _uiState.update { s ->
                            val updated = s.messages.toMutableList()
                            if (updated.size > aiIndex) {
                                updated[aiIndex] = updated[aiIndex].copy(content = current + token)
                            }
                            s.copy(messages = updated)
                        }
                    }
                }
                // Ensure final content is complete.
                _uiState.update { s ->
                    val updated = s.messages.toMutableList()
                    if (updated.size > aiIndex) {
                        updated[aiIndex] = updated[aiIndex].copy(content = fullResponse)
                    }
                    s.copy(messages = updated)
                }
            } catch (e: Exception) {
                val errMsg = "错误: ${e.message}"
                _uiState.update { s ->
                    val updated = s.messages.toMutableList()
                    if (updated.size > aiIndex) {
                        updated[aiIndex] = updated[aiIndex].copy(content = errMsg)
                    } else {
                        updated.add(MessageDto(id = "a-err", session_id = "", role = "assistant", content = errMsg))
                    }
                    s.copy(messages = updated)
                }
            } finally {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun abortChat() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { repo.abortChat() } }
            _uiState.update { it.copy(loading = false) }
        }
    }

    fun saveConversationAsNote(sessionId: String) {
        _uiState.update { it.copy(savingNote = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.saveConversationAsNote(sessionId) }
                _uiState.update { it.copy(savingNote = false, noteSaved = true, showSaveNoteDialog = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(savingNote = false, error = e.message ?: "保存失败") }
            }
        }
    }

    fun generateQuiz(topic: String, count: Int = 3) {
        if (topic.isBlank()) return
        _uiState.update { it.copy(generatingQuiz = true) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { repo.generateQuiz(topic, count) }
                // generate_quiz persists questions server-side; load them.
                val questions = withContext(Dispatchers.IO) {
                    runCatching { repo.getQuizQuestions() }.getOrDefault(emptyList())
                }
                if (questions.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            generatingQuiz = false,
                            quizQuestions = questions.take(count),
                            showQuiz = true,
                            showQuizGenDialog = false,
                            quizTopic = "",
                        )
                    }
                } else {
                    _uiState.update { it.copy(generatingQuiz = false) }
                }
            } catch (e: Exception) {
                // Fallback: load from quiz bank
                val questions = withContext(Dispatchers.IO) {
                    runCatching { repo.getQuizQuestions() }.getOrDefault(emptyList())
                }
                _uiState.update {
                    it.copy(
                        generatingQuiz = false,
                        quizQuestions = questions.take(count),
                        showQuiz = questions.isNotEmpty(),
                        showQuizGenDialog = false,
                        quizTopic = "",
                    )
                }
            }
        }
    }

    // ── Simple state mutators for the screen ──

    fun setShowQuiz(v: Boolean) = _uiState.update { it.copy(showQuiz = v, quizQuestions = if (!v) emptyList() else it.quizQuestions) }
    fun setShowQuizGenDialog(v: Boolean) = _uiState.update { it.copy(showQuizGenDialog = v) }
    fun setShowSaveNoteDialog(v: Boolean) = _uiState.update { it.copy(showSaveNoteDialog = v) }
    fun setQuizTopic(v: String) = _uiState.update { it.copy(quizTopic = v) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    private var localIdCounter = 0
    private fun nextLocalId(): Int = localIdCounter++

    companion object {
        fun factory(repo: LumoRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass == ChatDetailViewModel::class.java)
                    return ChatDetailViewModel(repo) as T
                }
            }
    }
}
