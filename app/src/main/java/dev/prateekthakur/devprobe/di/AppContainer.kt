package dev.prateekthakur.devprobe.di

import android.content.Context
import androidx.room.Room
import dev.prateekthakur.devprobe.data.ai.AiService
import dev.prateekthakur.devprobe.data.ai.MockAiService
import dev.prateekthakur.devprobe.data.apk.ApkFileExplorer
import dev.prateekthakur.devprobe.data.apk.ApkImporter
import dev.prateekthakur.devprobe.data.apk.ApkMetadataExtractor
import dev.prateekthakur.devprobe.data.apk.ApkSizeAnalyzer
import dev.prateekthakur.devprobe.data.apk.DexAnalyzer
import dev.prateekthakur.devprobe.data.apk.DexClassBrowser
import dev.prateekthakur.devprobe.data.apk.ElfAnalyzer
import dev.prateekthakur.devprobe.data.apk.ManifestAnalyzer
import dev.prateekthakur.devprobe.data.apk.SigningAnalyzer
import dev.prateekthakur.devprobe.data.apk.StringScanner
import dev.prateekthakur.devprobe.data.device.DeviceInfoProvider
import dev.prateekthakur.devprobe.data.local.SettingsStore
import dev.prateekthakur.devprobe.data.local.db.AppDatabase
import dev.prateekthakur.devprobe.data.log.CrashAnalyzer
import dev.prateekthakur.devprobe.data.profiling.AppProfiler
import dev.prateekthakur.devprobe.data.report.ReportExporter
import dev.prateekthakur.devprobe.data.repository.AnalysisRepository
import dev.prateekthakur.devprobe.data.security.SecurityRuleEngine
import dev.prateekthakur.devprobe.domain.usecase.AnalyzeApkUseCase
import dev.prateekthakur.devprobe.domain.usecase.AnalyzeNativeLibrariesUseCase
import dev.prateekthakur.devprobe.domain.usecase.BrowseClassesUseCase
import dev.prateekthakur.devprobe.domain.usecase.ExplainCrashUseCase
import dev.prateekthakur.devprobe.domain.usecase.ExplainSecurityUseCase
import dev.prateekthakur.devprobe.domain.usecase.ExploreApkFilesUseCase
import dev.prateekthakur.devprobe.domain.usecase.ParseLogUseCase
import dev.prateekthakur.devprobe.domain.usecase.ProfileInstalledAppsUseCase

/** Manual DI container — no framework overhead needed for an app this size (§5's recommended stack omits one). */
class AppContainer(context: Context) {

    private val database = Room.databaseBuilder(context, AppDatabase::class.java, "devprobe.db")
        .fallbackToDestructiveMigration(true)
        .build()

    val settingsStore = SettingsStore(context)
    val analysisRepository = AnalysisRepository(database.analysisSessionDao())

    val apkImporter = ApkImporter(context)
    private val apkMetadataExtractor = ApkMetadataExtractor(context)
    private val manifestAnalyzer = ManifestAnalyzer()
    private val apkSizeAnalyzer = ApkSizeAnalyzer()
    private val securityRuleEngine = SecurityRuleEngine()
    private val signingAnalyzer = SigningAnalyzer(context)
    private val dexAnalyzer = DexAnalyzer()
    private val stringScanner = StringScanner()
    private val crashAnalyzer = CrashAnalyzer()
    val aiService: AiService = MockAiService()
    val deviceInfoProvider = DeviceInfoProvider(context)
    val reportExporter = ReportExporter(context)

    private val apkFileExplorer = ApkFileExplorer()
    private val dexClassBrowser = DexClassBrowser()
    private val elfAnalyzer = ElfAnalyzer()
    private val appProfiler = AppProfiler(context)

    val analyzeApkUseCase = AnalyzeApkUseCase(
        apkMetadataExtractor, manifestAnalyzer, apkSizeAnalyzer, securityRuleEngine, signingAnalyzer, dexAnalyzer, stringScanner,
    )
    val parseLogUseCase = ParseLogUseCase(crashAnalyzer)
    val explainCrashUseCase = ExplainCrashUseCase(aiService)
    val explainSecurityUseCase = ExplainSecurityUseCase(aiService)

    val exploreApkFilesUseCase = ExploreApkFilesUseCase(apkFileExplorer)
    val browseClassesUseCase = BrowseClassesUseCase(dexClassBrowser)
    val analyzeNativeLibrariesUseCase = AnalyzeNativeLibrariesUseCase(elfAnalyzer)
    val profileInstalledAppsUseCase = ProfileInstalledAppsUseCase(appProfiler)
}
