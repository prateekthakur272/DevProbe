package dev.prateekthakur.devprobe.data.crash

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/** Notification channel ids used by [CrashMonitorService], created once at app startup. */
object CrashMonitorChannels {
    const val STATUS_CHANNEL_ID = "crash_monitor_status"
    const val ALERT_CHANNEL_ID = "crash_monitor_alerts"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(STATUS_CHANNEL_ID, "Crash monitor status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Persistent notification shown while DevProbe is watching for crashes."
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL_ID, "Crash alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when DevProbe automatically detects a crash in any app."
            },
        )
    }
}
