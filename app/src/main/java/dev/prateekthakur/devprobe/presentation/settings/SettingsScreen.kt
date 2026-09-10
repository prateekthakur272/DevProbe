package dev.prateekthakur.devprobe.presentation.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.prateekthakur.devprobe.data.apk.ApkImporter
import dev.prateekthakur.devprobe.data.crash.CrashMonitorService
import dev.prateekthakur.devprobe.data.crash.CrashPermissions
import dev.prateekthakur.devprobe.data.local.SettingsStore
import dev.prateekthakur.devprobe.data.repository.AnalysisRepository
import dev.prateekthakur.devprobe.presentation.components.AppCard
import dev.prateekthakur.devprobe.presentation.components.ScreenTopBar
import dev.prateekthakur.devprobe.presentation.components.SecondaryActionButton
import dev.prateekthakur.devprobe.presentation.components.StatusChip
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, settingsStore: SettingsStore, analysisRepository: AnalysisRepository, apkImporter: ApkImporter) {
    var aiEnabled by remember { mutableStateOf(settingsStore.isAiEnabled) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var readLogsGranted by remember { mutableStateOf(CrashPermissions.hasReadLogsPermission(context)) }
    var crashMonitorEnabled by remember { mutableStateOf(settingsStore.isCrashMonitorEnabled && readLogsGranted) }

    Scaffold(topBar = { ScreenTopBar(title = "Settings", onBack = onBack) }) { padding ->
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsCard(icon = Icons.Filled.Lock, iconTint = MaterialTheme.colorScheme.primary, title = "AI Assistant") {
            Text(
                "AI explanations currently run fully on-device using a local template engine. " +
                    "No crash logs, APK data, or analysis results are ever sent to a cloud service.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Enable AI explanations", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = aiEnabled,
                    onCheckedChange = {
                        aiEnabled = it
                        settingsStore.isAiEnabled = it
                    },
                    thumbContent = {
                        Icon(
                            imageVector = if (aiEnabled) Icons.Filled.Check else Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    },
                )
            }
        }

        SettingsCard(icon = Icons.Filled.Warning, iconTint = MaterialTheme.colorScheme.error, title = "Automatic Crash Detection") {
            Text(
                "DevProbe can watch the full device log in the background and automatically save any app's " +
                    "crash as a session here — no manual paste needed. This requires a one-time protected " +
                    "permission grant from a computer (Android does not allow apps to request this directly).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(if (readLogsGranted) "PRESENT" else "ABSENT")
                Text(
                    if (readLogsGranted) "READ_LOGS permission granted" else "READ_LOGS permission not granted",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!readLogsGranted) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Run this command from a computer with ADB, with the device connected:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                AppCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                        Text(
                            CrashPermissions.grantCommand(context),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                SecondaryActionButton(
                    "Copy command",
                    onClick = { clipboard.setText(AnnotatedString(CrashPermissions.grantCommand(context))) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                SecondaryActionButton(
                    "I've granted it — recheck",
                    onClick = { readLogsGranted = CrashPermissions.hasReadLogsPermission(context) },
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Watch for crashes in the background", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = crashMonitorEnabled,
                        onCheckedChange = { enabled ->
                            crashMonitorEnabled = enabled
                            settingsStore.isCrashMonitorEnabled = enabled
                            if (enabled) CrashMonitorService.start(context) else CrashMonitorService.stop(context)
                        },
                    )
                }
            }
        }

        SettingsCard(icon = Icons.Filled.Delete, iconTint = MaterialTheme.colorScheme.tertiary, title = "Local Data") {
            Text(
                "All analysis sessions and imported APKs are stored only in this app's private storage on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            SecondaryActionButton(
                "Delete all local analysis data",
                onClick = { scope.launch { analysisRepository.clearAll() }; apkImporter.clearCache() },
            )
        }

        DeveloperFooter(
            modifier = Modifier.padding(top = 8.dp),
            onOpenGitHub = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DEVELOPER_GITHUB_URL)))
            },
        )
    }
    }
}

private const val DEVELOPER_GITHUB_URL = "https://github.com/prateekthakur272"

@Composable
private fun DeveloperFooter(modifier: Modifier = Modifier, onOpenGitHub: () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.AccountCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Built by Prateek Thakur",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "github.com/prateekthakur272",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp).clickable(onClick = onOpenGitHub),
        )
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    content: @Composable () -> Unit,
) {
    AppCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
