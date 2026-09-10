package dev.prateekthakur.devprobe.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.prateekthakur.devprobe.domain.model.AnalysisSession
import dev.prateekthakur.devprobe.domain.model.SessionSource
import dev.prateekthakur.devprobe.domain.model.SessionType
import dev.prateekthakur.devprobe.presentation.common.rememberContainerViewModel
import dev.prateekthakur.devprobe.presentation.components.AppCard
import dev.prateekthakur.devprobe.presentation.components.EmptyState
import dev.prateekthakur.devprobe.presentation.components.ScreenTopBar
import dev.prateekthakur.devprobe.presentation.components.TintedChip
import dev.prateekthakur.devprobe.presentation.components.pressScale
import dev.prateekthakur.devprobe.presentation.theme.SeverityMedium
import java.text.DateFormat
import java.util.Date

/**
 * Home is the app's only navigation hub now that there's no bottom bar — every
 * workflow is a full-width row here (icon, title, one-line description, chevron),
 * pushed onto the stack and left via the top bar's back arrow. Home is also the
 * only screen whose top bar shows the Settings action.
 */
@Composable
fun HomeScreen(
    onOpenApkInspector: () -> Unit,
    onOpenLogAnalyzer: () -> Unit,
    onOpenProfiler: () -> Unit,
    onOpenDeviceDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    createViewModel: () -> HomeViewModel,
) {
    val viewModel = rememberContainerViewModel { createViewModel() }
    val sessions by viewModel.recentSessions.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = "DevProbe",
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "What are we inspecting today?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            item {
                NavRow(
                    title = "Inspect APK",
                    description = "Reverse-engineer manifest, permissions, security & bytecode",
                    icon = Icons.Filled.Search,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onOpenApkInspector,
                )
            }
            item {
                NavRow(
                    title = "Log Analyzer",
                    description = "Parse crash logs — pasted, or auto-detected in the background",
                    icon = Icons.AutoMirrored.Filled.List,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onOpenLogAnalyzer,
                )
            }
            item {
                NavRow(
                    title = "Profiler",
                    description = "Per-app storage, network, and foreground usage time",
                    icon = Icons.Filled.Build,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = onOpenProfiler,
                )
            }
            item {
                NavRow(
                    title = "Device Info",
                    description = "OS, hardware, memory & storage diagnostics",
                    icon = Icons.Filled.Phone,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = onOpenDeviceDiagnostics,
                )
            }
            item {
                Text(text = "Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            }
            if (sessions.isEmpty()) {
                item {
                    EmptyState(
                        "No analysis sessions yet.\nImport an APK or paste a crash log to get started.",
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                    )
                }
            }
            items(sessions, key = { it.id }) { session ->
                RecentSessionCard(session, Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun NavRow(
    title: String,
    description: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AppCard(modifier = modifier.pressScale(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(containerColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = contentColor)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentSessionCard(session: AnalysisSession, modifier: Modifier = Modifier) {
    val accent = when {
        session.securityCount > 0 -> MaterialTheme.colorScheme.error
        session.warningCount > 0 -> SeverityMedium
        else -> MaterialTheme.colorScheme.primary
    }
    AppCard(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text = session.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (session.source == SessionSource.AUTO_DETECTED) {
                            TintedChip(label = "AUTO", color = MaterialTheme.colorScheme.tertiary)
                        }
                        SessionTypeChip(session.type)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.createdAtEpochMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatPill("Findings", session.findingsCount)
                    StatPill("Security", session.securityCount, emphasize = session.securityCount > 0)
                    StatPill("Warnings", session.warningCount)
                }
            }
        }
    }
}

@Composable
private fun SessionTypeChip(type: SessionType) {
    val label = when (type) {
        SessionType.APK_ANALYSIS -> "APK"
        SessionType.LOG_ANALYSIS -> "LOG"
    }
    TintedChip(label = label, color = MaterialTheme.colorScheme.secondary)
}

@Composable
private fun StatPill(label: String, value: Int, emphasize: Boolean = false) {
    val color = if (emphasize && value > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    TintedChip(label = "$value $label", color = color)
}
