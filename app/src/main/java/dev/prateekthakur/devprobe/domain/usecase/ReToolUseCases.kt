package dev.prateekthakur.devprobe.domain.usecase

import dev.prateekthakur.devprobe.data.apk.ApkFileExplorer
import dev.prateekthakur.devprobe.data.apk.DexClassBrowser
import dev.prateekthakur.devprobe.data.apk.ElfAnalyzer
import dev.prateekthakur.devprobe.domain.model.ApkFileEntryDetail
import dev.prateekthakur.devprobe.domain.model.DexClassInfo
import dev.prateekthakur.devprobe.domain.model.NativeLibraryInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** On-demand reverse-engineering tools — deliberately kept out of the eager
 * analysis pipeline (see AnalyzeApkUseCase) since results can be large and are
 * only needed if the user opens the corresponding tab. */

class ExploreApkFilesUseCase(private val explorer: ApkFileExplorer) {
    suspend fun listEntries(apkFile: File): List<ApkFileEntryDetail> =
        withContext(Dispatchers.IO) { explorer.listEntries(apkFile) }

    suspend fun readEntryBytes(apkFile: File, path: String, maxBytes: Int = 64 * 1024): ByteArray? =
        withContext(Dispatchers.IO) { explorer.readEntryBytes(apkFile, path, maxBytes) }
}

class BrowseClassesUseCase(private val browser: DexClassBrowser) {
    suspend operator fun invoke(apkFile: File): List<DexClassInfo> =
        withContext(Dispatchers.IO) { browser.browse(apkFile) }
}

class AnalyzeNativeLibrariesUseCase(private val elfAnalyzer: ElfAnalyzer) {
    suspend operator fun invoke(apkFile: File): List<NativeLibraryInfo> =
        withContext(Dispatchers.IO) { elfAnalyzer.analyzeAll(apkFile) }
}
