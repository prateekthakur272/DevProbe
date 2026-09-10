package dev.prateekthakur.devprobe.data.profiling

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dev.prateekthakur.devprobe.domain.model.AppProfile
import dev.prateekthakur.devprobe.domain.model.ProfilingTimeRange

/** Combines usage, network, and storage analyzers into one per-app resource snapshot. */
class AppProfiler(
    private val context: Context,
    private val usageStatsAnalyzer: UsageStatsAnalyzer = UsageStatsAnalyzer(context),
    private val networkUsageAnalyzer: NetworkUsageAnalyzer = NetworkUsageAnalyzer(context),
    private val storageUsageAnalyzer: StorageUsageAnalyzer = StorageUsageAnalyzer(context),
) {
    private val packageManager = context.packageManager

    fun profileInstalledApps(range: ProfilingTimeRange, includeSystemApps: Boolean = false): List<AppProfile> {
        val usageByPackage = usageStatsAnalyzer.snapshotByPackage(range)
        val apps = runCatching { packageManager.getInstalledApplications(PackageManager.GET_META_DATA) }.getOrDefault(emptyList())

        return apps
            .filter { includeSystemApps || it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || usageByPackage.containsKey(it.packageName) }
            .map { appInfo -> buildProfile(appInfo, range, usageByPackage[appInfo.packageName]) }
            .sortedByDescending { it.foregroundTimeMs }
    }

    private fun buildProfile(appInfo: ApplicationInfo, range: ProfilingTimeRange, usage: UsageSnapshot?): AppProfile {
        val label = runCatching { packageManager.getApplicationLabel(appInfo).toString() }.getOrDefault(appInfo.packageName)
        val network = networkUsageAnalyzer.snapshotFor(appInfo.packageName, range)
        val storage = storageUsageAnalyzer.snapshotFor(appInfo.packageName)
        return AppProfile(
            packageName = appInfo.packageName,
            appLabel = label,
            foregroundTimeMs = usage?.foregroundTimeMs ?: 0,
            lastUsedEpochMillis = usage?.lastUsedEpochMillis ?: 0,
            launchCount = usage?.launchCount ?: 0,
            wifiBytes = network.wifiBytes,
            mobileBytes = network.mobileBytes,
            appSizeBytes = storage.appBytes,
            dataSizeBytes = storage.dataBytes,
            cacheSizeBytes = storage.cacheBytes,
        )
    }
}
