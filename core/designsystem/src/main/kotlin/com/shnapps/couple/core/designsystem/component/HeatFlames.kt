package com.shnapps.couple.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme

/**
 * How far tonight goes, as flames: one to five of them (design/redesign, D-143).
 *
 * It replaced a row of dots — `●●●○○` — which was an abstraction to be counted. A flame is the
 * thing itself. The count carries the level, the colour carries the tier, and neither is the only
 * cue: the tier's name is always beside it, and a screen reader is told "Level 3 of 5" by whatever
 * holds this, never by the flames, which are decoration.
 *
 * [flicker] makes them a candle rather than a fire: low, organic, out of step with each other. It
 * is decorative, so it stops under reduce-motion.
 */
@Composable
fun HeatFlames(
    count: Int,
    modifier: Modifier = Modifier,
    color: Color = AfterhoursTheme.tier.accent,
    core: Color = AfterhoursTheme.colors.brass,
    height: Dp = 14.dp,
    flicker: Boolean = false,
) {
    val moving = flicker && !AfterhoursTheme.reduceMotion
    Row(
        modifier = modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(count.coerceIn(0, MAX)) { index ->
            val sway = if (moving) sway(index) else STILL
            Canvas(modifier = Modifier.size(width = height * ASPECT, height = height)) {
                val unit = size.height / VIEW_HEIGHT
                withTransform({
                    // Grows and leans from its base, the way a flame does.
                    scale(scaleX = sway.value, scaleY = 2f - sway.value, pivot = center.copy(y = size.height))
                }) {
                    scale(unit, unit, pivot = Offset.Zero) {
                        drawPath(FLAME_PATH, color = color)
                        drawPath(CORE_PATH, color = core.copy(alpha = CORE_ALPHA))
                    }
                }
            }
        }
    }
}

/** Each flame on its own clock, so five of them never move as one. */
@Composable
private fun sway(index: Int): State<Float> =
    rememberInfiniteTransition(label = "flame$index").animateFloat(
        initialValue = 1f - SWAY,
        targetValue = 1f + SWAY,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PERIODS[index % PERIODS.size], easing = AfterhoursEasing.Breathing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "flame$index",
    )

private val STILL: State<Float> = mutableFloatStateOf(1f)

/** The flame and its core, in a 12 × 16 box: the same paths the prototype draws. */
private val FLAME_PATH = PathParser()
    .parsePathString(
        "M6 .8C8.6 3.6 10.6 6.2 10.6 9.4a4.6 4.6 0 0 1-9.2 0" +
            "C1.4 7.8 2.2 6.4 3.2 5.2c.4 1 1 1.6 1.6 1.9C4.6 4.6 5 2.6 6 .8z",
    )
    .toPath()
private val CORE_PATH = PathParser()
    .parsePathString("M6 8.4c1.1 1.2 1.7 2.1 1.7 3.1a1.7 1.7 0 0 1-3.4 0c0-1 .6-1.9 1.7-3.1z")
    .toPath()

private const val VIEW_HEIGHT = 16f
private const val ASPECT = 12f / 16f
private const val MAX = 5
private const val SWAY = 0.035f
private const val CORE_ALPHA = 0.9f
private val PERIODS = intArrayOf(1_700, 1_900, 1_450, 2_100, 1_600)
