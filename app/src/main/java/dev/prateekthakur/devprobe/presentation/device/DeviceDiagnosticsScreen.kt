package dev.prateekthakur.devprobe.presentation.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.prateekthakur.devprobe.data.device.DeviceInfoProvider
import dev.prateekthakur.devprobe.presentation.apkinspector.formatBytes
import dev.prateekthakur.devprobe.presentation.components.InfoRow
import dev.prateekthakur.devprobe.presentation.components.RowDivider
import dev.prateekthakur.devprobe.presentation.components.ScreenTopBar
import dev.prateekthakur.devprobe.presentation.components.SectionCard
import dev.prateekthakur.devprobe.presentation.components.SectionHeader

@Composable
fun DeviceDiagnosticsScreen(onBack: () -> Unit, deviceInfoProvider: DeviceInfoProvider) {
    val diagnostics = remember { deviceInfoProvider.collect() }

    Scaffold(topBar = { ScreenTopBar(title = "Device", onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { SectionHeader("OS") }
            item {
                SectionCard {
                    InfoRow("Android version", diagnostics.os.androidVersion)
                    RowDivider()
                    InfoRow("API level", diagnostics.os.apiLevel.toString())
                    RowDivider()
                    InfoRow("Security patch", diagnostics.os.securityPatch ?: "unavailable")
                    RowDivider()
                    InfoRow("Build fingerprint", diagnostics.os.buildFingerprint)
                }
            }

            item { SectionHeader("Device") }
            item {
                SectionCard {
                    InfoRow("Manufacturer", diagnostics.hardware.manufacturer)
                    RowDivider()
                    InfoRow("Model", diagnostics.hardware.model)
                    RowDivider()
                    InfoRow("Supported ABIs", diagnostics.hardware.cpuAbis.joinToString())
                }
            }

            item { SectionHeader("Memory") }
            item {
                SectionCard {
                    InfoRow("Total memory", formatBytes(diagnostics.memory.totalBytes))
                    RowDivider()
                    InfoRow("Available memory", formatBytes(diagnostics.memory.availableBytes))
                    RowDivider()
                    InfoRow("Memory class", "${diagnostics.memory.memoryClassMb} MB")
                    RowDivider()
                    InfoRow("Large memory class", "${diagnostics.memory.largeMemoryClassMb} MB")
                    RowDivider()
                    InfoRow("Low memory", diagnostics.memory.lowMemory.toString())
                }
            }

            item { SectionHeader("Storage") }
            item {
                SectionCard {
                    InfoRow("Total storage", formatBytes(diagnostics.storage.totalBytes))
                    RowDivider()
                    InfoRow("Available storage", formatBytes(diagnostics.storage.availableBytes))
                }
            }
        }
    }
}
