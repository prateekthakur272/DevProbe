package dev.prateekthakur.devprobe.data.profiling

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dev.prateekthakur.devprobe.domain.model.ProfilingTimeRange

data class UsageSnapshot(val foregroundTimeMs: Long, val lastUsedEpochMillis: Long, val launchCount: Int)

/** Wraps [UsageStatsManager] — requires the user-granted "Usage access" special app op. */
class UsageStatsAnalyzer(context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    fun snapshotByPackage(range: ProfilingTimeRange): Map<String, UsageSnapshot> {
        val manager = usageStatsManager ?: return emptyMap()
        val end = System.currentTimeMillis()
        val start = end - range.days * 24L * 60 * 60 * 1000

        val foregroundTime = runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
                .groupBy { it.packageName }
                .mapValues { (_, stats) -> stats.sumOf { it.totalTimeInForeground } to (stats.maxOfOrNull { it.lastTimeUsed } ?: 0L) }
        }.getOrDefault(emptyMap())

        val launchCounts = runCatching { countLaunches(manager, start, end) }.getOrDefault(emptyMap())

        return foregroundTime.mapValues { (pkg, value) ->
            UsageSnapshot(
                foregroundTimeMs = value.first,
                lastUsedEpochMillis = value.second,
                launchCount = launchCounts[pkg] ?: 0,
            )
        }
    }

    private fun countLaunches(manager: UsageStatsManager, start: Long, end: Long): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        val events = manager.queryEvents(start, end)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                counts[event.packageName] = (counts[event.packageName] ?: 0) + 1
            }
        }
        return counts
    }
}
