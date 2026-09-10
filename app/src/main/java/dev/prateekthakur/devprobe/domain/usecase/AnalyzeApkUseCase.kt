package dev.prateekthakur.devprobe.domain.usecase

import dev.prateekthakur.devprobe.data.apk.ApkMetadataExtractor
import dev.prateekthakur.devprobe.data.apk.ApkSizeAnalyzer
import dev.prateekthakur.devprobe.data.apk.DexAnalyzer
import dev.prateekthakur.devprobe.data.apk.ManifestAnalyzer
import dev.prateekthakur.devprobe.data.apk.SigningAnalyzer
import dev.prateekthakur.devprobe.data.apk.StringScanner
import dev.prateekthakur.devprobe.data.security.SecurityRuleEngine
import dev.prateekthakur.devprobe.domain.model.ApkAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

sealed interface AnalysisProgress {
    data class InProgress(val step: String, val percent: Int) : AnalysisProgress
    data class Done(val result: ApkAnalysisResult) : AnalysisProgress
    data class Failed(val message: String) : AnalysisProgress
}

/** Orchestrates the deterministic APK analysis pipeline (§39): parse → permissions → signing → bytecode → strings → rules → size. */
class AnalyzeApkUseCase(
    private val metadataExtractor: ApkMetadataExtractor,
    private val manifestAnalyzer: ManifestAnalyzer,
    private val sizeAnalyzer: ApkSizeAnalyzer,
    private val securityRuleEngine: SecurityRuleEngine,
    private val signingAnalyzer: SigningAnalyzer,
    private val dexAnalyzer: DexAnalyzer,
    private val stringScanner: StringScanner,
) {
    operator fun invoke(apkFile: File): Flow<AnalysisProgress> = flow {
        emit(AnalysisProgress.InProgress("Reading APK metadata...", 7))
        val rawMetadata = withContext(Dispatchers.IO) { metadataExtractor.extractMetadata(apkFile) }

        emit(AnalysisProgress.InProgress("Parsing manifest...", 20))
        val manifest = withContext(Dispatchers.IO) { manifestAnalyzer.analyze(apkFile) }
        val metadata = rawMetadata.copy(compileSdk = manifest.compileSdkVersion)

        emit(AnalysisProgress.InProgress("Analyzing permissions...", 32))
        val permissions = withContext(Dispatchers.IO) { metadataExtractor.extractPermissions(apkFile) }

        emit(AnalysisProgress.InProgress("Verifying signing certificate...", 44))
        val signing = withContext(Dispatchers.IO) { signingAnalyzer.analyze(apkFile) }

        emit(AnalysisProgress.InProgress("Analyzing bytecode...", 56))
        val bytecode = withContext(Dispatchers.IO) { dexAnalyzer.analyze(apkFile) }

        emit(AnalysisProgress.InProgress("Scanning strings for secrets...", 68))
        val stringScan = withContext(Dispatchers.IO) { stringScanner.scan(apkFile) }

        emit(AnalysisProgress.InProgress("Running security rules...", 80))
        val findings = withContext(Dispatchers.IO) {
            securityRuleEngine.evaluate(manifest, permissions, signing, bytecode, stringScan, metadata.minSdk, metadata.targetSdk)
        }

        emit(AnalysisProgress.InProgress("Analyzing APK size...", 92))
        val size = withContext(Dispatchers.IO) { sizeAnalyzer.analyze(apkFile) }

        emit(AnalysisProgress.InProgress("Finalizing...", 98))
        val result = ApkAnalysisResult(metadata, manifest, permissions, findings, size, signing, bytecode, stringScan)
        emit(AnalysisProgress.Done(result))
    }
}
