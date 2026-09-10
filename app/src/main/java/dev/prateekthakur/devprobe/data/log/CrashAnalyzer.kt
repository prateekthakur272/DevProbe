package dev.prateekthakur.devprobe.data.log

import dev.prateekthakur.devprobe.domain.model.CrashReport
import dev.prateekthakur.devprobe.domain.model.DetectedError
import dev.prateekthakur.devprobe.domain.model.ErrorCategory
import dev.prateekthakur.devprobe.domain.model.LogAnalysisResult
import dev.prateekthakur.devprobe.domain.model.ParsedStackTrace
import dev.prateekthakur.devprobe.domain.model.Severity

/** Produces structured crash reports and detects common error categories (§15.2, §16). */
class CrashAnalyzer(
    private val logcatParser: LogcatParser = LogcatParser(),
    private val stackTraceParser: StackTraceParser = StackTraceParser(),
) {

    private val errorPatterns: List<Pair<Regex, ErrorCategory>> = listOf(
        Regex("FATAL EXCEPTION") to ErrorCategory.FATAL_EXCEPTION,
        Regex("ANR in|Application Not Responding", RegexOption.IGNORE_CASE) to ErrorCategory.ANR,
        Regex("SIGSEGV|SIGABRT|native crash|\\*\\*\\* \\*\\*\\* \\*\\*\\*", RegexOption.IGNORE_CASE) to ErrorCategory.NATIVE_CRASH,
        Regex("NullPointerException") to ErrorCategory.NULL_POINTER,
        Regex("IllegalStateException") to ErrorCategory.ILLEGAL_STATE,
        Regex("OutOfMemoryError") to ErrorCategory.OUT_OF_MEMORY,
        Regex("SQLiteException|SQLiteConstraintException") to ErrorCategory.SQLITE,
        Regex("UnknownHostException|SocketTimeoutException|ConnectException|SSLHandshakeException") to ErrorCategory.NETWORK,
        Regex("HTTP\\s?(4\\d{2}|5\\d{2})|HttpException") to ErrorCategory.HTTP,
        Regex("SecurityException|Permission Denial") to ErrorCategory.SECURITY,
    )

    fun analyze(rawText: String, appPackage: String? = null): LogAnalysisResult {
        val entries = logcatParser.parse(rawText)
        val detectedErrors = detectErrors(rawText)
        val crashReports = extractCrashReports(rawText, appPackage)
        return LogAnalysisResult(entries, detectedErrors, crashReports)
    }

    private fun detectErrors(rawText: String): List<DetectedError> {
        val results = mutableListOf<DetectedError>()
        rawText.lines().forEach { line ->
            for ((regex, category) in errorPatterns) {
                if (regex.containsMatchIn(line)) {
                    results += DetectedError(category, line.trim().take(160), line)
                    break
                }
            }
        }
        return results
    }

    private fun extractCrashReports(rawText: String, appPackage: String?): List<CrashReport> {
        val reports = mutableListOf<CrashReport>()
        val fullTraceRegex = Regex("""^(?:FATAL EXCEPTION.*?:\s*)?[\w.$]+(?:Exception|Error|Throwable)""")
        val lines = rawText.lines()
        var i = 0
        while (i < lines.size) {
            if (fullTraceRegex.matches(lines[i].trim()) || lines[i].contains("FATAL EXCEPTION")) {
                val block = lines.drop(i).joinToString("\n")
                val parsed = stackTraceParser.parse(block, appPackage)
                if (parsed != null) {
                    reports += toCrashReport(parsed)
                    // Skip past this trace's frames to avoid re-matching nested "Caused by" as a new top-level crash.
                    i += 1 + countConsumedLines(lines, i)
                    continue
                }
            }
            i++
        }
        return reports
    }

    private fun countConsumedLines(lines: List<String>, startIndex: Int): Int {
        var count = 0
        var idx = startIndex + 1
        val frameOrCauseRegex = Regex("""^\s*at\s+.+\(.*\)\s*$|^Caused by:.*$|^\s*\.\.\.\s*\d+\s*more\s*$""")
        while (idx < lines.size && frameOrCauseRegex.matches(lines[idx])) {
            count++
            idx++
        }
        return count
    }

    private fun toCrashReport(parsed: ParsedStackTrace): CrashReport {
        val firstAppFrame = parsed.frames.firstOrNull { it.isAppFrame }
            ?: parsed.causedBy?.frames?.firstOrNull { it.isAppFrame }
        val severity = when (parsed.exceptionType) {
            "OutOfMemoryError" -> Severity.CRITICAL
            else -> if (parsed.exceptionType.endsWith("Error")) Severity.CRITICAL else Severity.HIGH
        }
        return CrashReport(
            exceptionType = parsed.exceptionType,
            message = parsed.message,
            thread = parsed.thread,
            firstAppFrame = firstAppFrame,
            severity = severity,
            fullTrace = parsed,
        )
    }
}
