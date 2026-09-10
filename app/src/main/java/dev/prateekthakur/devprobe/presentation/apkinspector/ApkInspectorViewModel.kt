package dev.prateekthakur.devprobe.presentation.apkinspector

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.prateekthakur.devprobe.data.ai.AiExplanation
import dev.prateekthakur.devprobe.data.apk.ApkImporter
import dev.prateekthakur.devprobe.data.report.ReportExporter
import dev.prateekthakur.devprobe.data.repository.AnalysisRepository
import dev.prateekthakur.devprobe.domain.model.ApkAnalysisResult
import dev.prateekthakur.devprobe.domain.model.ApkFileEntryDetail
import dev.prateekthakur.devprobe.domain.model.AppError
import dev.prateekthakur.devprobe.domain.model.DexClassInfo
import dev.prateekthakur.devprobe.domain.model.NativeLibraryInfo
import dev.prateekthakur.devprobe.domain.usecase.AnalysisProgress
import dev.prateekthakur.devprobe.domain.usecase.AnalyzeApkUseCase
import dev.prateekthakur.devprobe.domain.usecase.AnalyzeNativeLibrariesUseCase
import dev.prateekthakur.devprobe.domain.usecase.BrowseClassesUseCase
import dev.prateekthakur.devprobe.domain.usecase.ExplainSecurityUseCase
import dev.prateekthakur.devprobe.domain.usecase.ExploreApkFilesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ApkInspectorUiState {
    data object Idle : ApkInspectorUiState
    data class Analyzing(val step: String, val percent: Int) : ApkInspectorUiState
    data class Success(
        val result: ApkAnalysisResult,
        val apkFile: File,
        val aiExplanation: AiExplanation? = null,
        val aiLoading: Boolean = false,
    ) : ApkInspectorUiState
    data class Error(val error: AppError) : ApkInspectorUiState
}

/** Generic tri-state for the on-demand RE tool tabs (Files/Classes/Natives) — each is
 * only computed once, the first time its tab is opened. */
sealed interface ReToolState<out T> {
    data object NotLoaded : ReToolState<Nothing>
    data object Loading : ReToolState<Nothing>
    data class Loaded<T>(val data: T) : ReToolState<T>
    data class Failed(val message: String) : ReToolState<Nothing>
}

class ApkInspectorViewModel(
    private val apkImporter: ApkImporter,
    private val analyzeApkUseCase: AnalyzeApkUseCase,
    private val explainSecurityUseCase: ExplainSecurityUseCase,
    private val analysisRepository: AnalysisRepository,
    private val exploreApkFilesUseCase: ExploreApkFilesUseCase,
    private val browseClassesUseCase: BrowseClassesUseCase,
    private val analyzeNativeLibrariesUseCase: AnalyzeNativeLibrariesUseCase,
    private val reportExporter: ReportExporter,
    private val nowProvider: () -> Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ApkInspectorUiState>(ApkInspectorUiState.Idle)
    val uiState: StateFlow<ApkInspectorUiState> = _uiState.asStateFlow()

    private val _fileTreeState = MutableStateFlow<ReToolState<List<ApkFileEntryDetail>>>(ReToolState.NotLoaded)
    val fileTreeState: StateFlow<ReToolState<List<ApkFileEntryDetail>>> = _fileTreeState.asStateFlow()

    private val _classesState = MutableStateFlow<ReToolState<List<DexClassInfo>>>(ReToolState.NotLoaded)
    val classesState: StateFlow<ReToolState<List<DexClassInfo>>> = _classesState.asStateFlow()

    private val _nativeLibsState = MutableStateFlow<ReToolState<List<NativeLibraryInfo>>>(ReToolState.NotLoaded)
    val nativeLibsState: StateFlow<ReToolState<List<NativeLibraryInfo>>> = _nativeLibsState.asStateFlow()

    private var importedFile: File? = null

    private fun currentApkFile(): File? = (_uiState.value as? ApkInspectorUiState.Success)?.apkFile

    fun onApkSelected(uri: Uri) {
        viewModelScope.launch {
            resetReToolState()
            _uiState.value = ApkInspectorUiState.Analyzing("Importing APK...", 0)
            val file = try {
                apkImporter.importFromUri(uri)
            } catch (e: Exception) {
                _uiState.value = ApkInspectorUiState.Error(AppError.unreadableFile(e.message))
                return@launch
            }
            importedFile = file
            runAnalysis(file)
        }
    }

    private suspend fun runAnalysis(file: File) {
        try {
            analyzeApkUseCase(file).collect { progress ->
                when (progress) {
                    is AnalysisProgress.InProgress -> _uiState.value = ApkInspectorUiState.Analyzing(progress.step, progress.percent)
                    is AnalysisProgress.Done -> {
                        _uiState.value = ApkInspectorUiState.Success(progress.result, file)
                        analysisRepository.saveApkAnalysis(
                            title = progress.result.metadata.appLabel.ifBlank { progress.result.metadata.packageName },
                            result = progress.result,
                            createdAt = nowProvider(),
                            sourceFilePath = file.absolutePath,
                        )
                    }
                    is AnalysisProgress.Failed -> _uiState.value = ApkInspectorUiState.Error(AppError.unexpected(progress.message))
                }
            }
        } catch (e: Exception) {
            _uiState.value = ApkInspectorUiState.Error(AppError.corruptedApk(e.message))
        }
    }

    fun explainSecurityFindings() {
        val current = _uiState.value
        if (current !is ApkInspectorUiState.Success) return
        _uiState.update { (it as ApkInspectorUiState.Success).copy(aiLoading = true) }
        viewModelScope.launch {
            val explanation = explainSecurityUseCase(current.result.securityFindings)
            _uiState.update {
                (it as? ApkInspectorUiState.Success)?.copy(aiExplanation = explanation, aiLoading = false) ?: it
            }
        }
    }

    fun loadFileTree() {
        val file = currentApkFile() ?: return
        if (_fileTreeState.value !is ReToolState.NotLoaded) return
        _fileTreeState.value = ReToolState.Loading
        viewModelScope.launch {
            try {
                _fileTreeState.value = ReToolState.Loaded(exploreApkFilesUseCase.listEntries(file))
            } catch (e: Exception) {
                _fileTreeState.value = ReToolState.Failed(e.message ?: "Unable to list APK contents.")
            }
        }
    }

    suspend fun readFileEntryBytes(path: String, maxBytes: Int = 64 * 1024): ByteArray? {
        val file = currentApkFile() ?: return null
        return exploreApkFilesUseCase.readEntryBytes(file, path, maxBytes)
    }

    fun loadClasses() {
        val file = currentApkFile() ?: return
        if (_classesState.value !is ReToolState.NotLoaded) return
        _classesState.value = ReToolState.Loading
        viewModelScope.launch {
            try {
                _classesState.value = ReToolState.Loaded(browseClassesUseCase(file))
            } catch (e: Exception) {
                _classesState.value = ReToolState.Failed(e.message ?: "Unable to parse DEX classes.")
            }
        }
    }

    fun loadNativeLibraries() {
        val file = currentApkFile() ?: return
        if (_nativeLibsState.value !is ReToolState.NotLoaded) return
        _nativeLibsState.value = ReToolState.Loading
        viewModelScope.launch {
            try {
                _nativeLibsState.value = ReToolState.Loaded(analyzeNativeLibrariesUseCase(file))
            } catch (e: Exception) {
                _nativeLibsState.value = ReToolState.Failed(e.message ?: "Unable to parse native libraries.")
            }
        }
    }

    private fun resetReToolState() {
        _fileTreeState.value = ReToolState.NotLoaded
        _classesState.value = ReToolState.NotLoaded
        _nativeLibsState.value = ReToolState.NotLoaded
    }

    /** Renders the current result to PDF on a background thread, saves it into Downloads, and returns its content Uri. */
    suspend fun exportPdfReport(): Uri? {
        val current = _uiState.value as? ApkInspectorUiState.Success ?: return null
        return withContext(Dispatchers.IO) {
            val metadata = current.result.metadata
            reportExporter.exportApkReport(
                title = metadata.appLabel.ifBlank { metadata.packageName },
                subtitle = metadata.packageName,
                result = current.result,
            )
        }
    }

    fun reset() {
        importedFile?.let { apkImporter.delete(it) }
        importedFile = null
        resetReToolState()
        _uiState.value = ApkInspectorUiState.Idle
    }
}
