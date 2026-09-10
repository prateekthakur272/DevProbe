package dev.prateekthakur.devprobe.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.prateekthakur.devprobe.data.repository.AnalysisRepository
import dev.prateekthakur.devprobe.domain.model.AnalysisSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(repository: AnalysisRepository) : ViewModel() {
    val recentSessions: StateFlow<List<AnalysisSession>> = repository.observeRecent(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
