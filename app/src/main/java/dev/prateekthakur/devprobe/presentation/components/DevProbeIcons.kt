package dev.prateekthakur.devprobe.presentation.components

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom vector icons not present in the bundled material-icons-core set (we
 * deliberately avoid pulling in material-icons-extended for one glyph). Tinted
 * the same way as every other Icon() in the app — the hardcoded fill color below
 * is irrelevant since Icon() overrides it via its `tint` parameter.
 */
private var _folderIcon: ImageVector? = null

val FolderIcon: ImageVector
    get() = _folderIcon ?: ImageVector.Builder(
        name = "Folder",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(10f, 4f)
        lineTo(4f, 4f)
        curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
        lineTo(2f, 18f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(16f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineTo(8f)
        curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
        horizontalLineToRelative(-8f)
        lineToRelative(-2f, -2f)
        close()
    }.build().also { _folderIcon = it }
