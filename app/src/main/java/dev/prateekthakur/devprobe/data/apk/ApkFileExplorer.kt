package dev.prateekthakur.devprobe.data.apk

import dev.prateekthakur.devprobe.domain.model.ApkFileEntryDetail
import java.io.File
import java.util.zip.ZipFile

/**
 * Lists every entry inside an APK (§7.4 "APK Structure") and reads individual
 * entry contents on demand for the hex viewer — never extracts the whole
 * archive to disk, and caps single-entry reads so a huge asset can't stall the UI.
 */
class ApkFileExplorer {

    fun listEntries(apkFile: File): List<ApkFileEntryDetail> =
        ZipFile(apkFile).use { zip ->
            zip.entries().asSequence().map { entry ->
                ApkFileEntryDetail(
                    path = entry.name,
                    sizeBytes = if (entry.size >= 0) entry.size else 0L,
                    compressedSizeBytes = if (entry.compressedSize >= 0) entry.compressedSize else 0L,
                    crc32 = entry.crc,
                    isDirectory = entry.isDirectory,
                )
            }.toList()
        }

    /** Reads up to [maxBytes] of a single entry's content (for the hex/text preview). */
    fun readEntryBytes(apkFile: File, path: String, maxBytes: Int = 64 * 1024): ByteArray? =
        ZipFile(apkFile).use { zip ->
            val entry = zip.getEntry(path) ?: return null
            zip.getInputStream(entry).use { input ->
                val buffer = ByteArray(maxBytes)
                var total = 0
                while (total < maxBytes) {
                    val read = input.read(buffer, total, maxBytes - total)
                    if (read == -1) break
                    total += read
                }
                buffer.copyOf(total)
            }
        }
}
