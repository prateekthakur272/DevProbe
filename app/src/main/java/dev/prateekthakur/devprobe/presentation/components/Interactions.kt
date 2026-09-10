package dev.prateekthakur.devprobe.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import dev.prateekthakur.devprobe.presentation.theme.ExpressiveMotion

/**
 * Replaces a plain `Modifier.clickable` with one that also springs the element down
 * slightly on press (Material 3 Expressive-style motion — see ExpressiveMotion.kt).
 */
@Composable
fun Modifier.pressScale(pressedScale: Float = 0.95f, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = ExpressiveMotion.fastSpatialSpec(),
        label = "pressScale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
}
