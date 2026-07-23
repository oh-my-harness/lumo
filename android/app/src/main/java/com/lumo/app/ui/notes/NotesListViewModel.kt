package com.lumo.app.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumo.app.data.LumoRepository
import com.lumo.app.data.dto.NoteDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NotesListUiState(
    val loading: Boolean = true,
    val notes: List<NoteDto> = emptyList(),
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val processing: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<NoteDto> = emptyList(),
    val resultMessage: String? = null,
)

class NotesListViewModel(
    private val repo: LumoRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotesListUiState())
    val uiState: StateFlow<NotesListUiState> = _uiState.asStateFlow()

    fun load() {
        _uiState.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val notes = withContext(Dispatchers.IO) { repo.listNotes() }
                _uiState.update { it.copy(loading = false, notes = notes) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.IO) { repo.searchNotes(query) }
                _uiState.update { it.copy(searchResults = results) }
            } catch (e: Exception) {
                _uiState.update { it.copy(searchResults = emptyList()) }
            }
        }
    }

    fun toggleSelection(id: String) {
        _uiState.update { state ->
            val newIds = if (state.selectedIds.contains(id)) {
                state.selectedIds - id
            } else {
                state.selectedIds + id
            }
            state.copy(
                selectedIds = newIds,
                selectionMode = newIds.isNotEmpty() || state.selectionMode,
            )
        }
    }

    fun selectAll() {
        _uiState.update { it.copy(selectedIds = it.notes.map { n -> n.id }.toSet()) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet(), selectionMode = false) }
    }

    fun enterSelectionMode(id: String) {
        _uiState.update { it.copy(selectionMode = true, selectedIds = it.selectedIds + id) }
    }

    fun summarizeSelected() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        _uiState.update { it.copy(processing = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.summarizeNotes(ids) }
                val notes = withContext(Dispatchers.IO) { repo.listNotes() }
                _uiState.update {
                    it.copy(
                        processing = false,
                        notes = notes,
                        selectedIds = emptySet(),
                        selectionMode = false,
                        resultMessage = "汇总完成，已生成新笔记",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(processing = false, resultMessage = "汇总失败: ${e.message}")
                }
            }
        }
    }

    fun deleteSelected() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        _uiState.update { it.copy(processing = true) }
        viewModelScope.launch {
            try {
                for (id in ids) {
                    withContext(Dispatchers.IO) { repo.deleteNote(id) }
                }
                val notes = withContext(Dispatchers.IO) { repo.listNotes() }
                _uiState.update {
                    it.copy(
                        processing = false,
                        notes = notes,
                        selectedIds = emptySet(),
                        selectionMode = false,
                        resultMessage = "已删除 ${ids.size} 篇笔记",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(processing = false, resultMessage = "删除失败: ${e.message}")
                }
            }
        }
    }

    fun summarizeAndDeleteSelected() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        _uiState.update { it.copy(processing = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.summarizeNotes(ids) }
                for (id in ids) {
                    withContext(Dispatchers.IO) { repo.deleteNote(id) }
                }
                val notes = withContext(Dispatchers.IO) { repo.listNotes() }
                _uiState.update {
                    it.copy(
                        processing = false,
                        notes = notes,
                        selectedIds = emptySet(),
                        selectionMode = false,
                        resultMessage = "汇总完成，原笔记已删除",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(processing = false, resultMessage = "操作失败: ${e.message}")
                }
            }
        }
    }

    fun consumeResultMessage() {
        _uiState.update { it.copy(resultMessage = null) }
    }

    companion object {
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { NotesListViewModel(LumoRepository.get()) }
        }
    }
}
