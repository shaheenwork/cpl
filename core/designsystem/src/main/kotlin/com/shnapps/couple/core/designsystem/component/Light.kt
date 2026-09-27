package com.shnapps.couple.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme

/**
 * Light, properly (DESIGN_PROMPT.md §4.3).
 *
 * The halo this replaces drew **ten** concentric rounded rectangles of the same
 * translucent colour. At button size it passed; at card size the layers banded into a
 * hard-edged pink rectangle with a visible step, and a row of three of them — the heat
 * picker — smeared into one continuous band across the bottom of the screen. It read as a
 * rendering bug, because it was one.
 *
 * Three primitives instead, each with one job:
 *
 *  - [rim] — a surface's edge, brighter along the top. This is most of what makes an
 *    object look lit rather than outlined, and it costs one modifier.
 *  - [bloom] — light around one element.
 *  - [contactShadow] — weight under a card. Nearly invisible as a shadow on near-black,
 *    which is exactly right: it should read as mass, not as a drop shadow.
 *
 * Two rules keep this from going wrong again:
 *
 *  1. **At most one bloomed element per screen**, and never one inside a row of siblings.
 *  2. **The alpha is capped.** Past [MAX_BLOOM] at the brightest ring, a bloom stops reading
 *     as light and starts reading as a sticker.
 *
 * `Modifier.blur` is deliberately not used, even though API 31 has it. It blurs a node's
 * own content rather than painting a halo behind it, it is a no-op below 31, and a
 * primitive that renders differently on different API levels would make every Roborazzi
 * golden in the repo unstable. Three wide, low layers look like light everywhere.
 */
@Composable
fun Modifier.rim(
    shape: Shape,
    top: Color = AfterhoursTheme.colors.rimTop,
    bottom: Color = AfterhoursTheme.colors.rimBottom,
): Modifier = border(
    width = AfterhoursTheme.spacing.hairline,
    brush = Brush.verticalGradient(listOf(top, bottom)),
    shape = shape,
)

/**
 * A raised surface: fill, rim, and — from [Elevations.floating] up — the weight under it.
 *
 * One call, so the three never drift apart and no screen invents its own combination of
 * them.
 */
@Composable
fun Modifier.raised(
    color: Color,
    shape: Shape,
    elevation: Dp = AfterhoursTheme.elevations.raised,
    rimTop: Color = AfterhoursTheme.colors.rimTop,
): Modifier {
    val base = if (elevation >= AfterhoursTheme.elevations.floating) {
        contactShadow(shape = shape, depth = AfterhoursTheme.elevations.contact)
    } else {
        this
    }
    return base
        .background(color = color, shape = shape)
        .rim(shape = shape, top = rimTop)
}

/**
 * Light around one element.
 *
 * Widening rounded rectangles, and the thing that decides whether they read as light or as
 * a stack of rectangles is **the falloff, not the count**. Equal alpha per layer is what
 * bands: every ring adds the same step, so the eye finds every edge. Here each ring's alpha
 * falls with the square of its distance, so the near ones carry almost all the light and
 * the far ones fade to nothing — which is how light actually behaves.
 *
 * The corner radius grows with the outset so the halo hugs the shape instead of squaring
 * off at the corners — the mistake screenshot review caught in Phase 4, worth not repeating.
 */
fun Modifier.bloom(
    color: Color,
    cornerRadius: Dp = 28.dp,
    spread: Dp = 30.dp,
    alpha: Float = 1f,
): Modifier {
    val strength = (color.alpha * alpha * MAX_BLOOM).coerceIn(0f, MAX_BLOOM)
    if (strength <= 0f) return this
    return drawBehind {
        val reach = spread.toPx()
        val corner = cornerRadius.toPx()
        for (layer in BLOOM_LAYERS downTo 1) {
            val distance = layer.toFloat() / BLOOM_LAYERS
            val outset = reach * distance
            val falloff = (1f - distance) * (1f - distance)
            drawRoundRect(
                // Divided by the sum of the whole series, so what accumulates at the brightest
                // ring is [strength] and not twelve times it. Layers stack; their alphas add.
                color = color.copy(alpha = strength * falloff / FALLOFF_TOTAL),
                topLeft = Offset(-outset, -outset),
                size = Size(size.width + outset * 2, size.height + outset * 2),
                cornerRadius = CornerRadius(corner + outset),
            )
        }
    }
}

/**
 * Weight under a card.
 *
 * A Material drop shadow is invisible on near-black, so this is a soft dark pool offset
 * downwards. It is not meant to be noticed as a shadow; it is meant to make the card feel
 * like it has mass.
 */
fun Modifier.contactShadow(shape: Shape, depth: Dp): Modifier = drawBehind {
    val fall = depth.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    val pool = Color.Black.copy(alpha = SHADOW_ALPHA / SHADOW_LAYERS)
    for (layer in SHADOW_LAYERS downTo 1) {
        translate(top = fall * layer / SHADOW_LAYERS * SHADOW_DROP) {
            when (outline) {
                is Outline.Rounded -> drawPath(Path().apply { addRoundRect(outline.roundRect) }, pool)
                is Outline.Generic -> drawPath(outline.path, pool)
                is Outline.Rectangle -> drawRect(pool)
            }
        }
    }
}

/** Past this a bloom stops reading as light and starts reading as a sticker. */
private const val MAX_BLOOM = 0.34f
private const val BLOOM_LAYERS = 12

/**
 * What the twelve layers' falloff adds up to.
 *
 * Alphas accumulate where the layers overlap, so without dividing by this the brightest ring
 * would reach roughly three and a half times the intended strength — which is exactly the
 * solid pink band the first attempt produced.
 */
private val FALLOFF_TOTAL = (1..BLOOM_LAYERS).sumOf { layer ->
    val distance = layer.toDouble() / BLOOM_LAYERS
    (1.0 - distance) * (1.0 - distance)
}.toFloat()
private const val SHADOW_LAYERS = 3
private const val SHADOW_ALPHA = 0.45f
private const val SHADOW_DROP = 0.35f
