package com.shnapps.couple.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.decorativeTween

/**
 * The product's call to action. A bloom sits behind it and swells on press, so the button
 * reads as lit rather than merely filled (BUILD_PROMPT.md §15.1).
 *
 * [leadingEmoji] is decorative and is deliberately kept out of the accessibility tree —
 * the label already carries the meaning, and TalkBack announcing "fire emoji" is noise.
 */
@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlowButtonStyle = GlowButtonStyle.Primary,
    enabled: Boolean = true,
    leadingEmoji: String? = null,
) {
    val colors = AfterhoursTheme.colors
    val spacing = AfterhoursTheme.spacing
    val reduceMotion = AfterhoursTheme.reduceMotion

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val glowAlpha by animateFloatAsState(
        targetValue = when {
            !enabled -> 0f
            pressed -> 1f
            else -> 0.55f
        },
        animationSpec = decorativeTween(
            durationMillis = AfterhoursTheme.motion.standard,
            reduceMotion = reduceMotion,
            easing = AfterhoursEasing.Standard,
        ),
        label = "glowButtonBloom",
    )

    val glowColor = when (style) {
        GlowButtonStyle.Primary -> colors.glow
        GlowButtonStyle.Secondary, GlowButtonStyle.Quiet -> colors.glowSecondary
    }

    val buttonModifier = modifier
        .glow(glowColor, alpha = glowAlpha, spread = 12.dp, cornerRadius = 16.dp)
        .defaultMinSize(minHeight = spacing.touchTarget)

    val label: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingEmoji != null) {
                Text(
                    text = leadingEmoji,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }

    when (style) {
        GlowButtonStyle.Primary -> Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = MaterialTheme.shapes.medium,
        ) { label() }

        GlowButtonStyle.Secondary -> OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(spacing.hairline, MaterialTheme.colorScheme.outline),
        ) { label() }

        GlowButtonStyle.Quiet -> TextButton(
            onClick = onClick,
            modifier = modifier
                .defaultMinSize(minHeight = spacing.touchTarget)
                .padding(horizontal = spacing.xxs),
            enabled = enabled,
            interactionSource = interactionSource,
        ) { label() }
    }
}
