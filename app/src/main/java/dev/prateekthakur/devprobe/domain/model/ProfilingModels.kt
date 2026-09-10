package dev.prateekthakur.devprobe.domain.model

enum class ProfilingTimeRange(val label: String, val days: Int) {
    TODAY("Today", 1),
    LAST_7_DAYS("7 days", 7),
    LAST_30_DAYS("30 days", 30),
}

/** A per-app resource snapshot — storage, network, and foreground usage time only; CPU/RAM is not obtainable non-root (§ profiling scope). */
data class AppProfile(
    val packageName: String,
    val appLabel: String,
    val foregroundTimeMs: Long,
    val lastUsedEpochMillis: Long,
    val launchCount: Int,
    val wifiBytes: Long,
    val mobileBytes: Long,
    val appSizeBytes: Long,
    val dataSizeBytes: Long,
    val cacheSizeBytes: Long,
) {
    val totalNetworkBytes: Long get() = wifiBytes + mobileBytes
    val totalStorageBytes: Long get() = appSizeBytes + dataSizeBytes + cacheSizeBytes
}
