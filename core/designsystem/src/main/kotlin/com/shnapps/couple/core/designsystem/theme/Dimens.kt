package com.shnapps.couple.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale. Generous by default — BUILD_PROMPT.md §15.1 asks for large cinematic
 * cards and plenty of negative space, which mostly means resisting the urge to tighten.
 */
@Immutable
data class Spacing(
    val hairline: Dp = 1.dp,
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val xxxl: Dp = 64.dp,
    /** Standard screen side gutter. */
    val gutter: Dp = 24.dp,
    /** Minimum touch target (§20). Never shrink a control below this. */
    val touchTarget: Dp = 48.dp,
)

val LocalSpacing = androidx.compose.runtime.staticCompositionLocalOf { Spacing() }

/**
 * Corner radii. Cards are softly rounded rather than sharp: sharp corners read as
 * utilitarian, and this product should feel upholstered.
 */
val AfterhoursShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Elevation, expressed as glow rather than as drop shadow. On a near-black ground a
 * Material shadow is invisible, so depth has to come from surface tint and bloom.
 */
@Immutable
data class Elevations(
    val flat: Dp = 0.dp,
    val raised: Dp = 2.dp,
    val floating: Dp = 8.dp,
    val lifted: Dp = 16.dp,
)

val LocalElevations = androidx.compose.runtime.staticCompositionLocalOf { Elevations() }
