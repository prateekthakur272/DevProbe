package dev.prateekthakur.devprobe.data.apk

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Base64
import dev.prateekthakur.devprobe.domain.model.ApkMetadata
import dev.prateekthakur.devprobe.domain.model.PermissionCategory
import dev.prateekthakur.devprobe.domain.model.PermissionInfo
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

class ApkMetadataExtractor(private val context: Context) {

    private val knownAbiDirs = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64", "armeabi", "mips", "mips64")

    @Suppress("DEPRECATION")
    fun extractMetadata(apkFile: File): ApkMetadata {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
        val packageInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: throw IllegalStateException("This does not look like a valid APK — PackageManager could not parse it.")

        val appInfo = packageInfo.applicationInfo
        appInfo?.sourceDir = apkFile.absolutePath
        appInfo?.publicSourceDir = apkFile.absolutePath

        val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: packageInfo.packageName
        val versionCode = packageInfo.longVersionCode
        val debuggable = (appInfo?.flags ?: 0) and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val minSdk = appInfo?.minSdkVersion ?: 0

        val icon: String? = try {
            appInfo?.let { pm.getApplicationIcon(it) }?.let { drawableToBase64(it) }
        } catch (_: Exception) {
            null
        }

        val abis = ZipFile(apkFile).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.startsWith("lib/") && !it.isDirectory }
                .mapNotNull { it.name.split("/").getOrNull(1) }
                .filter { it in knownAbiDirs }
                .toSet()
                .sorted()
        }

        return ApkMetadata(
            appLabel = label,
            packageName = packageInfo.packageName,
            versionName = packageInfo.versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = appInfo?.targetSdkVersion ?: 0,
            compileSdk = null, // Not derivable from PackageManager for archived APKs; see ManifestInfo.compileSdkVersion.
            apkSizeBytes = apkFile.length(),
            debuggable = debuggable,
            nativeArchitectures = abis,
            iconBase64 = icon,
        )
    }

    @Suppress("DEPRECATION")
    fun extractPermissions(apkFile: File): List<PermissionInfo> {
        val pm = context.packageManager
        val packageInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_PERMISSIONS)
            ?: return emptyList()
        val names = packageInfo.requestedPermissions ?: emptyArray()
        return names.map { name ->
            PermissionInfo(
                name = name,
                category = PermissionCatalog.categoryOf(name),
                description = PermissionCatalog.describe(name),
                risk = PermissionCatalog.riskOf(name),
            )
        }.sortedWith(compareBy({ it.category != PermissionCategory.DANGEROUS }, { it.name }))
    }

    private fun drawableToBase64(drawable: Drawable): String {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
