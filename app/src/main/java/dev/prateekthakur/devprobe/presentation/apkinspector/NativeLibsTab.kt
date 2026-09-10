package dev.prateekthakur.devprobe.presentation.apkinspector

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.prateekthakur.devprobe.domain.model.AppError
import dev.prateekthakur.devprobe.domain.model.NativeLibraryInfo
import dev.prateekthakur.devprobe.presentation.components.AppCard
import dev.prateekthakur.devprobe.presentation.components.EmptyState
import dev.prateekthakur.devprobe.presentation.components.ErrorState
import dev.prateekthakur.devprobe.presentation.components.EyebrowLabel
import dev.prateekthakur.devprobe.presentation.components.FullScreenLoading
import dev.prateekthakur.devprobe.presentation.components.RowDivider

@Composable
fun NativeLibsTab(viewModel: ApkInspectorViewModel) {
    LaunchedEffect(Unit) { viewModel.loadNativeLibraries() }
    val state by viewModel.nativeLibsState.collectAsStateWithLifecycle()

    when (val s = state) {
        is ReToolState.NotLoaded, is ReToolState.Loading -> FullScreenLoading("Parsing native libraries…")
        is ReToolState.Failed -> Column(modifier = Modifier.padding(16.dp)) { ErrorState(AppError.unexpected(s.message)) }
        is ReToolState.Loaded -> {
            val libs = s.data
            if (libs.isEmpty()) {
                EmptyState("This APK contains no native (.so) libraries.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(libs, key = { it.path }) { lib ->
                        NativeLibCard(lib, Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeLibCard(lib: NativeLibraryInfo, modifier: Modifier = Modifier) {
    var expandedSection by remember { mutableStateOf<String?>(null) }
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(lib.path, style = MaterialTheme.typography.titleSmall)
            Text(
                "${lib.architecture} · ${if (lib.is64Bit) "64-bit" else "32-bit"} · ${formatBytes(lib.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EyebrowLabel(
                "${lib.exportedSymbols.size} exported · ${lib.importedSymbols.size} imported",
                modifier = Modifier.padding(top = 6.dp).clickable {
                    expandedSection = if (expandedSection == "exported") null else "exported"
                },
            )
            if (expandedSection == "exported" && lib.exportedSymbols.isNotEmpty()) {
                RowDivider()
                lib.exportedSymbols.take(200).forEach { symbol ->
                    Text(symbol, style = MaterialTheme.typography.bodySmall)
                }
                if (lib.exportedSymbols.size > 200) {
                    Text("… ${lib.exportedSymbols.size - 200} more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            EyebrowLabel(
                "Show imported symbols",
                modifier = Modifier.padding(top = 6.dp).clickable {
                    expandedSection = if (expandedSection == "imported") null else "imported"
                },
            )
            if (expandedSection == "imported" && lib.importedSymbols.isNotEmpty()) {
                RowDivider()
                lib.importedSymbols.take(200).forEach { symbol ->
                    Text(symbol, style = MaterialTheme.typography.bodySmall)
                }
                if (lib.importedSymbols.size > 200) {
                    Text("… ${lib.importedSymbols.size - 200} more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

