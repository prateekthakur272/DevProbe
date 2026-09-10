package dev.prateekthakur.devprobe.data.report

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dev.prateekthakur.devprobe.domain.model.ApkAnalysisResult
import dev.prateekthakur.devprobe.domain.model.LogAnalysisResult
import java.io.File
import java.util.UUID

/**
 * Generates beautifully formatted, on-device PDF reports and saves them straight into the
 * device's public Downloads folder — like a browser download — so the user finds them where
 * they'd expect, with their PDF viewer's own share/export actions available from there.
 */
class ReportExporter(private val context: Context) {

    private val scratchDir: File
        get() = File(context.cacheDir, "reports").apply { mkdirs() }

    fun exportApkReport(title: String, subtitle: String, result: ApkAnalysisResult): Uri =
        render(title, subtitle, buildApkReportBlocks(result), "apk", title)

    fun exportLogReport(title: String, subtitle: String, result: LogAnalysisResult): Uri =
        render(title, subtitle, buildLogReportBlocks(result, subtitle), "log", title)

    /** WRITE_EXTERNAL_STORAGE is only required below Android 10 — MediaStore.Downloads needs no permission on API 29+. */
    fun requiresLegacyWritePermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    private fun render(title: String, subtitle: String, blocks: List<ReportBlock>, kind: String, displayTitle: String): Uri {
        val scratchFile = File(scratchDir, "devprobe-$kind-report-${UUID.randomUUID()}.pdf")
        PdfReportRenderer(title, subtitle).renderAndSave(blocks, scratchFile)
        val displayName = "${sanitizeFileName(displayTitle)}-$kind-report.pdf"
        return saveToDownloads(scratchFile, displayName)
    }

    private fun saveToDownloads(scratchFile: File, displayName: String): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create a Downloads entry for the report")
            resolver.openOutputStream(uri)?.use { out -> scratchFile.inputStream().use { it.copyTo(out) } }
            return uri
        }

        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir.mkdirs()
        val destination = File(downloadsDir, displayName)
        scratchFile.copyTo(destination, overwrite = true)
        MediaScannerConnection.scanFile(context, arrayOf(destination.absolutePath), arrayOf("application/pdf"), null)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destination)
    }

    private fun sanitizeFileName(name: String): String =
        name.ifBlank { "report" }.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(60)
}
