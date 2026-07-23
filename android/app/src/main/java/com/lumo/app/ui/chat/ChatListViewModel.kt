package com.lumo.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lumo.app.data.LumoRepository
import com.lumo.app.data.dto.MessageDto
import com.lumo.app.data.dto.SessionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatListUiState(
    val loading: Boolean = true,
    val sessions: List<SessionDto> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<MessageDto> = emptyList(),
)

class ChatListViewModel(private val repo: LumoRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    fun load() {
        _uiState.update { it.copy(loading = true) }
        viewModelScope.launch {
            val sessions = withContext(Dispatchers.IO) {
                runCatching { repo.listSessions() }.getOrDefault(emptyList())
            }
            _uiState.update { it.copy(loading = false, sessions = sessions) }
        }
    }

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                runCatching { repo.searchMessages(query) }.getOrDefault(emptyList())
            }
            _uiState.update { it.copy(searchResults = results) }
        }
    }

    /** Creates a new session and returns its id. */
    fun createSession(): String {
        return runCatching { repo.createSession("新对话") }.getOrElse {
            // Surface error via state; caller should not navigate on failure.
            _uiState.update { it.copy(loading = false) }
            ""
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { repo.deleteSession(id) } }
            val sessions = withContext(Dispatchers.IO) {
                runCatching { repo.listSessions() }.getOrDefault(emptyList())
            }
            _uiState.update { it.copy(sessions = sessions) }
        }
    }

    companion object {
        fun factory(repo: LumoRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass == ChatListViewModel::class.java)
                    return ChatListViewModel(repo) as T
                }
            }
    }
}
