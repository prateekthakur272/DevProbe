package dev.prateekthakur.devprobe.presentation.profiler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.prateekthakur.devprobe.domain.model.AppProfile
import dev.prateekthakur.devprobe.domain.model.ProfilingTimeRange
import dev.prateekthakur.devprobe.domain.usecase.ProfileInstalledAppsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProfilerUiState {
    data object Loading : ProfilerUiState
    data class Loaded(val profiles: List<AppProfile>, val range: ProfilingTimeRange) : ProfilerUiState
    data class Failed(val message: String) : ProfilerUiState
}

class ProfilerViewModel(private val profileInstalledAppsUseCase: ProfileInstalledAppsUseCase) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfilerUiState>(ProfilerUiState.Loading)
    val uiState: StateFlow<ProfilerUiState> = _uiState.asStateFlow()

    fun load(range: ProfilingTimeRange) {
        _uiState.value = ProfilerUiState.Loading
        viewModelScope.launch {
            runCatching { profileInstalledAppsUseCase.execute(range) }
                .onSuccess { _uiState.value = ProfilerUiState.Loaded(it, range) }
                .onFailure { _uiState.value = ProfilerUiState.Failed(it.message ?: "Failed to profile installed apps") }
        }
    }
}
