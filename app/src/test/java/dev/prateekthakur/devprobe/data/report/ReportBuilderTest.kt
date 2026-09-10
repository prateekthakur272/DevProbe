package dev.prateekthakur.devprobe.data.report

import dev.prateekthakur.devprobe.domain.model.ApkAnalysisResult
import dev.prateekthakur.devprobe.domain.model.ApkMetadata
import dev.prateekthakur.devprobe.domain.model.ApkSizeBreakdown
import dev.prateekthakur.devprobe.domain.model.BytecodeAnalysis
import dev.prateekthakur.devprobe.domain.model.CrashReport
import dev.prateekthakur.devprobe.domain.model.LogAnalysisResult
import dev.prateekthakur.devprobe.domain.model.ManifestInfo
import dev.prateekthakur.devprobe.domain.model.ObfuscationSignal
import dev.prateekthakur.devprobe.domain.model.ParsedStackTrace
import dev.prateekthakur.devprobe.domain.model.PermissionCategory
import dev.prateekthakur.devprobe.domain.model.PermissionInfo
import dev.prateekthakur.devprobe.domain.model.RiskLevel
import dev.prateekthakur.devprobe.domain.model.SecurityFinding
import dev.prateekthakur.devprobe.domain.model.Severity
import dev.prateekthakur.devprobe.domain.model.SigningInfo
import dev.prateekthakur.devprobe.domain.model.StackFrame
import dev.prateekthakur.devprobe.domain.model.StringScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportBuilderTest {

    private fun sampleApkResult(): ApkAnalysisResult {
        val metadata = ApkMetadata(
            appLabel = "Food App",
            packageName = "com.example.foodapp",
            versionName = "2.1.0",
            versionCode = 21,
            minSdk = 24,
            targetSdk = 34,
            compileSdk = 34,
            apkSizeBytes = 15_000_000,
            debuggable = false,
            nativeArchitectures = listOf("arm64-v8a"),
        )
        val manifest = ManifestInfo(
            packageName = metadata.packageName,
            appLabel = metadata.appLabel,
            debuggable = false,
            allowBackup = true,
            usesCleartextTraffic = false,
            hasNetworkSecurityConfig = true,
            hasDataExtractionRules = true,
            activities = emptyList(),
            services = emptyList(),
            receivers = emptyList(),
            providers = emptyList(),
        )
        return ApkAnalysisResult(
            metadata = metadata,
            manifest = manifest,
            permissions = listOf(
                PermissionInfo("android.permission.INTERNET", PermissionCategory.NORMAL, "Access network", RiskLevel.LOW),
                PermissionInfo("android.permission.CAMERA", PermissionCategory.DANGEROUS, "Access camera", RiskLevel.HIGH),
            ),
            securityFindings = listOf(
                SecurityFinding(Severity.HIGH, "Cleartext traffic allowed", "desc", "evidence", "recommendation"),
            ),
            sizeBreakdown = ApkSizeBreakdown(
                totalBytes = 15_000_000, fileCount = 500, dexBytes = 5_000_000, nativeLibBytes = 4_000_000,
                resourceBytes = 3_000_000, assetBytes = 2_000_000, otherBytes = 1_000_000, largestFiles = emptyList(),
            ),
            signing = SigningInfo(hasV1 = false, hasV2 = true, hasV3 = true, hasV31 = false, certificates = emptyList()),
            bytecode = BytecodeAnalysis(
                dexFiles = emptyList(), totalClasses = 1200, totalMethods = 8000, totalFields = 3000, totalStrings = 5000,
                isMultidex = false, obfuscation = ObfuscationSignal(1200, 900, 75.0, true),
            ),
            stringScan = StringScanResult(totalStringsScanned = 5000, secrets = emptyList(), weakCryptoUsages = emptyList()),
        )
    }

    @Test
    fun `apk report includes overview and section headers`() {
        val blocks = buildApkReportBlocks(sampleApkResult())
        val sectionTitles = blocks.filterIsInstance<ReportBlock.Section>().map { it.text }

        assertTrue(sectionTitles.contains("Overview"))
        assertTrue(sectionTitles.any { it.startsWith("Security Findings") })
        assertTrue(sectionTitles.any { it.startsWith("Permissions") })
        assertTrue(sectionTitles.contains("Signing & Certificate"))
        assertTrue(sectionTitles.contains("Size Breakdown"))
        assertTrue(sectionTitles.contains("Bytecode"))
        assertTrue(sectionTitles.contains("Strings & Secrets"))
    }

    @Test
    fun `apk report renders the one security finding as a finding block`() {
        val blocks = buildApkReportBlocks(sampleApkResult())
        val findings = blocks.filterIsInstance<ReportBlock.Finding>()

        assertEquals(1, findings.size)
        assertEquals("HIGH", findings.first().severityLabel)
        assertEquals("Cleartext traffic allowed", findings.first().title)
    }

    @Test
    fun `formatReportBytes formats across units`() {
        assertEquals("512 B", formatReportBytes(512))
        assertEquals("1.0 KB", formatReportBytes(1024))
        assertEquals("1.5 MB", formatReportBytes((1.5 * 1024 * 1024).toLong()))
    }

    private fun sampleLogResult(): LogAnalysisResult {
        val trace = ParsedStackTrace(
            exceptionType = "java.lang.NullPointerException",
            message = "Attempt to invoke virtual method on a null object reference",
            thread = "main",
            frames = listOf(StackFrame("com.example.foodapp.MainActivity", "onCreate", "MainActivity.kt", 42, true)),
        )
        val crash = CrashReport(
            exceptionType = trace.exceptionType,
            message = trace.message,
            thread = trace.thread,
            firstAppFrame = trace.frames.first(),
            severity = Severity.HIGH,
            fullTrace = trace,
        )
        return LogAnalysisResult(entries = emptyList(), detectedErrors = emptyList(), crashReports = listOf(crash))
    }

    @Test
    fun `log report renders crash reports as finding blocks with stack frames`() {
        val blocks = buildLogReportBlocks(sampleLogResult(), "com.example.foodapp")
        val findings = blocks.filterIsInstance<ReportBlock.Finding>()
        val code = blocks.filterIsInstance<ReportBlock.Code>()

        assertEquals(1, findings.size)
        assertEquals("java.lang.NullPointerException", findings.first().title)
        assertTrue(code.isNotEmpty())
        assertTrue(code.first().lines.first().contains("MainActivity"))
    }
}
