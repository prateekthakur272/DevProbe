package dev.prateekthakur.devprobe.presentation.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/** Opens a PDF content [uri] in the device's default PDF viewer, where the user can share/print/etc. from there. */
fun Context.openPdf(uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { startActivity(intent) }
        .onFailure { Toast.makeText(this, "Report saved to Downloads, but no app can open PDFs.", Toast.LENGTH_LONG).show() }
}

data class PdfExportAction(val isExporting: Boolean, val export: () -> Unit)

/**
 * Wraps a suspend PDF-export call (which saves into the public Downloads folder) with the
 * pre-Android-10 WRITE_EXTERNAL_STORAGE permission request — not needed on API 29+, where
 * `MediaStore.Downloads` requires no permission — and opens the result once saved.
 */
@Composable
fun rememberPdfExportAction(export: suspend () -> Uri?): PdfExportAction {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }

    fun runExport() {
        isExporting = true
        scope.launch {
            val uri = export()
            isExporting = false
            uri?.let { context.openPdf(it) }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runExport()
    }

    return PdfExportAction(
        isExporting = isExporting,
        export = {
            val needsLegacyPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            if (needsLegacyPermission) {
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                runExport()
            }
        },
    )
}
