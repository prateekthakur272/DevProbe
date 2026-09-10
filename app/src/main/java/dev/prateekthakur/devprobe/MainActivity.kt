package dev.prateekthakur.devprobe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.prateekthakur.devprobe.data.crash.CrashMonitorService
import dev.prateekthakur.devprobe.data.crash.CrashPermissions
import dev.prateekthakur.devprobe.presentation.navigation.DevProbeNavGraph
import dev.prateekthakur.devprobe.presentation.theme.DevProbeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DevProbeApplication).container
        if (container.settingsStore.isCrashMonitorEnabled && CrashPermissions.hasReadLogsPermission(this)) {
            CrashMonitorService.start(this)
        }
        setContent {
            DevProbeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DevProbeNavGraph(container = container)
                }
            }
        }
    }
}
