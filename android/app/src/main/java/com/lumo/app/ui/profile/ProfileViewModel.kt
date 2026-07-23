package com.lumo.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumo.app.data.LumoRepository
import com.lumo.app.data.dto.ProviderConfigDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileUiState(
    val loading: Boolean = true,
    val config: ProviderConfigDto? = null,
    val showConfig: Boolean = false,
)

class ProfileViewModel(
    private val repo: LumoRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun load() {
        _uiState.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val config = withContext(Dispatchers.IO) { repo.getProviderConfig() }
                _uiState.update { it.copy(loading = false, config = config) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun showConfig(show: Boolean) {
        _uiState.update { it.copy(showConfig = show) }
    }

    fun saveConfig(type: String, key: String, url: String, model: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.saveProviderConfig(type, key, url, model) }
                val config = withContext(Dispatchers.IO) { repo.getProviderConfig() }
                _uiState.update { it.copy(config = config, showConfig = false) }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    suspend fun testConnection(type: String, key: String, url: String, model: String): String {
        return try {
            withContext(Dispatchers.IO) { repo.testConnection(type, key, url, model) }
        } catch (e: Exception) {
            "错误: ${e.message}"
        }
    }

    companion object {
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ProfileViewModel(LumoRepository.get()) }
        }
    }
}
