package dev.prateekthakur.devprobe.data.apk

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Copies a SAF-provided APK Uri into app-private storage, streaming so we never
 * hold the whole file in memory (supports APKs up to 500MB+ per requirements).
 */
class ApkImporter(private val context: Context) {

    private val cacheDir: File
        get() = File(context.filesDir, "apk_cache").apply { mkdirs() }

    suspend fun importFromUri(uri: Uri): File = withContext(Dispatchers.IO) {
        val destination = File(cacheDir, "${UUID.randomUUID()}.apk")
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                }
            }
        } ?: throw IllegalStateException("Unable to open input stream for the selected file.")
        destination
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    fun delete(file: File) {
        if (file.exists() && file.parentFile == cacheDir) file.delete()
    }
}
