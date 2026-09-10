package dev.prateekthakur.devprobe.data.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs

data class OsInfo(
    val androidVersion: String,
    val apiLevel: Int,
    val securityPatch: String?,
    val buildFingerprint: String,
)

data class DeviceHardwareInfo(
    val manufacturer: String,
    val model: String,
    val cpuAbis: List<String>,
)

data class MemoryInfo(
    val totalBytes: Long,
    val availableBytes: Long,
    val memoryClassMb: Int,
    val largeMemoryClassMb: Int,
    val lowMemory: Boolean,
)

data class StorageInfo(
    val totalBytes: Long,
    val availableBytes: Long,
)

data class DeviceDiagnostics(
    val os: OsInfo,
    val hardware: DeviceHardwareInfo,
    val memory: MemoryInfo,
    val storage: StorageInfo,
)

/** Reads only public, non-privileged Android APIs (§14) — no root required. */
class DeviceInfoProvider(private val context: Context) {

    fun collect(): DeviceDiagnostics {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val statFs = StatFs(context.filesDir.absolutePath)
        val totalStorage = statFs.blockCountLong * statFs.blockSizeLong
        val availableStorage = statFs.availableBlocksLong * statFs.blockSizeLong

        return DeviceDiagnostics(
            os = OsInfo(
                androidVersion = Build.VERSION.RELEASE,
                apiLevel = Build.VERSION.SDK_INT,
                securityPatch = runCatching { Build.VERSION.SECURITY_PATCH }.getOrNull(),
                buildFingerprint = Build.FINGERPRINT,
            ),
            hardware = DeviceHardwareInfo(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                cpuAbis = Build.SUPPORTED_ABIS.toList(),
            ),
            memory = MemoryInfo(
                totalBytes = memInfo.totalMem,
                availableBytes = memInfo.availMem,
                memoryClassMb = activityManager.memoryClass,
                largeMemoryClassMb = activityManager.largeMemoryClass,
                lowMemory = memInfo.lowMemory,
            ),
            storage = StorageInfo(
                totalBytes = totalStorage,
                availableBytes = availableStorage,
            ),
        )
    }
}
