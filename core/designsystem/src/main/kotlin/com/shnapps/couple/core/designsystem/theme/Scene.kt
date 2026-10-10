package com.shnapps.couple.core.designsystem.theme

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.max

/**
 * The scene (DESIGN_PROMPT.md §4.1, D-145).
 *
 * The background of this app is not a colour. A dim room has a *source* of light, things that
 * catch it, texture, and shadow where the light does not reach. Composed once, under everything:
 * ```
 * base  +  key light  +  two wine bounces  +  vignette  +  silk  +  grain
 * ```
 *
 * The key is overhead, and it is **tonight's heat**: lavender at Mild, scarlet at Hot, so the room
 * warms as the evening does. A screen says which heat that is with [LightTheRoom] — the scaffold
 * does it with the tier the screen provides — and with none, or after Stop, the light is calm:
 * blush, no heat in it at all. The bounces are wine, low on the
 * screen. The silk is a faint diagonal sheen that makes the dark read as fabric rather than paint,
 * and the grain is what stops the whole thing looking like flat vector art.
 *
 * Every gradient fades to `color.copy(alpha = 0f)` rather than to `Color.Transparent`, which is
 * RGBA(0,0,0,0) and would drag the hue towards black as well as the alpha — the dirty-halo bug
 * this repo has already paid for once.
 */
@Composable
fun Modifier.scene(): Modifier {
    val colors = AfterhoursTheme.colors
    val grain = rememberGrain(colors.grain)
    val light = LocalSceneLight.current
    val heat = light?.color
    // Read in the draw phase, not here: the light can take a second to change, and a frame of it
    // should be a redraw, not a recomposition of everything on the screen.
    val key = animateColorAsState(
        targetValue = heat?.copy(alpha = KEY_ALPHA) ?: colors.sceneCalm,
        animationSpec = tween(
            durationMillis = AfterhoursTheme.motion.bloom.orInstantIfReduced(AfterhoursTheme.reduceMotion),
            easing = AfterhoursEasing.Standard,
        ),
        label = "sceneKey",
    )
    return this.drawBehind {
        val long = max(size.width, size.height)

        // The key: overhead, an ellipse wider than the screen, in tonight's heat.
        ellipse(
            color = key.value,
            center = Offset(size.width / 2f, size.height * KEY_Y),
            radiusX = size.width * KEY_RADIUS_X,
            radiusY = size.height * KEY_RADIUS_Y,
        )
        // The two bounces, low and warm.
        ellipse(
            color = colors.sceneKey,
            center = Offset(0f, size.height),
            radiusX = size.width * BOUNCE_LEFT_X,
            radiusY = size.height * BOUNCE_LEFT_Y,
        )
        ellipse(
            color = colors.sceneBounce,
            center = Offset(size.width, size.height * BOUNCE_RIGHT_AT),
            radiusX = size.width * BOUNCE_RIGHT_X,
            radiusY = size.height * BOUNCE_RIGHT_Y,
        )
        // The fall-off. Transparent at the middle, darkest in the corners.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(colors.sceneVignette.copy(alpha = 0f), colors.sceneVignette),
                center = Offset(size.width / 2f, size.height * VIGNETTE_Y),
                radius = long * VIGNETTE_RADIUS,
            ),
        )
        silk(colors.sceneSheen)
        drawRect(brush = grain)
    }
}

/**
 * Lights the room in [heat] — a tier's accent — for as long as this is composed; `null` is the
 * calm light.
 *
 * Called once per screen, by the screen's scaffold, with the heat the screen is playing at. The
 * newest caller wins and the light falls back as callers leave, so a screen sliding in over
 * another takes the room with it and gives it back on the way out.
 */
@Composable
fun LightTheRoom(heat: Color?) {
    val light = LocalSceneLight.current ?: return
    DisposableEffect(light, heat) {
        val claim = SceneClaim(heat)
        light.claims += claim
        onDispose { light.claims -= claim }
    }
}

/** Who has the light: the screens that asked for it, newest last. */
@Stable
internal class SceneLight {
    val claims = mutableStateListOf<SceneClaim>()
    val color: Color? get() = claims.lastOrNull()?.color
}

/** One screen's claim on the light. Compared by identity, so two at the same heat are two claims. */
internal class SceneClaim(val color: Color?)

internal val LocalSceneLight = staticCompositionLocalOf<SceneLight?> { null }

@Composable
internal fun rememberSceneLight(): SceneLight = remember { SceneLight() }

/** A radial gradient stretched into an ellipse: CSS's `radial-gradient(rx ry at x y, …)`. */
private fun DrawScope.ellipse(color: Color, center: Offset, radiusX: Float, radiusY: Float) {
    scale(scaleX = 1f, scaleY = radiusY / radiusX, pivot = center) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color, color.copy(alpha = 0f)),
                center = center,
                radius = radiusX,
            ),
            radius = radiusX,
            center = center,
        )
    }
}

/** Two diagonal bands of sheen, the second fainter, at the prototype's angles. */
private fun DrawScope.silk(sheen: Color) {
    val clear = sheen.copy(alpha = 0f)
    val far = Offset(size.width, size.height)
    drawRect(
        brush = Brush.linearGradient(
            SILK_START to clear,
            SILK_PEAK to sheen,
            SILK_END to clear,
            start = Offset.Zero,
            end = far,
        ),
    )
    drawRect(
        brush = Brush.linearGradient(
            SILK2_START to clear,
            SILK2_PEAK to sheen.copy(alpha = sheen.alpha * SILK2_STRENGTH),
            SILK2_END to clear,
            start = Offset(0f, size.height * SILK2_TILT),
            end = far,
        ),
    )
}

/**
 * The grain: one tile, generated once, repeated over everything.
 *
 * Deterministic on purpose — a seeded generator rather than `Random()` — so a screenshot golden
 * does not change between runs. Without that every Roborazzi comparison in the repo would fail on
 * noise, which is a very expensive way to learn this lesson.
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

// --- the key, the bounces and the fall-off, as fractions of the surface ------------------
// The prototype's `radial-gradient(110% 60% at 50% -8%, … 62%)`: the radii are where it reaches
// zero, which is the ellipse's size times the last stop.
private const val KEY_ALPHA = 0.20f
private const val KEY_Y = -0.08f
private const val KEY_RADIUS_X = 0.68f
private const val KEY_RADIUS_Y = 0.37f
private const val BOUNCE_LEFT_X = 0.56f
private const val BOUNCE_LEFT_Y = 0.32f
private const val BOUNCE_RIGHT_AT = 0.85f
private const val BOUNCE_RIGHT_X = 0.49f
private const val BOUNCE_RIGHT_Y = 0.28f
private const val VIGNETTE_Y = 0.45f
private const val VIGNETTE_RADIUS = 0.80f

// --- the silk ------------------------------------------------------------------------------
private const val SILK_START = 0.30f
private const val SILK_PEAK = 0.45f
private const val SILK_END = 0.60f
private const val SILK2_START = 0.55f
private const val SILK2_PEAK = 0.68f
private const val SILK2_END = 0.80f
private const val SILK2_STRENGTH = 0.7f
private const val SILK2_TILT = 0.15f

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
