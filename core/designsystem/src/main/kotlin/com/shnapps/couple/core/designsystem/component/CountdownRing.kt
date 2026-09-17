package com.shnapps.couple.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.DisplayFontFamily
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.designsystem.theme.decorativeTween

/**
 * A ring counting down (BUILD_PROMPT.md §40, §28): until a scheduled experience starts,
 * until a chapter unlocks, until we meet again.
 *
 * The numerals are set in the display serif, because §40 wants the countdown to read as
 * typography rather than as a widget.
 *
 * [progress] is how much time remains, from 1 down to 0. The caller owns the clock —
 * keeping time in a design-system component would make it untestable and would tie a
 * visual to a scheduler.
 */
@Composable
fun CountdownRing(
    progress: Float,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
) {
    val colors = AfterhoursTheme.colors
    val spacing = AfterhoursTheme.spacing

    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = decorativeTween(
            durationMillis = AfterhoursTheme.motion.slow,
            reduceMotion = AfterhoursTheme.reduceMotion,
            easing = AfterhoursEasing.Standard,
        ),
        label = "countdownProgress",
    )

    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val arcColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "$value $unit remaining" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)

            drawArc(
                color = trackColor,
                startAngle = START_ANGLE,
                sweepAngle = FULL_SWEEP,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = arcColor,
                startAngle = START_ANGLE,
                sweepAngle = FULL_SWEEP * animated,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = value,
                fontFamily = DisplayFontFamily,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = unit.uppercase(),
                style = EyebrowTextStyle,
                color = colors.textMuted,
            )
        }
    }
}

private const val START_ANGLE = -90f
private const val FULL_SWEEP = 360f
