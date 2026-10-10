package com.shnapps.couple.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.decorativeTween

/**
 * The product's call to action (DESIGN_PROMPT.md §5.2).
 *
 * Three styles, and they have to be distinguishable in a greyscale screenshot — that is
 * the test, because a person deciding at eleven at night reads weight before they read
 * colour.
 *
 *  - **Primary** takes the heat's fill, with a sheen across its top and a ring in the heat's rim
 *    (D-145). One per screen. It blooms, which is why the rule about one per screen matters: two
 *    bloomed buttons is a lit row, and a lit row is a smear.
 *  - **Secondary** is velvet: a fill a step above the room, its rim, and a label.
 *  - **Quiet** is a label.
 *
 * Press is a scale of [PRESSED_SCALE] and a brighter fill — never a bigger halo, which is
 * what the old button did and which read as the whole control inflating.
 *
 * [leadingIcon] replaces the emoji this API used to take. Emoji cannot be coloured, sized
 * or animated, differ on every platform, and are the loudest amateur signal a product can
 * carry (§4.9). It is decorative here: the label already says what the button does, so it
 * stays out of the accessibility tree.
 */
@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlowButtonStyle = GlowButtonStyle.Primary,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val spacing = AfterhoursTheme.spacing
    val tier = AfterhoursTheme.tier
    val reduceMotion = AfterhoursTheme.reduceMotion

    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    /**
     * A light tick on every press that did something (BUILD_PROMPT.md §20).
     *
     * One place for the whole app, so a Done in a game feels like a Done on a card. The
     * system's own haptic setting governs whether anything is felt at all — this asks, it
     * does not insist — and it says nothing when the button is disabled, because nothing
     * happened.
     */
    val click: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }

    val pressed by interactionSource.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed && enabled) PRESSED_SCALE else 1f,
        animationSpec = decorativeTween(AfterhoursTheme.motion.quick, reduceMotion, AfterhoursEasing.Standard),
        label = "buttonPress",
    )
    val fill by animateColorAsState(
        targetValue = when {
            !enabled -> tier.accent.copy(alpha = DISABLED_FILL)
            pressed -> tier.accent.copy(alpha = PRESSED_FILL)
            else -> tier.accent
        },
        animationSpec = decorativeTween(AfterhoursTheme.motion.quick, reduceMotion, AfterhoursEasing.Standard),
        label = "buttonFill",
    )

    val label: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(ICON),
                )
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }

    // heightIn rather than defaultMinSize: Material's own button applies a 40 dp default
    // inside this one, and a default loses to a default. A touch target is not negotiable (§20).
    val sized = modifier
        .scale(press)
        .heightIn(min = spacing.touchTarget)
    val shape = MaterialTheme.shapes.medium

    when (style) {
        GlowButtonStyle.Primary -> Button(
            onClick = click,
            modifier = sized
                .lit(enabled = enabled, fill = fill, shape = shape)
                .heightIn(min = PRIMARY_HEIGHT),
            enabled = enabled,
            interactionSource = interactionSource,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = tier.onAccent,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = tier.onAccent.copy(alpha = DISABLED_LABEL),
            ),
        ) { label() }

        GlowButtonStyle.Secondary -> Button(
            onClick = click,
            modifier = sized
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ),
                )
                .rim(shape),
            enabled = enabled,
            interactionSource = interactionSource,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (pressed) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_PRESSED)
                } else {
                    Color.Transparent
                },
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = AfterhoursTheme.colors.textFaint,
            ),
        ) { label() }

        GlowButtonStyle.Quiet -> TextButton(
            onClick = click,
            modifier = sized.padding(horizontal = spacing.xxs),
            enabled = enabled,
            interactionSource = interactionSource,
            colors = ButtonDefaults.textButtonColors(
                contentColor = if (pressed) MaterialTheme.colorScheme.onSurface else AfterhoursTheme.colors.textMuted,
                disabledContentColor = AfterhoursTheme.colors.textFaint,
            ),
        ) { label() }
    }
}

/**
 * A primary button's surface: its bloom, the heat's fill, a sheen where the light catches its top
 * edge, and a ring in the heat's rim — what makes a filled button an object rather than a patch.
 * Disabled, it is the fill alone: nothing about a button that does nothing should glow.
 */
@Composable
private fun Modifier.lit(enabled: Boolean, fill: Color, shape: Shape): Modifier {
    val tier = AfterhoursTheme.tier
    val sheen = MaterialTheme.colorScheme.onBackground
    if (!enabled) return clip(shape).background(fill)
    return bloom(color = tier.rim, cornerRadius = BUTTON_CORNER, spread = BLOOM_SPREAD)
        .clip(shape)
        .background(fill)
        .drawBehind {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(sheen.copy(alpha = SHEEN), sheen.copy(alpha = 0f)),
                    center = Offset(size.width / 2f, 0f),
                    radius = size.width * SHEEN_REACH,
                ),
            )
        }
        .border(AfterhoursTheme.spacing.hairline, tier.rim.copy(alpha = RING), shape)
}

private val ICON = 18.dp
private val PRIMARY_HEIGHT = 52.dp
private val BUTTON_CORNER = 16.dp
private val BLOOM_SPREAD = 18.dp

/** The whole press language of the app, in one number. */
private const val PRESSED_SCALE = 0.985f
private const val PRESSED_FILL = 0.86f
private const val DISABLED_FILL = 0.30f
private const val DISABLED_LABEL = 0.55f
private const val SECONDARY_PRESSED = 0.06f

/** The sheen across a primary button's top, how far it reaches, and its ring in the heat's rim. */
private const val SHEEN = 0.22f
private const val SHEEN_REACH = 0.7f
private const val RING = 0.4f
