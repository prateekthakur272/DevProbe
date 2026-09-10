package dev.prateekthakur.devprobe.domain.model

/** Developer-friendly error shape per §25: reason + actionable next steps. */
data class AppError(
    val reason: String,
    val suggestions: List<String>,
) {
    companion object {
        fun corruptedApk(detail: String? = null) = AppError(
            reason = "The APK appears to be corrupted or uses an unsupported package structure." +
                (detail?.let { " ($it)" } ?: ""),
            suggestions = listOf("Verify the APK file", "Export a fresh APK", "Try another APK"),
        )

        fun unreadableFile(detail: String? = null) = AppError(
            reason = "Unable to read the selected file." + (detail?.let { " ($it)" } ?: ""),
            suggestions = listOf("Check the file is accessible", "Try selecting the file again"),
        )

        fun emptyInput() = AppError(
            reason = "No content was provided to analyze.",
            suggestions = listOf("Paste a Logcat excerpt or crash report", "Import a .txt/.log file"),
        )

        fun unexpected(detail: String) = AppError(
            reason = "Something went wrong during analysis: $detail",
            suggestions = listOf("Try again", "If this persists, try a smaller or different input"),
        )
    }
}
