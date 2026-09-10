package dev.prateekthakur.devprobe.domain.model

enum class SessionType { APK_ANALYSIS, LOG_ANALYSIS }

/** Whether a session was pasted/imported by the user, or captured automatically by [dev.prateekthakur.devprobe.data.crash.CrashMonitorService]. */
enum class SessionSource { MANUAL, AUTO_DETECTED }

data class AnalysisSession(
    val id: Long = 0,
    val title: String,
    val type: SessionType,
    val createdAtEpochMillis: Long,
    val packageName: String?,
    val findingsCount: Int,
    val securityCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val source: SessionSource = SessionSource.MANUAL,
    val apkResult: ApkAnalysisResult? = null,
    val logResult: LogAnalysisResult? = null,
)

data class AiConfidenceLabel(
    val confirmedFacts: List<String>,
    val likelyCause: String?,
    val possibleCauses: List<String>,
    val recommendations: List<String>,
)
