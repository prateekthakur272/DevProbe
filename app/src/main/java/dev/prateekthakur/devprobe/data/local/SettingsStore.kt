package dev.prateekthakur.devprobe.data.local

import android.content.Context

/** Simple local preference storage — no cloud sync, no account required (§23). */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("devprobe_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_AI_ENABLED = "ai_enabled"
        private const val KEY_CRASH_MONITOR_ENABLED = "crash_monitor_enabled"
    }

    var isAiEnabled: Boolean
        get() = prefs.getBoolean(KEY_AI_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AI_ENABLED, value).apply()

    var isCrashMonitorEnabled: Boolean
        get() = prefs.getBoolean(KEY_CRASH_MONITOR_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CRASH_MONITOR_ENABLED, value).apply()
}
