package com.shnapps.couple.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme

/**
 * The signature animation of the product (BUILD_PROMPT.md §15.2): blurred card, slow glow
 * bloom, reveal. It is what "WAIT... YOU BOTH PICKED THIS 👀" is made of, and §14.7 gives
 * it the best animation in the app.
 *
 * Two things this gets deliberately right:
 *
 * **Concealment is real, not visual.** While [state] is [RevealState.Concealed] the
 * content is not composed at all. A blur that merely hides text still ships that text to
 * the view hierarchy, where a screen reader — or a screenshot — would find it. Since the
 * whole mechanic rests on one partner genuinely not knowing yet, hiding has to mean
 * absent (§5.7).
 *
 * **Reduce-motion keeps the timing.** The bloom and scale are decorative and collapse to
 * nothing, but the reveal still steps through its states rather than snapping open,
 * because skipping it would surface the content early and destroy the anticipation the
 * product runs on (§15.2).
 */
@Composable
fun RevealCard(
    state: RevealState,
    modifier: Modifier = Modifier,
    concealedLabel: String = "Not yet",
    concealedDescription: String = "Hidden until it is revealed",
    /** The bloom's colour. Brass marks the rarest reveal: both secretly curious (§14.7). */
    glowColor: Color = AfterhoursTheme.colors.glow,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = AfterhoursTheme.motion
    val reduceMotion = AfterhoursTheme.reduceMotion

    val revealed = state == RevealState.Revealed
    val revealing = state != RevealState.Concealed

    val bloom by animateFloatAsState(
        targetValue = if (state == RevealState.Revealing) 1f else if (revealed) 0.35f else 0f,
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else motion.bloom,
            easing = AfterhoursEasing.Anticipation,
        ),
        label = "revealBloom",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else motion.reveal,
            easing = AfterhoursEasing.Decelerate,
        ),
        label = "revealContentAlpha",
    )
    val softness by animateFloatAsState(
        targetValue = if (revealed) 0f else 1f,
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else motion.reveal,
            easing = AfterhoursEasing.Anticipation,
        ),
        label = "revealSoftness",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .glow(glowColor, alpha = bloom, spread = 32.dp),
    ) {
        CinematicCard(
            glowing = false,
            modifier = Modifier.graphicsLayer {
                // A touch of swell as it opens. Decorative, so reduce-motion flattens it.
                val scale = if (reduceMotion) 1f else 0.98f + 0.02f * contentAlpha
                scaleX = scale
                scaleY = scale
            },
        ) {
            if (revealing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(contentAlpha)
                        // Modifier.blur is a no-op below API 31, so the alpha above is
                        // what actually carries the effect on older devices.
                        .blur(radius = (MAX_BLUR * softness).dp),
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.fillMaxWidth(),
                        content = content,
                    )
                }
            } else {
                // Concealed: the real content is not in the tree at all.
                ConcealedFace(label = concealedLabel, description = concealedDescription)
            }
        }
    }
}

@Composable
private fun ConcealedFace(label: String, description: String) {
    val colors = AfterhoursTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // A card-sized face: something is clearly waiting to be turned over.
            .heightIn(min = AfterhoursTheme.spacing.xxxl * 2)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textFaint,
        )
    }
}

private const val MAX_BLUR = 18f
