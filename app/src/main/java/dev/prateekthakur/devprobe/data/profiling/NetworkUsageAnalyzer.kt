package dev.prateekthakur.devprobe.data.profiling

import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import dev.prateekthakur.devprobe.domain.model.ProfilingTimeRange

data class NetworkSnapshot(val wifiBytes: Long, val mobileBytes: Long)

/** Wraps [NetworkStatsManager] — requires the same "Usage access" special app op as [UsageStatsAnalyzer]. */
class NetworkUsageAnalyzer(private val context: Context) {

    private val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
    private val packageManager = context.packageManager

    fun snapshotFor(packageName: String, range: ProfilingTimeRange): NetworkSnapshot {
        val manager = networkStatsManager ?: return NetworkSnapshot(0, 0)
        val uid = runCatching { packageManager.getApplicationInfo(packageName, 0).uid }
            .getOrElse { return NetworkSnapshot(0, 0) }

        val end = System.currentTimeMillis()
        val start = end - range.days * 24L * 60 * 60 * 1000

        val wifi = sumBytes(manager, ConnectivityManager.TYPE_WIFI, start, end, uid)
        val mobile = sumBytes(manager, ConnectivityManager.TYPE_MOBILE, start, end, uid)
        return NetworkSnapshot(wifiBytes = wifi, mobileBytes = mobile)
    }

    private fun sumBytes(manager: NetworkStatsManager, networkType: Int, start: Long, end: Long, uid: Int): Long =
        runCatching {
            var total = 0L
            val buckets = manager.queryDetailsForUid(networkType, null, start, end, uid)
            val bucket = android.app.usage.NetworkStats.Bucket()
            while (buckets.hasNextBucket()) {
                buckets.getNextBucket(bucket)
                total += bucket.rxBytes + bucket.txBytes
            }
            buckets.close()
            total
        }.getOrDefault(0L)
}
