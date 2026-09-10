package dev.prateekthakur.devprobe.presentation.apkinspector

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.prateekthakur.devprobe.domain.model.ApkAnalysisResult
import dev.prateekthakur.devprobe.domain.model.PermissionInfo
import dev.prateekthakur.devprobe.domain.model.RiskLevel
import dev.prateekthakur.devprobe.domain.model.SecurityFinding
import dev.prateekthakur.devprobe.domain.model.Severity
import dev.prateekthakur.devprobe.presentation.common.rememberContainerViewModel
import dev.prateekthakur.devprobe.presentation.common.rememberPdfExportAction
import dev.prateekthakur.devprobe.presentation.components.AppCard
import dev.prateekthakur.devprobe.presentation.components.BarEntry
import dev.prateekthakur.devprobe.presentation.components.ChartLegend
import dev.prateekthakur.devprobe.presentation.components.ChartSlice
import dev.prateekthakur.devprobe.presentation.components.DonutChart
import dev.prateekthakur.devprobe.presentation.components.EmptyState
import dev.prateekthakur.devprobe.presentation.components.ErrorState
import dev.prateekthakur.devprobe.presentation.components.EyebrowLabel
import dev.prateekthakur.devprobe.presentation.components.FindingCard
import dev.prateekthakur.devprobe.presentation.components.GaugeChart
import dev.prateekthakur.devprobe.presentation.components.HorizontalBarChart
import dev.prateekthakur.devprobe.presentation.components.InfoRow
import dev.prateekthakur.devprobe.presentation.components.KpiCard
import dev.prateekthakur.devprobe.presentation.components.PrimaryActionButton
import dev.prateekthakur.devprobe.presentation.components.ProgressBarLabelled
import dev.prateekthakur.devprobe.presentation.components.RowDivider
import dev.prateekthakur.devprobe.presentation.components.ScreenTopBar
import dev.prateekthakur.devprobe.presentation.components.SecondaryActionButton
import dev.prateekthakur.devprobe.presentation.components.SectionCard
import dev.prateekthakur.devprobe.presentation.components.SectionHeader
import dev.prateekthakur.devprobe.presentation.components.StatChip
import dev.prateekthakur.devprobe.presentation.components.StatusChip
import dev.prateekthakur.devprobe.presentation.components.severityColor
import dev.prateekthakur.devprobe.presentation.theme.ExpressiveMotion
import dev.prateekthakur.devprobe.presentation.theme.SeverityLow
import dev.prateekthakur.devprobe.presentation.theme.SeverityMedium

private val tabs = listOf("Overview", "Manifest", "Permissions", "Security", "Size", "Certificate", "Bytecode", "Files", "Strings", "Classes", "Natives")

@Composable
fun ApkInspectorScreen(onBack: () -> Unit, createViewModel: () -> ApkInspectorViewModel) {
    val viewModel = rememberContainerViewModel { createViewModel() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val pdfExport = rememberPdfExportAction { viewModel.exportPdfReport() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.onApkSelected(it) }
    }

    // transitionSpec below is a plain (non-@Composable) lambda, so the motion specs are
    // read from the active MotionScheme here, in composable context, and captured by it.
    val fadeInSpec = ExpressiveMotion.effectsSpec<Float>()
    val fadeOutSpec = ExpressiveMotion.fastEffectsSpec<Float>()
    val slideInSpec = ExpressiveMotion.spatialSpec<IntOffset>()
    val slideOutSpec = ExpressiveMotion.fastSpatialSpec<IntOffset>()

    val successState = state as? ApkInspectorUiState.Success
    val title = successState?.result?.metadata?.let { it.appLabel.ifBlank { it.packageName } } ?: "Inspect APK"

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = title,
                onBack = onBack,
                actions = {
                    if (successState != null) {
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
                // Only the state's *kind* drives the transition — e.g. clicking "Explain" on a
                // Success state shouldn't re-trigger a fade/slide, only Idle<->Analyzing<->Error<->Success does.
                contentKey = { it::class },
                transitionSpec = {
                    (fadeIn(fadeInSpec) + slideInVertically(slideInSpec) { it / 10 })
                        .togetherWith(fadeOut(fadeOutSpec) + slideOutVertically(slideOutSpec) { -it / 20 })
                },
                label = "apkInspectorState",
            ) { s ->
                when (s) {
                    is ApkInspectorUiState.Idle -> IdleContent {
                        launcher.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
                    }
                    is ApkInspectorUiState.Analyzing -> Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                        ProgressBarLabelled(label = s.step, percent = s.percent)
                    }
                    is ApkInspectorUiState.Error -> Column(modifier = Modifier.padding(16.dp)) {
                        ErrorState(error = s.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        SecondaryActionButton("Try again", onClick = { viewModel.reset() })
                    }
                    is ApkInspectorUiState.Success -> Column(modifier = Modifier.fillMaxSize()) {
                        ApkTabBar(tabs = tabs, selectedIndex = selectedTab, onSelect = { selectedTab = it })
                        when (selectedTab) {
                            0 -> OverviewTab(s.result)
                            1 -> ManifestTab(s.result)
                            2 -> PermissionsTab(s.result.permissions)
                            3 -> SecurityTab(s.result.securityFindings, s, viewModel)
                            4 -> SizeTab(s.result)
                            5 -> CertificateTab(s.result)
                            6 -> BytecodeTab(s.result)
                            7 -> FileExplorerTab(viewModel)
                            8 -> StringsTab(s.result.stringScan)
                            9 -> ClassesTab(viewModel)
                            10 -> NativeLibsTab(viewModel)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A pill-style, horizontally scrollable tab bar — an alternative to the stock
 * Material TabRow that reads less "form control" and more like a segmented
 * filter strip (Linear/Notion-style), which suits a dense analysis dashboard.
 */
@Composable
private fun ApkTabBar(tabs: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEachIndexed { index, title ->
                ApkTabPill(
                    title = title,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

@Composable
private fun ApkTabPill(title: String, selected: Boolean, onClick: () -> Unit) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = ExpressiveMotion.effectsSpec(),
        label = "tabBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = ExpressiveMotion.effectsSpec(),
        label = "tabContent",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = ExpressiveMotion.fastSpatialSpec(),
        label = "tabScale",
    )
    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun IdleContent(onImport: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "idlePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "idlePulseScale",
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text("Inspect an APK", style = MaterialTheme.typography.headlineSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Import an APK to see its metadata, manifest, permissions, security posture, and size — fully on-device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryActionButton("Import APK", onClick = onImport)
    }
}

/** A KPI + chart dashboard — the at-a-glance summary before diving into the detail tabs. */
@Composable
private fun OverviewTab(result: ApkAnalysisResult) {
    val m = result.metadata
    val findingsBySeverity = Severity.entries.associateWith { sev -> result.securityFindings.count { it.severity == sev } }
    val securityScore = securityScore(findingsBySeverity)
    val scoreColor = when {
        securityScore >= 80 -> MaterialTheme.colorScheme.primary
        securityScore >= 50 -> SeverityMedium
        else -> MaterialTheme.colorScheme.error
    }
    val dangerousPermissions = result.permissions.count { it.risk == RiskLevel.HIGH || it.risk == RiskLevel.MEDIUM }

    val sizeSlices = listOfNotNull(
        sliceOrNull("DEX", result.sizeBreakdown.dexBytes, MaterialTheme.colorScheme.primary),
        sliceOrNull("Native libs", result.sizeBreakdown.nativeLibBytes, MaterialTheme.colorScheme.tertiary),
        sliceOrNull("Resources", result.sizeBreakdown.resourceBytes, MaterialTheme.colorScheme.secondary),
        sliceOrNull("Assets", result.sizeBreakdown.assetBytes, SeverityLow),
        sliceOrNull("Other", result.sizeBreakdown.otherBytes, MaterialTheme.colorScheme.onSurfaceVariant),
    )

    val severityBars = Severity.entries.reversed().map { sev ->
        BarEntry(sev.name, findingsBySeverity[sev] ?: 0, severityColor(sev))
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            AppCard {
                Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                        GaugeChart(progress = securityScore / 100f, color = scoreColor, modifier = Modifier.size(120.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(securityScore.toString(), style = MaterialTheme.typography.headlineMedium, color = scoreColor)
                                Text("/ 100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column {
                        Text("Security Score", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            securityScoreLabel(securityScore),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                KpiCard("Findings", result.securityFindings.size.toString(), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                KpiCard("Risky permissions", dangerousPermissions.toString(), Modifier.weight(1f), color = SeverityMedium)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                KpiCard("APK size", formatBytes(m.apkSizeBytes), Modifier.weight(1f), color = MaterialTheme.colorScheme.tertiary)
                KpiCard("Methods", "%,d".format(result.bytecode.totalMethods), Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                KpiCard("Min · Target SDK", "${m.minSdk} · ${m.targetSdk}", Modifier.weight(1f))
                KpiCard(
                    "Obfuscation",
                    if (result.bytecode.obfuscation.likelyObfuscated) "Likely" else "Unlikely",
                    Modifier.weight(1f),
                    color = if (result.bytecode.obfuscation.likelyObfuscated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionHeader("Findings by severity") }
        item {
            AppCard {
                if (result.securityFindings.isEmpty()) {
                    Box(modifier = Modifier.padding(24.dp)) {
                        Text("No security findings detected.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    HorizontalBarChart(entries = severityBars, modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
            }
        }

        item { SectionHeader("Size breakdown") }
        item {
            AppCard {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                        DonutChart(slices = sizeSlices, modifier = Modifier.size(120.dp))
                        Text(formatBytes(result.sizeBreakdown.totalBytes), style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    ChartLegend(
                        slices = sizeSlices,
                        format = { formatBytes(it.toLong()) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item { SectionHeader("Details") }
        item {
            SectionCard {
                InfoRow("Application name", m.appLabel)
                RowDivider()
                InfoRow("Package name", m.packageName)
                RowDivider()
                InfoRow("Version", "${m.versionName ?: "unknown"} (${m.versionCode})")
                RowDivider()
                InfoRow("Compile SDK", m.compileSdk?.toString() ?: "unavailable")
                RowDivider()
                InfoRow("Debuggable", if (m.debuggable) "Yes" else "No")
                RowDivider()
                InfoRow("Native architectures", if (m.nativeArchitectures.isEmpty()) "None (Java/Kotlin only)" else m.nativeArchitectures.joinToString())
            }
        }
    }
}

private fun sliceOrNull(label: String, bytes: Long, color: androidx.compose.ui.graphics.Color): ChartSlice? =
    if (bytes > 0) ChartSlice(label, bytes.toFloat(), color) else null

private fun securityScore(findingsBySeverity: Map<Severity, Int>): Int {
    val penalty = (findingsBySeverity[Severity.CRITICAL] ?: 0) * 25 +
        (findingsBySeverity[Severity.HIGH] ?: 0) * 15 +
        (findingsBySeverity[Severity.MEDIUM] ?: 0) * 8 +
        (findingsBySeverity[Severity.LOW] ?: 0) * 3
    return (100 - penalty).coerceIn(0, 100)
}

private fun securityScoreLabel(score: Int): String = when {
    score >= 90 -> "No significant issues detected."
    score >= 70 -> "Minor issues found — worth reviewing."
    score >= 40 -> "Notable security concerns detected."
    else -> "Serious security concerns — review findings below."
}

@Composable
private fun ManifestTab(result: ApkAnalysisResult) {
    val manifest = result.manifest
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SectionCard {
                InfoRow("Allow backup", manifest.allowBackup.toString())
                RowDivider()
                InfoRow("Cleartext traffic", manifest.usesCleartextTraffic?.toString() ?: "not declared")
                RowDivider()
                InfoRow("Network security config", manifest.hasNetworkSecurityConfig.toString())
            }
        }
        item { SectionHeader("Activities (${manifest.activities.size})") }
        if (manifest.activities.isNotEmpty()) item { SectionCard { manifest.activities.forEachIndexed { i, c -> ComponentRow(c.name, c.exported, c.permission, c.intentFilters.size); if (i != manifest.activities.lastIndex) RowDivider() } } }
        item { SectionHeader("Services (${manifest.services.size})") }
        if (manifest.services.isNotEmpty()) item { SectionCard { manifest.services.forEachIndexed { i, c -> ComponentRow(c.name, c.exported, c.permission, c.intentFilters.size); if (i != manifest.services.lastIndex) RowDivider() } } }
        item { SectionHeader("Receivers (${manifest.receivers.size})") }
        if (manifest.receivers.isNotEmpty()) item { SectionCard { manifest.receivers.forEachIndexed { i, c -> ComponentRow(c.name, c.exported, c.permission, c.intentFilters.size); if (i != manifest.receivers.lastIndex) RowDivider() } } }
        item { SectionHeader("Providers (${manifest.providers.size})") }
        if (manifest.providers.isNotEmpty()) {
            item {
                SectionCard {
                    manifest.providers.forEachIndexed { i, p ->
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Text(p.name, style = MaterialTheme.typography.bodyMedium)
                            Text("authority: ${p.authority} · exported: ${p.exported} · grantUriPermissions: ${p.grantUriPermissions}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (i != manifest.providers.lastIndex) RowDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ComponentRow(name: String, exported: Boolean, permission: String?, filterCount: Int) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "exported: $exported · permission: ${permission ?: "none"} · intent-filters: $filterCount",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionsTab(permissions: List<PermissionInfo>) {
    if (permissions.isEmpty()) {
        EmptyState("This APK declares no permissions.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            SectionCard {
                permissions.forEachIndexed { i, permission ->
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(permission.name.substringAfterLast('.'), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            StatusChip(permission.risk.name)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Category: ${permission.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(permission.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (i != permissions.lastIndex) RowDivider()
                }
            }
        }
    }
}

@Composable
private fun SecurityTab(findings: List<SecurityFinding>, state: ApkInspectorUiState.Success, viewModel: ApkInspectorViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (findings.isEmpty()) {
            item { EmptyState("No deterministic security findings for this APK.") }
        }
        items(findings) { finding ->
            FindingCard(finding.severity, finding.title, finding.description, finding.evidence, finding.recommendation, Modifier.animateItem())
        }
        if (findings.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                if (state.aiExplanation == null) {
                    PrimaryActionButton(
                        text = if (state.aiLoading) "Explaining..." else "Ask AI to explain these findings",
                        onClick = { viewModel.explainSecurityFindings() },
                        enabled = !state.aiLoading,
                    )
                } else {
                    AiExplanationCard(state.aiExplanation)
                }
            }
        }
    }
}

@Composable
fun AiExplanationCard(explanation: dev.prateekthakur.devprobe.data.ai.AiExplanation) {
    val onContainer = MaterialTheme.colorScheme.onSecondaryContainer
    AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = MaterialTheme.colorScheme.secondary, shape = CircleShape) {
                    Text("AI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Text(explanation.source, style = MaterialTheme.typography.labelMedium, color = onContainer)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(explanation.summary, style = MaterialTheme.typography.bodyMedium, color = onContainer)
            explanation.likelyCause?.let {
                Spacer(modifier = Modifier.height(10.dp))
                EyebrowLabel("LIKELY CAUSE", color = onContainer)
                Text(it, style = MaterialTheme.typography.bodySmall, color = onContainer)
            }
            if (explanation.possibleCauses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                EyebrowLabel("POSSIBLE CAUSES", color = onContainer)
                explanation.possibleCauses.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = onContainer) }
            }
            if (explanation.recommendations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                EyebrowLabel("RECOMMENDATIONS", color = onContainer)
                explanation.recommendations.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = onContainer) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Confidence: ${(explanation.confidence * 100).toInt()}% · Runs fully on-device",
                style = MaterialTheme.typography.labelSmall,
                color = onContainer.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun SizeTab(result: ApkAnalysisResult) {
    val size = result.sizeBreakdown
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatChip("Total size", formatBytes(size.totalBytes), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatChip("Files", size.fileCount.toString(), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            }
        }
        item {
            SectionCard {
                InfoRow("DEX", formatBytes(size.dexBytes))
                RowDivider()
                InfoRow("Native libraries", formatBytes(size.nativeLibBytes))
                RowDivider()
                InfoRow("Resources", formatBytes(size.resourceBytes))
                RowDivider()
                InfoRow("Assets", formatBytes(size.assetBytes))
                RowDivider()
                InfoRow("Other", formatBytes(size.otherBytes))
            }
        }
        item { SectionHeader("Largest files") }
        item {
            SectionCard {
                size.largestFiles.forEachIndexed { i, file ->
                    InfoRow(file.path, formatBytes(file.sizeBytes))
                    if (i != size.largestFiles.lastIndex) RowDivider()
                }
            }
        }
    }
}

@Composable
private fun CertificateTab(result: ApkAnalysisResult) {
    val signing = result.signing
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionHeader("Signing schemes") }
        item {
            SectionCard {
                SchemeRow("v1 (JAR signing)", signing.hasV1)
                RowDivider()
                SchemeRow("v2", signing.hasV2)
                RowDivider()
                SchemeRow("v3", signing.hasV3)
                RowDivider()
                SchemeRow("v3.1", signing.hasV31)
            }
        }
        if (signing.certificates.isEmpty()) {
            item { EmptyState("No signing certificate could be extracted from this APK.") }
        }
        itemsIndexed(signing.certificates) { index, cert ->
            Column(modifier = Modifier.animateItem()) {
                if (index == 0) SectionHeader(if (signing.certificates.size > 1) "Certificates (${signing.certificates.size})" else "Certificate")
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text("Status", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StatusChip(if (cert.isExpired) "EXPIRED" else "VALID")
                    }
                    if (cert.isDebugCertificate) {
                        RowDivider()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Text("Debug certificate", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            StatusChip("HIGH")
                        }
                    }
                    RowDivider()
                    InfoRow("Subject", cert.subject)
                    RowDivider()
                    InfoRow("Issuer", cert.issuer)
                    RowDivider()
                    InfoRow("Self-signed", cert.isSelfSigned.toString())
                    RowDivider()
                    InfoRow("Serial number", cert.serialNumber)
                    RowDivider()
                    InfoRow("Valid from", cert.notBefore)
                    RowDivider()
                    InfoRow("Valid until", cert.notAfter)
                    RowDivider()
                    InfoRow("Signature algorithm", cert.signatureAlgorithm)
                    RowDivider()
                    InfoRow("Public key", if (cert.keyBits > 0) "${cert.keyAlgorithm}-${cert.keyBits}" else cert.keyAlgorithm)
                    RowDivider()
                    InfoRow("SHA-1", cert.sha1Fingerprint)
                    RowDivider()
                    InfoRow("SHA-256", cert.sha256Fingerprint)
                }
            }
        }
    }
}

@Composable
private fun SchemeRow(label: String, present: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        StatusChip(if (present) "PRESENT" else "ABSENT")
    }
}

@Composable
private fun BytecodeTab(result: ApkAnalysisResult) {
    val bytecode = result.bytecode
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatChip("Classes", bytecode.totalClasses.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatChip("Methods", bytecode.totalMethods.toString(), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            }
        }
        item {
            SectionCard {
                InfoRow("DEX files", bytecode.dexFiles.size.toString())
                RowDivider()
                InfoRow("Multidex", if (bytecode.isMultidex) "Yes" else "No")
                RowDivider()
                InfoRow("Total fields", bytecode.totalFields.toString())
                RowDivider()
                InfoRow("Total strings", bytecode.totalStrings.toString())
            }
        }
        item { SectionHeader("Obfuscation signal") }
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text("Likely minified/obfuscated", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StatusChip(if (bytecode.obfuscation.likelyObfuscated) "LIKELY" else "UNLIKELY")
                }
                RowDivider()
                InfoRow("Short-named classes", "${bytecode.obfuscation.shortNameCount} / ${bytecode.obfuscation.sampledClassCount}")
                RowDivider()
                InfoRow("Short-name percentage", "%.1f%%".format(bytecode.obfuscation.shortNamePercent))
            }
        }
        item {
            Text(
                "Heuristic only: counts defined classes with 1-2 character simple names, typical of ProGuard/R8 output — not a guarantee.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { SectionHeader("DEX files") }
        items(bytecode.dexFiles) { dex ->
            SectionCard(modifier = Modifier.animateItem()) {
                Text(dex.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                Spacer(modifier = Modifier.height(4.dp))
                RowDivider()
                InfoRow("Version", dex.dexVersion)
                RowDivider()
                InfoRow("Size", formatBytes(dex.sizeBytes))
                RowDivider()
                InfoRow("Classes", dex.classCount.toString())
                RowDivider()
                InfoRow("Methods", dex.methodCount.toString())
                RowDivider()
                InfoRow("Fields", dex.fieldCount.toString())
                RowDivider()
                InfoRow("Strings", dex.stringCount.toString())
                RowDivider()
                InfoRow("SHA-1 signature", dex.sha1Signature.take(16) + "…")
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes / 1024.0
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}
