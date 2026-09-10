package dev.prateekthakur.devprobe.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.prateekthakur.devprobe.di.AppContainer
import dev.prateekthakur.devprobe.presentation.apkinspector.ApkInspectorScreen
import dev.prateekthakur.devprobe.presentation.apkinspector.ApkInspectorViewModel
import dev.prateekthakur.devprobe.presentation.device.DeviceDiagnosticsScreen
import dev.prateekthakur.devprobe.presentation.home.HomeScreen
import dev.prateekthakur.devprobe.presentation.home.HomeViewModel
import dev.prateekthakur.devprobe.presentation.loganalyzer.LogAnalyzerScreen
import dev.prateekthakur.devprobe.presentation.loganalyzer.LogAnalyzerViewModel
import dev.prateekthakur.devprobe.presentation.profiler.ProfilerScreen
import dev.prateekthakur.devprobe.presentation.profiler.ProfilerViewModel
import dev.prateekthakur.devprobe.presentation.settings.SettingsScreen

/**
 * No shared top app bar — each screen owns its own (see ScreenTopBar.kt) so that,
 * e.g., only Home shows the Settings action and only the analysis screens show an
 * export action. This is a plain NavHost, not a Scaffold with shared chrome.
 */
@Composable
fun DevProbeNavGraph(container: AppContainer, navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onOpenApkInspector = { navController.navigate(Screen.ApkInspector.route) },
                onOpenLogAnalyzer = { navController.navigate(Screen.LogAnalyzer.route) },
                onOpenProfiler = { navController.navigate(Screen.Profiler.route) },
                onOpenDeviceDiagnostics = { navController.navigate(Screen.DeviceDiagnostics.route) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                createViewModel = { HomeViewModel(container.analysisRepository) },
            )
        }
        composable(Screen.ApkInspector.route) {
            ApkInspectorScreen(
                onBack = { navController.popBackStack() },
                createViewModel = {
                    ApkInspectorViewModel(
                        apkImporter = container.apkImporter,
                        analyzeApkUseCase = container.analyzeApkUseCase,
                        explainSecurityUseCase = container.explainSecurityUseCase,
                        analysisRepository = container.analysisRepository,
                        exploreApkFilesUseCase = container.exploreApkFilesUseCase,
                        browseClassesUseCase = container.browseClassesUseCase,
                        analyzeNativeLibrariesUseCase = container.analyzeNativeLibrariesUseCase,
                        reportExporter = container.reportExporter,
                        nowProvider = { System.currentTimeMillis() },
                    )
                },
            )
        }
        composable(Screen.LogAnalyzer.route) {
            LogAnalyzerScreen(
                onBack = { navController.popBackStack() },
                createViewModel = {
                    LogAnalyzerViewModel(
                        parseLogUseCase = container.parseLogUseCase,
                        explainCrashUseCase = container.explainCrashUseCase,
                        analysisRepository = container.analysisRepository,
                        reportExporter = container.reportExporter,
                        nowProvider = { System.currentTimeMillis() },
                    )
                },
            )
        }
        composable(Screen.Profiler.route) {
            ProfilerScreen(
                onBack = { navController.popBackStack() },
                createViewModel = { ProfilerViewModel(container.profileInstalledAppsUseCase) },
            )
        }
        composable(Screen.DeviceDiagnostics.route) {
            DeviceDiagnosticsScreen(
                onBack = { navController.popBackStack() },
                deviceInfoProvider = container.deviceInfoProvider,
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                settingsStore = container.settingsStore,
                analysisRepository = container.analysisRepository,
                apkImporter = container.apkImporter,
            )
        }
    }
}
