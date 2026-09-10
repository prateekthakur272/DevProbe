package dev.prateekthakur.devprobe.data.apk

import dev.prateekthakur.devprobe.domain.model.PermissionCategory
import dev.prateekthakur.devprobe.domain.model.RiskLevel

/**
 * Static knowledge of well-known Android permissions. This intentionally does NOT
 * call an LLM — permission semantics are documented facts, not something to infer.
 */
object PermissionCatalog {

    private data class Entry(val category: PermissionCategory, val description: String, val risk: RiskLevel)

    private val known: Map<String, Entry> = mapOf(
        "android.permission.CAMERA" to Entry(PermissionCategory.DANGEROUS, "Allows the application to access the camera.", RiskLevel.MEDIUM),
        "android.permission.RECORD_AUDIO" to Entry(PermissionCategory.DANGEROUS, "Allows the application to record audio.", RiskLevel.MEDIUM),
        "android.permission.ACCESS_FINE_LOCATION" to Entry(PermissionCategory.DANGEROUS, "Allows access to precise device location.", RiskLevel.HIGH),
        "android.permission.ACCESS_COARSE_LOCATION" to Entry(PermissionCategory.DANGEROUS, "Allows access to approximate device location.", RiskLevel.MEDIUM),
        "android.permission.ACCESS_BACKGROUND_LOCATION" to Entry(PermissionCategory.DANGEROUS, "Allows access to location while the app runs in the background.", RiskLevel.HIGH),
        "android.permission.READ_CONTACTS" to Entry(PermissionCategory.DANGEROUS, "Allows reading the user's contacts.", RiskLevel.HIGH),
        "android.permission.WRITE_CONTACTS" to Entry(PermissionCategory.DANGEROUS, "Allows modifying the user's contacts.", RiskLevel.MEDIUM),
        "android.permission.READ_SMS" to Entry(PermissionCategory.DANGEROUS, "Allows reading SMS messages.", RiskLevel.HIGH),
        "android.permission.SEND_SMS" to Entry(PermissionCategory.DANGEROUS, "Allows sending SMS messages, which may incur charges.", RiskLevel.HIGH),
        "android.permission.READ_CALL_LOG" to Entry(PermissionCategory.DANGEROUS, "Allows reading the device's call log.", RiskLevel.HIGH),
        "android.permission.READ_PHONE_STATE" to Entry(PermissionCategory.DANGEROUS, "Allows reading phone state and identifiers.", RiskLevel.MEDIUM),
        "android.permission.CALL_PHONE" to Entry(PermissionCategory.DANGEROUS, "Allows initiating phone calls without user confirmation.", RiskLevel.HIGH),
        "android.permission.READ_EXTERNAL_STORAGE" to Entry(PermissionCategory.DANGEROUS, "Allows reading files from shared/external storage.", RiskLevel.MEDIUM),
        "android.permission.WRITE_EXTERNAL_STORAGE" to Entry(PermissionCategory.DANGEROUS, "Allows writing files to shared/external storage.", RiskLevel.MEDIUM),
        "android.permission.BODY_SENSORS" to Entry(PermissionCategory.DANGEROUS, "Allows access to body sensor data (e.g. heart rate).", RiskLevel.MEDIUM),
        "android.permission.ACTIVITY_RECOGNITION" to Entry(PermissionCategory.DANGEROUS, "Allows access to physical activity recognition.", RiskLevel.LOW),
        "android.permission.POST_NOTIFICATIONS" to Entry(PermissionCategory.DANGEROUS, "Allows posting notifications to the user.", RiskLevel.LOW),
        "android.permission.INTERNET" to Entry(PermissionCategory.NORMAL, "Allows the application to open network sockets.", RiskLevel.LOW),
        "android.permission.ACCESS_NETWORK_STATE" to Entry(PermissionCategory.NORMAL, "Allows reading network connectivity state.", RiskLevel.NONE),
        "android.permission.ACCESS_WIFI_STATE" to Entry(PermissionCategory.NORMAL, "Allows reading Wi-Fi connectivity state.", RiskLevel.NONE),
        "android.permission.BLUETOOTH" to Entry(PermissionCategory.NORMAL, "Allows connecting to paired Bluetooth devices.", RiskLevel.LOW),
        "android.permission.VIBRATE" to Entry(PermissionCategory.NORMAL, "Allows control of the vibrator.", RiskLevel.NONE),
        "android.permission.WAKE_LOCK" to Entry(PermissionCategory.NORMAL, "Allows preventing the device from sleeping.", RiskLevel.LOW),
        "android.permission.RECEIVE_BOOT_COMPLETED" to Entry(PermissionCategory.NORMAL, "Allows the app to start automatically after boot.", RiskLevel.LOW),
        "android.permission.FOREGROUND_SERVICE" to Entry(PermissionCategory.NORMAL, "Allows running a foreground service.", RiskLevel.LOW),
        "android.permission.SYSTEM_ALERT_WINDOW" to Entry(PermissionCategory.SPECIAL, "Allows drawing overlays on top of other apps.", RiskLevel.HIGH),
        "android.permission.WRITE_SETTINGS" to Entry(PermissionCategory.SPECIAL, "Allows modifying system settings.", RiskLevel.HIGH),
        "android.permission.MANAGE_EXTERNAL_STORAGE" to Entry(PermissionCategory.SPECIAL, "Allows broad access to shared storage, bypassing scoped storage.", RiskLevel.HIGH),
        "android.permission.REQUEST_INSTALL_PACKAGES" to Entry(PermissionCategory.SPECIAL, "Allows the app to request installation of other packages.", RiskLevel.HIGH),
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to Entry(PermissionCategory.SIGNATURE, "Allows binding to an accessibility service.", RiskLevel.HIGH),
        "android.permission.BIND_DEVICE_ADMIN" to Entry(PermissionCategory.SIGNATURE, "Allows binding to a device admin receiver.", RiskLevel.HIGH),
    )

    fun categoryOf(name: String): PermissionCategory = known[name]?.category ?: when {
        name.startsWith("android.permission.") -> PermissionCategory.UNKNOWN
        else -> PermissionCategory.UNKNOWN
    }

    fun describe(name: String): String = known[name]?.description
        ?: "No local description available for this permission."

    fun riskOf(name: String): RiskLevel = known[name]?.risk ?: RiskLevel.NONE

    fun isSensitive(name: String): Boolean {
        val entry = known[name] ?: return false
        return entry.category == PermissionCategory.DANGEROUS || entry.risk == RiskLevel.HIGH
    }
}
