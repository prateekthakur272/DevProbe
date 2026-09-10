package dev.prateekthakur.devprobe.presentation.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Thin accessors over the real Material 3 Expressive [androidx.compose.material3.MotionScheme]
 * (`DevProbeTheme` installs `MotionScheme.expressive()` — see Theme.kt), so call sites read the
 * same as before this shipped stable: bouncier, spring-driven "spatial" motion for
 * position/size/scale changes, and snappier "effects" motion for color/opacity fades.
 */
object ExpressiveMotion {
    @Composable
    fun <T> spatialSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultSpatialSpec()

    @Composable
    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastSpatialSpec()

    @Composable
    fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.slowSpatialSpec()

    @Composable
    fun <T> effectsSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultEffectsSpec()

    @Composable
    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastEffectsSpec()
}
