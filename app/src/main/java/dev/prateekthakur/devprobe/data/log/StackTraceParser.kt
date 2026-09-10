package dev.prateekthakur.devprobe.data.log

import dev.prateekthakur.devprobe.domain.model.ParsedStackTrace
import dev.prateekthakur.devprobe.domain.model.StackFrame

/** Extracts exception type/message/frames from a Java/Kotlin stack trace (§15.3). */
class StackTraceParser {

    private val exceptionLineRegex = Regex("""^(?:FATAL EXCEPTION.*?:\s*)?([\w.$]+(?:Exception|Error|Throwable))(?::\s*(.*))?$""")
    private val causedByRegex = Regex("""^Caused by:\s*([\w.$]+(?:Exception|Error|Throwable))(?::\s*(.*))?$""")
    private val frameRegex = Regex("""^\s*at\s+([\w.$]+)\.([\w$<>]+)\(([^:)]*)(?::(\d+))?\)\s*$""")
    private val threadLineRegex = Regex(
        """Exception in thread\s+"([^"]+)"|FATAL EXCEPTION:\s*(\S+)|^\s*Process:.*|thread\s+([\w.\-]+)""",
        RegexOption.IGNORE_CASE
    )

    private val nonAppPrefixes = listOf(
        "android.", "androidx.", "java.", "javax.", "kotlin.", "kotlinx.",
        "dalvik.", "com.android.", "libcore.", "sun.", "org.jetbrains."
    )

    fun parse(rawText: String, appPackage: String? = null): ParsedStackTrace? {
        val lines = rawText.lines()
        var startIndex = -1
        for ((i, line) in lines.withIndex()) {
            if (exceptionLineRegex.matches(line.trim())) {
                startIndex = i
                break
            }
        }
        if (startIndex == -1) return null
        return parseBlock(lines, startIndex, appPackage)
    }

    private fun parseBlock(lines: List<String>, startIndex: Int, appPackage: String?): ParsedStackTrace {
        val rawHeaderLine = lines[startIndex].trim()
        val headerLine = rawHeaderLine.removePrefix("Caused by:").trim()
        val match = exceptionLineRegex.find(headerLine)
            ?: return ParsedStackTrace(exceptionType = headerLine, message = null, thread = null, frames = emptyList())
        val exceptionType = match.groupValues[1]
        val message = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }

        var thread: String? = null
        for (i in maxOf(0, startIndex - 3) until startIndex) {
            threadLineRegex.find(lines[i])?.let { m ->
                val found = m.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
                    ?: m.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
                    ?: m.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
                if (found != null) thread = found
            }
        }

        val frames = mutableListOf<StackFrame>()
        var causedByIndex = -1
        for (i in (startIndex + 1) until lines.size) {
            val line = lines[i]
            if (causedByRegex.matches(line.trim())) {
                causedByIndex = i
                break
            }
            frameRegex.find(line)?.let { fm ->
                val declaringClass = fm.groupValues[1]
                val methodName = fm.groupValues[2]
                val file = fm.groupValues[3].takeIf { it.isNotBlank() }
                val lineNo = fm.groupValues[4].toIntOrNull()
                val isApp = appPackage?.let { declaringClass.startsWith(it) }
                    ?: nonAppPrefixes.none { declaringClass.startsWith(it) }
                frames += StackFrame(declaringClass, methodName, file, lineNo, isApp)
            }
        }

        val causedBy = if (causedByIndex != -1) parseBlock(lines, causedByIndex, appPackage) else null

        return ParsedStackTrace(
            exceptionType = exceptionType,
            message = message,
            thread = thread,
            frames = frames,
            causedBy = causedBy,
        )
    }
}
