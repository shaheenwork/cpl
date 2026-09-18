package com.shnapps.couple.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Soft bloom around a surface (BUILD_PROMPT.md §15.1).
 *
 * On a near-black ground a Material drop shadow is invisible, so depth and emphasis come
 * from light instead: a halo that follows the element's rounded shape and fades out past
 * its edge.
 *
 * Built from concentric rounded rectangles of the same translucent colour. Where they
 * overlap, near the edge, the colour accumulates; further out only the outer layers
 * remain. That approximates a blur on every API level — `Modifier.blur` needs API 31.
 *
 * History worth keeping, because both mistakes looked plausible in code:
 *  - A radial gradient to `Color.Transparent` dragged the hue towards black as well as
 *    the alpha, painting a dirty halo.
 *  - A radial gradient clipped to the element's bounds, with an opaque rounded card on
 *    top, was visible *only* in the four corners outside the rounding: a faint rectangle,
 *    not a glow. Screenshot review caught it in Phase 4.
 *
 * [cornerRadius] should match the element's own shape so the halo hugs it.
 */
fun Modifier.glow(
    color: Color,
    alpha: Float = 1f,
    spread: Dp = 20.dp,
    cornerRadius: Dp = 24.dp,
): Modifier = drawBehind {
    val layerColor = color.copy(alpha = color.alpha * alpha * LAYER_ALPHA)
    if (layerColor.alpha <= 0f) return@drawBehind

    val spreadPx = spread.toPx()
    val baseRadius = cornerRadius.toPx()
    for (layer in LAYERS downTo 1) {
        val outset = spreadPx * layer / LAYERS
        drawRoundRect(
            color = layerColor,
            topLeft = Offset(-outset, -outset),
            size = Size(size.width + outset * 2, size.height + outset * 2),
            cornerRadius = CornerRadius(baseRadius + outset),
        )
    }
}

private const val LAYERS = 10

/**
 * Per-layer opacity. Ten layers stacked near the edge accumulate to roughly a third of the
 * glow colour's own alpha, falling to a tenth of that at the outer ring.
 */
private const val LAYER_ALPHA = 0.18f
