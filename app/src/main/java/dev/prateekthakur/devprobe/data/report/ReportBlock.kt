package dev.prateekthakur.devprobe.data.report

/** A layout-agnostic description of one piece of report content, rendered/paginated by [PdfReportRenderer]. */
sealed interface ReportBlock {
    data class Section(val text: String) : ReportBlock
    data class KeyValue(val rows: List<Pair<String, String>>) : ReportBlock
    data class Paragraph(val text: String) : ReportBlock
    data class Bullets(val items: List<String>) : ReportBlock
    data class Finding(
        val severityLabel: String,
        val title: String,
        val description: String,
        val evidence: String? = null,
        val recommendation: String? = null,
    ) : ReportBlock
    data class Code(val lines: List<String>) : ReportBlock
    data object Divider : ReportBlock
    data class Spacer(val height: Float = 10f) : ReportBlock
}

fun formatReportBytes(bytes: Long): String {
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
