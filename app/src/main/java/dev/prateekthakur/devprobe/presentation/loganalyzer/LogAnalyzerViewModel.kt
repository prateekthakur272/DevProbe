package dev.prateekthakur.devprobe.presentation.loganalyzer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.prateekthakur.devprobe.data.ai.AiExplanation
import dev.prateekthakur.devprobe.data.report.ReportExporter
import dev.prateekthakur.devprobe.data.repository.AnalysisRepository
import dev.prateekthakur.devprobe.domain.model.AppError
import dev.prateekthakur.devprobe.domain.model.CrashReport
import dev.prateekthakur.devprobe.domain.model.LogAnalysisResult
import dev.prateekthakur.devprobe.domain.usecase.ExplainCrashUseCase
import dev.prateekthakur.devprobe.domain.usecase.ParseLogUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LogAnalyzerUiState {
    data object Idle : LogAnalyzerUiState
    data object Analyzing : LogAnalyzerUiState
    data class Success(
        val result: LogAnalysisResult,
        val explanations: Map<Int, AiExplanation> = emptyMap(),
        val explainingIndex: Int? = null,
    ) : LogAnalyzerUiState
    data class Error(val error: AppError) : LogAnalyzerUiState
}

class LogAnalyzerViewModel(
    private val parseLogUseCase: ParseLogUseCase,
    private val explainCrashUseCase: ExplainCrashUseCase,
    private val analysisRepository: AnalysisRepository,
    private val reportExporter: ReportExporter,
    private val nowProvider: () -> Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LogAnalyzerUiState>(LogAnalyzerUiState.Idle)
    val uiState: StateFlow<LogAnalyzerUiState> = _uiState.asStateFlow()

    private var lastPackageHint: String? = null

    fun analyze(text: String, packageHint: String?) {
        if (text.isBlank()) {
            _uiState.value = LogAnalyzerUiState.Error(AppError.emptyInput())
            return
        }
        lastPackageHint = packageHint?.takeIf { it.isNotBlank() }
        _uiState.value = LogAnalyzerUiState.Analyzing
        viewModelScope.launch {
            try {
                val result = parseLogUseCase(text, packageHint?.takeIf { it.isNotBlank() })
                _uiState.value = LogAnalyzerUiState.Success(result)
                analysisRepository.saveLogAnalysis(
                    title = result.crashReports.firstOrNull()?.exceptionType ?: "Log Analysis",
                    result = result,
                    createdAt = nowProvider(),
                    packageName = packageHint,
                )
            } catch (e: Exception) {
                _uiState.value = LogAnalyzerUiState.Error(AppError.unexpected(e.message ?: "unknown error"))
            }
        }
    }

    fun explainCrash(index: Int, crash: CrashReport, appPackage: String?) {
        val current = _uiState.value
        if (current !is LogAnalyzerUiState.Success) return
        _uiState.update { (it as LogAnalyzerUiState.Success).copy(explainingIndex = index) }
        viewModelScope.launch {
            val explanation = explainCrashUseCase(crash, appPackage)
            _uiState.update {
                val success = it as? LogAnalyzerUiState.Success ?: return@update it
                success.copy(
                    explanations = success.explanations + (index to explanation),
                    explainingIndex = null,
                )
            }
        }
    }

    /** Renders the current result to PDF on a background thread and returns a shareable content Uri. */
    suspend fun exportPdfReport(): Uri? {
        val current = _uiState.value as? LogAnalyzerUiState.Success ?: return null
        return withContext(Dispatchers.IO) {
            val title = current.result.crashReports.firstOrNull()?.exceptionType?.substringAfterLast('.') ?: "Log Analysis"
            reportExporter.exportLogReport(
                title = "Crash Log Report — $title",
                subtitle = lastPackageHint ?: "",
                result = current.result,
            )
        }
    }

    fun reset() {
        _uiState.value = LogAnalyzerUiState.Idle
    }
}
