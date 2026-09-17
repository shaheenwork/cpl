package com.shnapps.couple.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Soft bloom behind a surface (BUILD_PROMPT.md §15.1).
 *
 * On a near-black ground a Material drop shadow is invisible, so depth and emphasis have
 * to come from light instead. This paints a radial wash behind the content, which is what
 * makes a revealed card look lit from within rather than merely outlined.
 *
 * Two details that are easy to get wrong, and were:
 *
 * **The far stop fades [color] to zero alpha rather than using [Color.Transparent].**
 * `Color.Transparent` is RGBA(0,0,0,0), so a gradient towards it interpolates its *colour*
 * towards black as well as its alpha. On a dark theme that reads as a dirty black halo
 * instead of a fade. Holding the hue constant and moving only alpha is the fix.
 *
 * **It clips to its own bounds.** `drawBehind` does not clip, so a radius larger than the
 * element happily paints over whatever sits next to it in a column.
 */
fun Modifier.glow(
    color: Color,
    radiusScale: Float = 1.1f,
    alpha: Float = 1f,
): Modifier = clipToBounds().drawBehind {
    if (alpha <= 0f) return@drawBehind

    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = maxOf(size.width, size.height) * radiusScale

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = color.alpha * alpha),
                color.copy(alpha = 0f),
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
