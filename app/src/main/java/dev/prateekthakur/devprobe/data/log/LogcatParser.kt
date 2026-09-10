package dev.prateekthakur.devprobe.data.log

import dev.prateekthakur.devprobe.domain.model.LogEntry
import dev.prateekthakur.devprobe.domain.model.LogLevel

/** Parses pasted Logcat text into structured entries (§15.1). */
class LogcatParser {

    // Standard `threadtime` format: 09-09 19:32:12.123  1234  1256 E PaymentService: Payment failed
    private val threadTimeRegex = Regex(
        """^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+)\s+(\d+)\s+(\d+)\s+([VDIWEFA])\s+([^:]+):\s?(.*)$"""
    )

    // Brief format: E/PaymentService( 1234): Payment failed
    private val briefRegex = Regex(
        """^([VDIWEFA])/([^(]+)\(\s*(\d+)\)\s*:\s?(.*)$"""
    )

    fun parse(rawText: String): List<LogEntry> {
        return rawText.lineSequence()
            .filter { it.isNotBlank() }
            .map { line -> parseLine(line) }
            .toList()
    }

    private fun parseLine(line: String): LogEntry {
        threadTimeRegex.find(line)?.let { m ->
            val g = m.groupValues
            return LogEntry(g[1], g[2], g[3], levelOf(g[4]), g[5].trim(), g[6], line)
        }
        briefRegex.find(line)?.let { m ->
            val (lvl, tag, pid, msg) = m.destructured
            return LogEntry(null, pid, null, levelOf(lvl), tag.trim(), msg, line)
        }
        return LogEntry(null, null, null, LogLevel.UNKNOWN, null, line, line)
    }

    private fun levelOf(code: String): LogLevel = when (code) {
        "V" -> LogLevel.VERBOSE
        "D" -> LogLevel.DEBUG
        "I" -> LogLevel.INFO
        "W" -> LogLevel.WARN
        "E" -> LogLevel.ERROR
        "F", "A" -> LogLevel.FATAL
        else -> LogLevel.UNKNOWN
    }
}
