package dev.prateekthakur.devprobe.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR, FATAL, UNKNOWN }

@Serializable
data class LogEntry(
    val timestamp: String?,
    val pid: String?,
    val tid: String?,
    val level: LogLevel,
    val tag: String?,
    val message: String,
    val raw: String,
)

@Serializable
enum class ErrorCategory {
    FATAL_EXCEPTION, ANR, NATIVE_CRASH, NULL_POINTER, ILLEGAL_STATE,
    OUT_OF_MEMORY, SQLITE, NETWORK, HTTP, SECURITY, OTHER
}

@Serializable
data class DetectedError(
    val category: ErrorCategory,
    val summary: String,
    val sourceLine: String,
)

@Serializable
data class StackFrame(
    val declaringClass: String,
    val method: String,
    val file: String?,
    val line: Int?,
    val isAppFrame: Boolean,
)

@Serializable
data class ParsedStackTrace(
    val exceptionType: String,
    val message: String?,
    val thread: String?,
    val frames: List<StackFrame>,
    val causedBy: ParsedStackTrace? = null,
)

@Serializable
data class CrashReport(
    val exceptionType: String,
    val message: String?,
    val thread: String?,
    val firstAppFrame: StackFrame?,
    val severity: Severity,
    val fullTrace: ParsedStackTrace,
)

@Serializable
data class LogAnalysisResult(
    val entries: List<LogEntry>,
    val detectedErrors: List<DetectedError>,
    val crashReports: List<CrashReport>,
)
