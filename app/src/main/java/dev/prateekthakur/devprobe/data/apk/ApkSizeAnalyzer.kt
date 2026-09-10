package dev.prateekthakur.devprobe.data.apk

import dev.prateekthakur.devprobe.domain.model.ApkFileEntry
import dev.prateekthakur.devprobe.domain.model.ApkSizeBreakdown
import java.io.File
import java.util.zip.ZipFile

/**
 * Reads APK zip central-directory entries only (no extraction) so this scales to
 * large (500MB+) APKs without loading file contents into memory.
 */
class ApkSizeAnalyzer {

    fun analyze(apkFile: File): ApkSizeBreakdown {
        var dex = 0L
        var natives = 0L
        var resources = 0L
        var assets = 0L
        var other = 0L
        var fileCount = 0
        val allFiles = mutableListOf<ApkFileEntry>()

        ZipFile(apkFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                fileCount++
                val size = if (entry.size >= 0) entry.size else 0L
                allFiles += ApkFileEntry(entry.name, size)
                when {
                    entry.name.endsWith(".dex") -> dex += size
                    entry.name.startsWith("lib/") -> natives += size
                    entry.name == "resources.arsc" || entry.name.startsWith("res/") -> resources += size
                    entry.name.startsWith("assets/") -> assets += size
                    else -> other += size
                }
            }
        }

        return ApkSizeBreakdown(
            totalBytes = apkFile.length(),
            fileCount = fileCount,
            dexBytes = dex,
            nativeLibBytes = natives,
            resourceBytes = resources,
            assetBytes = assets,
            otherBytes = other,
            largestFiles = allFiles.sortedByDescending { it.sizeBytes }.take(10),
        )
    }
}
