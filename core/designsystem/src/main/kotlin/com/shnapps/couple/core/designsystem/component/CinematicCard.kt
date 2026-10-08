package com.shnapps.couple.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme

/**
 * A raised panel: the base surface for something the product presents as a group.
 *
 * It used to be the hero as well, which is why the card a couple actually reads looked
 * like a list row. The hero is `AfterhoursCard` now (DESIGN_PROMPT.md §5.1); this is
 * everything else — a panel, a grouped block, a summary.
 *
 * Depth comes from `Modifier.raised`: fill, a rim light brighter along the top edge, and
 * weight underneath from `floating` up. Not from a border of uniform hairline, which is
 * what made every surface in the app read as the same object.
 */
@Composable
fun CinematicCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    glowing: Boolean = false,
    /**
     * Breathes, rather than simply being lit.
     *
     * For one thing only: something waiting on this phone that the *other* phone started. A card
     * that moves is the difference between "there is a card here" and "this is for you, now", and
     * a screen with two of them has neither.
     */
    pulsing: Boolean = false,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = AfterhoursTheme.spacing
    val tier = AfterhoursTheme.tier
    // Bound to a local so the semantics setter below is not shadowed by the parameter.
    val description = contentDescription

    // Decorative, so it collapses to its lit resting state under reduce-motion rather than
    // settling anywhere dimmer: the glow still says the card is waiting.
    val breath = if (pulsing && !AfterhoursTheme.reduceMotion) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = PULSE_LOW,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(AfterhoursTheme.motion.ambient, easing = AfterhoursEasing.Breathing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse",
        ).value
    } else {
        1f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (glowing) {
                    Modifier.bloom(
                        color = tier.rim,
                        cornerRadius = spacing.lg,
                        spread = spacing.xl * breath,
                        alpha = breath,
                    )
                } else {
                    Modifier
                },
            )
            .raised(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = shape,
                rimTop = if (glowing) tier.rim.copy(alpha = RIM_LIT) else AfterhoursTheme.colors.rimTop,
            )
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(
                if (description != null) {
                    Modifier.semantics { this.contentDescription = description }
                } else {
                    Modifier
                },
            )
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.tight),
        content = content,
    )
}

/** How bright a lit panel's top edge goes. Enough to read as light, not as a stroke. */
private const val RIM_LIT = 0.30f

/** How far the glow falls back on the out-breath. Never to nothing: it is still lit. */
private const val PULSE_LOW = 0.45f
