package com.shnapps.couple.core.designsystem.theme

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max

/**
 * The scene (DESIGN_PROMPT.md §4.1).
 *
 * The background of this app is not a colour. A dark app is a light app with its colours
 * inverted — flat panels on a flat ground. A dim room has a *source* of light, things that
 * catch it, texture, and shadow where the light does not reach.
 *
 * Composed once, under everything:
 * ```
 * base ink  +  key wash  +  bounce wash  +  vignette  +  grain
 * ```
 *
 * The key is a warm wash off the top-left corner — the candle. The bounce is plum off the
 * bottom-right, which is the first real job plum has had in this palette and the reason
 * the app stops reading as one pink on black. The vignette is the fall-off. The grain is
 * what stops the whole thing looking like flat vector art.
 *
 * Every gradient fades to `color.copy(alpha = 0f)` rather than to `Color.Transparent`,
 * which is RGBA(0,0,0,0) and would drag the hue towards black as well as the alpha — the
 * dirty-halo bug this repo has already paid for once.
 */
@Composable
fun Modifier.scene(): Modifier {
    val colors = AfterhoursTheme.colors
    val grain = rememberGrain(colors.grain)
    return this.drawBehind {
        val long = max(size.width, size.height)

        // The candle: off-screen, top-left, falling off fast.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(colors.sceneKey, colors.sceneKey.copy(alpha = 0f)),
                center = Offset(x = size.width * KEY_X, y = size.height * KEY_Y),
                radius = long * KEY_RADIUS,
            ),
        )
        // The bounce, off the opposite corner.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(colors.sceneBounce, colors.sceneBounce.copy(alpha = 0f)),
                center = Offset(x = size.width * BOUNCE_X, y = size.height * BOUNCE_Y),
                radius = long * BOUNCE_RADIUS,
            ),
        )
        // The fall-off. Transparent at the middle, darkest in the corners.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(colors.sceneVignette.copy(alpha = 0f), colors.sceneVignette),
                center = Offset(size.width / 2f, size.height * VIGNETTE_Y),
                radius = long * VIGNETTE_RADIUS,
            ),
        )
        drawRect(brush = grain)
    }
}

/**
 * The grain: one tile, generated once, repeated over everything.
 *
 * Deterministic on purpose — a seeded generator rather than `Random()` — so a screenshot
 * golden does not change between runs. Without that every Roborazzi comparison in the
 * repo would fail on noise, which is a very expensive way to learn this lesson.
 */
@Composable
private fun rememberGrain(color: Color): Brush = remember(color) {
    ShaderBrush(
        ImageShader(
            image = grainTile(color),
            tileModeX = TileMode.Repeated,
            tileModeY = TileMode.Repeated,
        ),
    )
}

private fun grainTile(color: Color): ImageBitmap {
    val bitmap = Bitmap.createBitmap(GRAIN_TILE, GRAIN_TILE, Bitmap.Config.ARGB_8888)
    val red = (color.red * BYTE).toInt()
    val green = (color.green * BYTE).toInt()
    val blue = (color.blue * BYTE).toInt()
    // A tiny linear congruential generator. Nothing here needs to be a good random number,
    // only the same one every time.
    var state = GRAIN_SEED
    val pixels = IntArray(GRAIN_TILE * GRAIN_TILE)
    for (index in pixels.indices) {
        state = state * LCG_MULTIPLIER + LCG_INCREMENT
        val speck = ((state ushr LCG_DISCARD) and BYTE.toLong()).toInt()
        val alpha = (speck * color.alpha).toInt().coerceIn(0, BYTE)
        pixels[index] = (alpha shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
    }
    bitmap.setPixels(pixels, 0, GRAIN_TILE, 0, 0, GRAIN_TILE, GRAIN_TILE)
    return bitmap.asImageBitmap()
}

// --- the two lights and the fall-off, as fractions of the surface ------------------------
private const val KEY_X = -0.10f
private const val KEY_Y = -0.15f
private const val KEY_RADIUS = 1.20f
private const val BOUNCE_X = 1.10f
private const val BOUNCE_Y = 1.05f
private const val BOUNCE_RADIUS = 0.95f
private const val VIGNETTE_Y = 0.45f
private const val VIGNETTE_RADIUS = 0.80f

// --- the grain -----------------------------------------------------------------------------
private const val GRAIN_TILE = 128
private const val GRAIN_SEED = 0x5DEECE66DL
private const val LCG_MULTIPLIER = 6364136223846793005L
private const val LCG_INCREMENT = 1442695040888963407L
private const val LCG_DISCARD = 33
private const val BYTE = 0xFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
