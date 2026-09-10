package dev.prateekthakur.devprobe.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * No bottom bar / tabs — Home is the single hub screen, and every other destination
 * is a detail screen reached from a Home tile (or the top-bar settings action) and
 * left via the back arrow, like a launcher/dashboard rather than a tabbed app.
 */
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Filled.Home)
    data object ApkInspector : Screen("apk_inspector", "Inspect", Icons.Filled.Search)
    data object LogAnalyzer : Screen("log_analyzer", "Logs", Icons.AutoMirrored.Filled.List)
    data object Profiler : Screen("profiler", "Profiler", Icons.Filled.Build)
    data object DeviceDiagnostics : Screen("device_diagnostics", "Device", Icons.Filled.Phone)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    data object SessionDetail : Screen("session_detail/{sessionId}", "Session", Icons.AutoMirrored.Filled.List) {
        fun createRoute(sessionId: Long) = "session_detail/$sessionId"
    }

    companion object {
        val allScreens = listOf(Home, ApkInspector, LogAnalyzer, Profiler, DeviceDiagnostics, Settings)
    }
}
