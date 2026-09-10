package dev.prateekthakur.devprobe.presentation.loganalyzer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.prateekthakur.devprobe.domain.model.CrashReport
import dev.prateekthakur.devprobe.presentation.apkinspector.AiExplanationCard
import dev.prateekthakur.devprobe.presentation.common.rememberContainerViewModel
import dev.prateekthakur.devprobe.presentation.common.rememberPdfExportAction
import dev.prateekthakur.devprobe.presentation.components.AppCard
import dev.prateekthakur.devprobe.presentation.components.ErrorState
import dev.prateekthakur.devprobe.presentation.components.EyebrowLabel
import dev.prateekthakur.devprobe.presentation.components.FullScreenLoading
import dev.prateekthakur.devprobe.presentation.components.PrimaryActionButton
import dev.prateekthakur.devprobe.presentation.components.ScreenTopBar
import dev.prateekthakur.devprobe.presentation.components.SecondaryActionButton
import dev.prateekthakur.devprobe.presentation.components.SectionHeader
import dev.prateekthakur.devprobe.presentation.components.SeverityBadge
import dev.prateekthakur.devprobe.presentation.theme.ExpressiveMotion

@Composable
fun LogAnalyzerScreen(onBack: () -> Unit, createViewModel: () -> LogAnalyzerViewModel) {
    val viewModel = rememberContainerViewModel { createViewModel() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    var packageHint by remember { mutableStateOf("") }
    val context = LocalContext.current
    val pdfExport = rememberPdfExportAction { viewModel.exportPdfReport() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            runCatching {
                context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }
            }.getOrNull()?.let { text -> inputText = text }
        }
    }

    val fadeInSpec = ExpressiveMotion.effectsSpec<Float>()
    val fadeOutSpec = ExpressiveMotion.fastEffectsSpec<Float>()
    val slideInSpec = ExpressiveMotion.spatialSpec<IntOffset>()
    val slideOutSpec = ExpressiveMotion.fastSpatialSpec<IntOffset>()

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = "Log Analyzer",
                onBack = onBack,
                actions = {
                    if (state is LogAnalyzerUiState.Success) {
                        TextButton(enabled = !pdfExport.isExporting, onClick = pdfExport.export) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (pdfExport.isExporting) "Exporting…" else "Export PDF")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = {
                    (fadeIn(fadeInSpec) + slideInVertically(slideInSpec) { it / 10 })
                        .togetherWith(fadeOut(fadeOutSpec) + slideOutVertically(slideOutSpec) { -it / 20 })
                },
                label = "logAnalyzerState",
            ) { s ->
                when (s) {
                    is LogAnalyzerUiState.Idle, is LogAnalyzerUiState.Error -> {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            if (s is LogAnalyzerUiState.Idle) {
                                Box(
                                    modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(28.dp))
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Paste a crash log or Logcat excerpt", style = MaterialTheme.typography.headlineSmall)
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            OutlinedTextField(
                                value = packageHint,
                                onValueChange = { packageHint = it },
                                label = { Text("App package (optional, improves crash-frame detection)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                label = { Text("Paste Logcat / crash report / stack trace") },
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                shape = MaterialTheme.shapes.medium,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            SecondaryActionButton("Import .txt/.log file", onClick = { filePicker.launch("text/plain") }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(12.dp))
                            if (s is LogAnalyzerUiState.Error) {
                                ErrorState(error = s.error)
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            PrimaryActionButton("Analyze", onClick = { viewModel.analyze(inputText, packageHint) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    is LogAnalyzerUiState.Analyzing -> FullScreenLoading("Analyzing...")
                    is LogAnalyzerUiState.Success -> LogResultsView(
                        state = s,
                        onExplain = { index, crash -> viewModel.explainCrash(index, crash, packageHint.takeIf { it.isNotBlank() }) },
                        onReset = { viewModel.reset(); inputText = "" },
                    )
                }
            }
        }
    }
}

@Composable
private fun LogResultsView(
    state: LogAnalyzerUiState.Success,
    onExplain: (Int, CrashReport) -> Unit,
    onReset: () -> Unit,
) {
    val result = state.result

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SecondaryActionButton("Analyze another log", onClick = onReset, modifier = Modifier.fillMaxWidth()) }
        item { SectionHeader("Parsed entries: ${result.entries.size} · Detected errors: ${result.detectedErrors.size}") }
        if (result.crashReports.isEmpty()) {
            item { Text("No structured crash/stack trace found in this input.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        itemsIndexed(result.crashReports) { index, crash ->
            CrashCard(
                crash = crash,
                isExplaining = state.explainingIndex == index,
                explanation = state.explanations[index],
                onExplain = { onExplain(index, crash) },
                modifier = Modifier.animateItem(),
            )
        }
        if (result.detectedErrors.isNotEmpty()) {
            item { SectionHeader("Other detected errors") }
            items(result.detectedErrors) { error ->
                AppCard(modifier = Modifier.animateItem()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        EyebrowLabel(error.category.name)
                        Text(error.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashCard(
    crash: CrashReport,
    isExplaining: Boolean,
    explanation: dev.prateekthakur.devprobe.data.ai.AiExplanation?,
    onExplain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeverityBadge(crash.severity)
                Text(crash.exceptionType, style = MaterialTheme.typography.titleSmall)
            }
            crash.message?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            crash.thread?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text("Thread: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            crash.firstAppFrame?.let { frame ->
                Spacer(modifier = Modifier.height(10.dp))
                EyebrowLabel("FIRST APPLICATION FRAME")
                Text("${frame.declaringClass}.${frame.method}(${frame.file}:${frame.line ?: "?"})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(14.dp))
            if (explanation == null) {
                PrimaryActionButton(
                    text = if (isExplaining) "Explaining..." else "Ask AI to explain this crash",
                    onClick = onExplain,
                    enabled = !isExplaining,
                )
            } else {
                AiExplanationCard(explanation)
            }
        }
    }
}
