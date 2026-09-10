package dev.prateekthakur.devprobe.data.crash

import dev.prateekthakur.devprobe.data.log.CrashAnalyzer
import dev.prateekthakur.devprobe.data.log.LogcatParser
import dev.prateekthakur.devprobe.domain.model.CrashReport
import dev.prateekthakur.devprobe.domain.model.LogAnalysisResult

/** A crash trace block captured from a live logcat stream, already run through [CrashAnalyzer]. */
data class CapturedCrash(
    val result: LogAnalysisResult,
    val crashReport: CrashReport,
    val packageName: String?,
)

/**
 * Feeds live `logcat -v threadtime` lines one at a time and recognizes a complete
 * FATAL EXCEPTION trace block, reusing [CrashAnalyzer]'s existing parsing so there
 * is no second copy of the exception/stack-frame regexes.
 *
 * A crashing process always dies right after printing its trace, so the block is
 * terminated once a line from a different pid appears (with a line-count safety
 * valve in case that never happens).
 */
class LiveCrashDetector(
    private val crashAnalyzer: CrashAnalyzer = CrashAnalyzer(),
    private val logcatParser: LogcatParser = LogcatParser(),
) {
    private var capturing = false
    private var capturedPid: String? = null
    private val buffer = mutableListOf<String>()

    private val processLineRegex = Regex("""Process:\s*([a-zA-Z0-9_.]+),\s*PID:\s*(\d+)""")

    fun feed(rawLine: String): CapturedCrash? {
        if (rawLine.isBlank()) return null
        val entry = logcatParser.parse(rawLine).firstOrNull() ?: return null

        if (!capturing) {
            if (entry.message.contains("FATAL EXCEPTION")) {
                startCapture(entry.pid, entry.message)
            }
            return null
        }

        if (entry.pid != null && entry.pid != capturedPid) {
            val finished = finalize()
            if (entry.message.contains("FATAL EXCEPTION")) {
                startCapture(entry.pid, entry.message)
            }
            return finished
        }

        buffer += entry.message
        return if (buffer.size > MAX_BUFFERED_LINES) finalize() else null
    }

    private fun startCapture(pid: String?, firstLine: String) {
        capturing = true
        capturedPid = pid
        buffer.clear()
        buffer += firstLine
    }

    private fun finalize(): CapturedCrash? {
        val block = buffer.joinToString("\n")
        capturing = false
        capturedPid = null
        buffer.clear()
        if (block.isBlank()) return null

        val packageName = processLineRegex.find(block)?.groupValues?.getOrNull(1)
        val result = crashAnalyzer.analyze(block, packageName)
        val primary = result.crashReports.firstOrNull() ?: return null
        return CapturedCrash(result, primary, packageName)
    }

    private companion object {
        const val MAX_BUFFERED_LINES = 500
    }
}
