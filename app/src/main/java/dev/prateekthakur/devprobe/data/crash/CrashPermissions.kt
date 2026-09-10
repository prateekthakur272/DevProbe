package dev.prateekthakur.devprobe.data.crash

import android.content.Context
import android.content.pm.PackageManager

/** Checks for the protected `android.permission.READ_LOGS`, only obtainable via `adb shell pm grant`. */
object CrashPermissions {

    fun hasReadLogsPermission(context: Context): Boolean =
        context.checkSelfPermission("android.permission.READ_LOGS") == PackageManager.PERMISSION_GRANTED

    fun grantCommand(context: Context): String =
        "adb shell pm grant ${context.packageName} android.permission.READ_LOGS"
}
