package dev.prateekthakur.devprobe.data.crash

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.prateekthakur.devprobe.DevProbeApplication
import dev.prateekthakur.devprobe.MainActivity
import dev.prateekthakur.devprobe.R
import dev.prateekthakur.devprobe.domain.model.SessionSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that tails the full system logcat (requires the one-time
 * `adb shell pm grant ... android.permission.READ_LOGS` — see [hasReadLogsPermission])
 * and auto-saves any detected crash as an [SessionSource.AUTO_DETECTED] session.
 */
class CrashMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val detector = LiveCrashDetector()
    private var process: Process? = null
    private var detectedCount = 0

    private val container get() = (application as DevProbeApplication).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildStatusNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(STATUS_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(STATUS_NOTIFICATION_ID, notification)
        }
        startTailing()
        return START_STICKY
    }

    private fun startTailing() {
        if (process != null) return
        scope.launch {
            try {
                val proc = ProcessBuilder("logcat", "-v", "threadtime")
                    .redirectErrorStream(true)
                    .start()
                process = proc
                proc.inputStream.bufferedReader().use { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        detector.feed(line)?.let { onCrashDetected(it) }
                    }
                }
            } catch (_: Exception) {
                stopSelf()
            }
        }
    }

    private fun onCrashDetected(captured: CapturedCrash) {
        detectedCount++
        scope.launch {
            container.analysisRepository.saveLogAnalysis(
                title = "${captured.crashReport.exceptionType.substringAfterLast('.')} in ${captured.packageName ?: "unknown app"}",
                result = captured.result,
                createdAt = System.currentTimeMillis(),
                packageName = captured.packageName,
                source = SessionSource.AUTO_DETECTED,
            )
        }
        updateStatusNotification()
        postCrashAlert(captured)
    }

    override fun onDestroy() {
        process?.destroy()
        scope.cancel()
        super.onDestroy()
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun buildStatusNotification(): Notification =
        NotificationCompat.Builder(this, CrashMonitorChannels.STATUS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("DevProbe is watching for crashes")
            .setContentText(if (detectedCount == 0) "No crashes detected this session" else "$detectedCount crash(es) detected this session")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent())
            .build()

    private fun updateStatusNotification() {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        runCatching { manager.notify(STATUS_NOTIFICATION_ID, buildStatusNotification()) }
    }

    private fun postCrashAlert(captured: CapturedCrash) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(this, CrashMonitorChannels.ALERT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Crash detected in ${captured.packageName ?: "an app"}")
            .setContentText(captured.crashReport.exceptionType.substringAfterLast('.') + ": " + (captured.crashReport.message ?: ""))
            .setStyle(NotificationCompat.BigTextStyle().bigText(captured.crashReport.message ?: captured.crashReport.exceptionType))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent())
            .build()
        runCatching { manager.notify(ALERT_NOTIFICATION_ID_BASE + detectedCount, notification) }
    }

    companion object {
        private const val STATUS_NOTIFICATION_ID = 1001
        private const val ALERT_NOTIFICATION_ID_BASE = 2000

        fun start(context: Context) {
            context.startService(Intent(context, CrashMonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CrashMonitorService::class.java))
        }
    }
}
