package dev.prateekthakur.devprobe.data.report

import dev.prateekthakur.devprobe.domain.model.LogAnalysisResult
import dev.prateekthakur.devprobe.domain.model.StackFrame

/** Maps a completed [LogAnalysisResult] into report content — no rendering concerns here. */
fun buildLogReportBlocks(result: LogAnalysisResult, appPackage: String?): List<ReportBlock> {
    val blocks = mutableListOf<ReportBlock>()

    blocks += ReportBlock.Section("Summary")
    blocks += ReportBlock.KeyValue(
        listOf(
            "App package" to (appPackage?.takeIf { it.isNotBlank() } ?: "Not specified"),
            "Parsed log entries" to result.entries.size.toString(),
            "Detected errors" to result.detectedErrors.size.toString(),
            "Crash reports" to result.crashReports.size.toString(),
        ),
    )

    blocks += ReportBlock.Spacer()
    blocks += ReportBlock.Section("Crash Reports (${result.crashReports.size})")
    if (result.crashReports.isEmpty()) {
        blocks += ReportBlock.Paragraph("No structured crash or stack trace was found in this input.")
    } else {
        result.crashReports.forEachIndexed { index, crash ->
            blocks += ReportBlock.Finding(
                severityLabel = crash.severity.name,
                title = crash.exceptionType,
                description = crash.message ?: "(no message)",
                evidence = crash.firstAppFrame?.let { formatFrame(it) },
                recommendation = crash.thread?.let { "Thread: $it" },
            )
            val frames = crash.fullTrace.frames.take(15).map { "at ${formatFrame(it)}" }
            if (frames.isNotEmpty()) blocks += ReportBlock.Code(frames)
            crash.fullTrace.causedBy?.let { caused ->
                blocks += ReportBlock.Paragraph("Caused by: ${caused.exceptionType}${caused.message?.let { ": $it" } ?: ""}")
                val causedFrames = caused.frames.take(10).map { "at ${formatFrame(it)}" }
                if (causedFrames.isNotEmpty()) blocks += ReportBlock.Code(causedFrames)
            }
            if (index != result.crashReports.lastIndex) blocks += ReportBlock.Divider
        }
    }

    if (result.detectedErrors.isNotEmpty()) {
        blocks += ReportBlock.Spacer()
        blocks += ReportBlock.Section("Other Detected Errors (${result.detectedErrors.size})")
        blocks += ReportBlock.Bullets(result.detectedErrors.take(50).map { "[${it.category.name}] ${it.summary}" })
    }

    return blocks
}

private fun formatFrame(frame: StackFrame): String =
    "${frame.declaringClass}.${frame.method}(${frame.file ?: "?"}:${frame.line ?: "?"})"
