package dev.prateekthakur.devprobe.presentation.profiler

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.prateekthakur.devprobe.data.profiling.ProfilingPermissions
import dev.prateekthakur.devprobe.domain.model.AppError
import dev.prateekthakur.devprobe.domain.model.AppProfile
import dev.prateekthakur.devprobe.domain.model.ProfilingTimeRange
import dev.prateekthakur.devprobe.presentation.apkinspector.formatBytes
import dev.prateekthakur.devprobe.presentation.common.rememberContainerViewModel
import dev.prateekthakur.devprobe.presentation.components.AppCard
import dev.prateekthakur.devprobe.presentation.components.EmptyState
import dev.prateekthakur.devprobe.presentation.components.ErrorState
import dev.prateekthakur.devprobe.presentation.components.EyebrowLabel
import dev.prateekthakur.devprobe.presentation.components.FullScreenLoading
import dev.prateekthakur.devprobe.presentation.components.PrimaryActionButton
import dev.prateekthakur.devprobe.presentation.components.RowDivider
import dev.prateekthakur.devprobe.presentation.components.ScreenTopBar
import dev.prateekthakur.devprobe.presentation.components.StatChip
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@Composable
fun ProfilerScreen(onBack: () -> Unit, createViewModel: () -> ProfilerViewModel) {
    val context = LocalContext.current
    var usageAccessGranted by remember { mutableStateOf(ProfilingPermissions.hasUsageAccess(context)) }

    Scaffold(topBar = { ScreenTopBar(title = "Profiler", onBack = onBack) }) { padding ->
        if (!usageAccessGranted) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    "DevProbe needs \"Usage access\" to read per-app storage, network, and foreground-time data. " +
                        "This is a normal Settings toggle — no root or ADB required.",
                )
                Spacer(modifier = Modifier.height(16.dp))
                PrimaryActionButton(
                    "Open Usage access settings",
                    onClick = { context.startActivity(ProfilingPermissions.usageAccessSettingsIntent()) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "I've granted it — recheck",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { usageAccessGranted = ProfilingPermissions.hasUsageAccess(context) },
                    )
                }
            }
            return@Scaffold
        }

        val viewModel = rememberContainerViewModel { createViewModel() }
        var range by remember { mutableStateOf(ProfilingTimeRange.TODAY) }
        var query by remember { mutableStateOf("") }
        val state by viewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(range) { viewModel.load(range) }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                ProfilingTimeRange.entries.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = range == item,
                        onClick = { range = item },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ProfilingTimeRange.entries.size),
                    ) {
                        Text(item.label)
                    }
                }
            }
            when (val s = state) {
                is ProfilerUiState.Loading -> FullScreenLoading("Profiling installed apps…")
                is ProfilerUiState.Failed -> Column(modifier = Modifier.padding(16.dp)) { ErrorState(AppError.unexpected(s.message)) }
                is ProfilerUiState.Loaded -> {
                    val filtered = remember(query, s.profiles) {
                        if (query.isBlank()) s.profiles else s.profiles.filter { it.appLabel.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
                    }
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Search apps") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                        )
                    }
                    if (filtered.isEmpty()) {
                        EmptyState("No apps match \"$query\".")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(filtered, key = { it.packageName }) { profile ->
                                AppProfileCard(profile, Modifier.animateItem())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppProfileCard(profile: AppProfile, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(profile.appLabel, style = MaterialTheme.typography.titleSmall)
            Text(profile.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatChip("Storage", formatBytes(profile.totalStorageBytes), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatChip("Network", formatBytes(profile.totalNetworkBytes), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                StatChip("Foreground", formatDuration(profile.foregroundTimeMs), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            }
            EyebrowLabel(
                "Show breakdown",
                modifier = Modifier.padding(top = 10.dp).clickable { expanded = !expanded },
            )
            if (expanded) {
                RowDivider()
                InfoLine("App size", formatBytes(profile.appSizeBytes))
                InfoLine("Data size", formatBytes(profile.dataSizeBytes))
                InfoLine("Cache size", formatBytes(profile.cacheSizeBytes))
                InfoLine("Wi-Fi data", formatBytes(profile.wifiBytes))
                InfoLine("Mobile data", formatBytes(profile.mobileBytes))
                InfoLine("Launches", profile.launchCount.toString())
                InfoLine(
                    "Last used",
                    if (profile.lastUsedEpochMillis > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(profile.lastUsedEpochMillis)) else "—",
                )
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatDuration(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${TimeUnit.MILLISECONDS.toSeconds(millis)}s"
    }
}
