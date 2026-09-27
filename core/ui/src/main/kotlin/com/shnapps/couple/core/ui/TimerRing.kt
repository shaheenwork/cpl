package com.shnapps.couple.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.NumeralTextStyle
import com.shnapps.couple.core.designsystem.theme.decorativeTween

/**
 * A clock, as a shape (DESIGN_PROMPT.md §5.7).
 *
 * "Start the 5 minutes" as a full-width outlined button was a third call to action competing
 * with Done and Skip, and once running it was a number with nothing around it. A ring shows how
 * much is left without being read, which is the point of a clock in a room where nobody wants
 * to be reading a screen.
 *
 * The last stretch pulses and warms towards ember, so it is felt rather than watched. Under
 * reduce-motion it holds still and the colour still changes — a person who has turned animation
 * off should not lose the information, only the performance.
 */
@Composable
fun TimerRing(
    remainingSeconds: Int,
    totalSeconds: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    running: Boolean = true,
) {
    val spacing = AfterhoursTheme.spacing
    val heat = AfterhoursTheme.heat
    val reduceMotion = AfterhoursTheme.reduceMotion
    val onSurface = MaterialTheme.colorScheme.onSurface

    val left = remainingSeconds.coerceAtLeast(0)
    val fraction = if (totalSeconds <= 0) 0f else (left.toFloat() / totalSeconds).coerceIn(0f, 1f)
    val ending = left in 1..ENDING_SECONDS

    val sweep by animateFloatAsStateCompat(fraction, reduceMotion)
    val color by animateColorAsState(
        targetValue = if (ending) AfterhoursTheme.colors.ember else heat.accent,
        animationSpec = decorativeTween(AfterhoursTheme.motion.slow, reduceMotion, AfterhoursEasing.Standard),
        label = "timerColor",
    )

    // The pulse is the only ambient loop on this screen, and it stops entirely when the clock
    // is not about to run out.
    val pulse = if (ending && !reduceMotion) rememberPulse() else 1f

    Column(
        modifier = modifier
            .size(RING)
            .clickable(onClick = onToggle)
            .semantics {
                contentDescription = "${left / SECONDS_IN_MINUTE} minutes ${left % SECONDS_IN_MINUTE} seconds left"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(RING)) {
                val width = STROKE.toPx()
                val inset = width / 2
                val box = Size(size.width - width, size.height - width)
                drawArc(
                    color = onSurface.copy(alpha = TRACK),
                    startAngle = START_ANGLE,
                    sweepAngle = FULL_TURN,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = box,
                    style = Stroke(width = width, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color.copy(alpha = pulse),
                    startAngle = START_ANGLE,
                    sweepAngle = FULL_TURN * sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = box,
                    style = Stroke(width = width, cap = StrokeCap.Round),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = "%d:%02d".format(left / SECONDS_IN_MINUTE, left % SECONDS_IN_MINUTE),
                    style = NumeralTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (running) "PAUSE" else "START",
                    style = MaterialTheme.typography.labelSmall,
                    color = AfterhoursTheme.colors.textFaint,
                )
            }
        }
    }
}

/** The sweep, eased. Reduce-motion snaps it, because the number is the information. */
@Composable
private fun animateFloatAsStateCompat(target: Float, reduceMotion: Boolean) =
    androidx.compose.animation.core.animateFloatAsState(
        targetValue = target,
        animationSpec = decorativeTween(AfterhoursTheme.motion.standard, reduceMotion, AfterhoursEasing.Linear),
        label = "timerSweep",
    )

@Composable
private fun rememberPulse(): Float {
    val transition = rememberInfiniteTransition(label = "timerPulse")
    val pulse by transition.animateFloat(
        initialValue = PULSE_LOW,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(AfterhoursTheme.motion.ambient / 2, easing = AfterhoursEasing.Breathing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "timerPulseValue",
    )
    return pulse
}

private val RING = 96.dp
private val STROKE = 3.dp

private const val START_ANGLE = -90f
private const val FULL_TURN = 360f
private const val TRACK = 0.10f
private const val PULSE_LOW = 0.45f
private const val ENDING_SECONDS = 10
private const val SECONDS_IN_MINUTE = 60
