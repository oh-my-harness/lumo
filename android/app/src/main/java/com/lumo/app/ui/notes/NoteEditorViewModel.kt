package com.lumo.app.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumo.app.data.LumoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NoteEditorUiState(
    val title: String = "",
    val content: String = "",
    val previewMode: Boolean = false,
    val loaded: Boolean = false,
    val summarizing: Boolean = false,
)

class NoteEditorViewModel(
    private val repo: LumoRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    fun load(noteId: String?) {
        if (noteId == null) {
            _uiState.update { it.copy(loaded = true) }
            return
        }
        _uiState.update { it.copy(loaded = false) }
        viewModelScope.launch {
            try {
                val notes = withContext(Dispatchers.IO) { repo.listNotes() }
                val note = notes.find { it.id == noteId }
                if (note != null) {
                    _uiState.update {
                        it.copy(
                            title = note.title,
                            content = note.content,
                            previewMode = note.content.isNotBlank(),
                        )
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
            _uiState.update { it.copy(loaded = true) }
        }
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun updateContent(content: String) {
        _uiState.update { it.copy(content = content) }
    }

    fun togglePreview() {
        _uiState.update { it.copy(previewMode = !it.previewMode) }
    }

    fun aiSummarize(noteId: String) {
        if (_uiState.value.summarizing) return
        _uiState.update { it.copy(summarizing = true) }
        viewModelScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) { repo.aiSummarizeNote(noteId) }
                if (summary.isNotBlank()) {
                    _uiState.update { it.copy(content = summary) }
                }
            } catch (e: Exception) {
                // ignore
            }
            _uiState.update { it.copy(summarizing = false) }
        }
    }

    fun save(noteId: String?) {
        val state = _uiState.value
        if (noteId != null) {
            repo.updateNote(noteId, state.title, state.content)
        } else {
            repo.createNote(state.title, state.content)
        }
    }

    companion object {
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { NoteEditorViewModel(LumoRepository.get()) }
        }
    }
}
