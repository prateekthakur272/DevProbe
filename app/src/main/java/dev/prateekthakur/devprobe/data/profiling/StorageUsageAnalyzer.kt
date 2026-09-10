package dev.prateekthakur.devprobe.data.profiling

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager

data class StorageSnapshot(val appBytes: Long, val dataBytes: Long, val cacheBytes: Long)

/** Wraps [StorageStatsManager] — requires the same "Usage access" special app op as [UsageStatsAnalyzer]. */
class StorageUsageAnalyzer(context: Context) {

    private val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager

    fun snapshotFor(packageName: String): StorageSnapshot {
        val manager = storageStatsManager ?: return StorageSnapshot(0, 0, 0)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return StorageSnapshot(0, 0, 0)
        return runCatching {
            val stats = manager.queryStatsForPackage(StorageManager.UUID_DEFAULT, packageName, Process.myUserHandle())
            StorageSnapshot(appBytes = stats.appBytes, dataBytes = stats.dataBytes, cacheBytes = stats.cacheBytes)
        }.getOrDefault(StorageSnapshot(0, 0, 0))
    }
}
