package com.shnapps.couple.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme

/**
 * "Someone is choosing…" — the ambient, breathing wait (BUILD_PROMPT.md §15.2).
 *
 * Shown on one device while the other partner is deciding, during Partner Control and
 * Tonight's Director. The breathing is the point: a spinner would say "loading", and this
 * has to say "they are doing something and you do not get to know what yet".
 *
 * The message is a polite live region, so TalkBack announces the change without
 * interrupting whatever the user is already hearing. Under reduce-motion the pulse stops
 * and the card simply rests — still clearly a waiting state, minus the performance.
 */
@Composable
fun WaitingForPartner(
    message: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val colors = AfterhoursTheme.colors
    val spacing = AfterhoursTheme.spacing
    val reduceMotion = AfterhoursTheme.reduceMotion

    val transition = rememberInfiniteTransition(label = "waitingForPartner")
    val pulse by transition.animateFloat(
        initialValue = if (reduceMotion) 1f else 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AfterhoursTheme.motion.ambient,
                easing = AfterhoursEasing.Breathing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "waitingPulse",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(spacing.lg)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = listOfNotNull(message, detail).joinToString(". ")
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                    alpha = pulse
                }
                .glow(colors.glowSecondary, radiusScale = 1.4f),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.size(14.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary,
            ) {}
        }
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}
