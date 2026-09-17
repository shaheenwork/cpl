package com.shnapps.couple.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.PreferenceValue

/**
 * One card in private preference discovery (BUILD_PROMPT.md §11, §14.7).
 *
 * Cards arrive one at a time, and the answer is **private for life** — it is never
 * attributed to a person anywhere in the product (§5.1, §9). The privacy note is part of
 * the component rather than the screen, because that reassurance is the whole reason a
 * user answers honestly, and it should be impossible to forget to show it.
 *
 * Answers are plain buttons, not a swipe gesture. A swipe can be layered on top later as
 * an accelerator, but it can never be the only way to answer: gesture-only controls are
 * unreachable for switch access and awkward for TalkBack (§20).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferenceSwipeCard(
    prompt: String,
    onAnswer: (PreferenceValue) -> Unit,
    modifier: Modifier = Modifier,
    category: String? = null,
    secretlyCurious: Boolean = false,
    onToggleSecret: (() -> Unit)? = null,
) {
    val colors = AfterhoursTheme.colors
    val spacing = AfterhoursTheme.spacing

    CinematicCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            if (category != null) {
                Text(
                    text = category.uppercase(),
                    style = EyebrowTextStyle,
                    color = colors.textFaint,
                )
            }

            Text(
                text = prompt,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Only you can see this answer. " +
                            "Your partner is told only if you both choose it."
                    },
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.VisibilityOff,
                    contentDescription = null,
                    tint = colors.textFaint,
                )
                Text(
                    text = "Only you see this. They find out only if you both pick it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textFaint,
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                AnswerButton("Yes", PreferenceValue.YES, onAnswer, GlowButtonStyle.Primary)
                AnswerButton("Curious", PreferenceValue.CURIOUS, onAnswer, GlowButtonStyle.Primary)
                AnswerButton("Maybe", PreferenceValue.MAYBE, onAnswer, GlowButtonStyle.Secondary)
                AnswerButton("Not for me", PreferenceValue.NOT_FOR_ME, onAnswer, GlowButtonStyle.Secondary)
                AnswerButton("Never", PreferenceValue.NEVER, onAnswer, GlowButtonStyle.Quiet)
            }

            if (onToggleSecret != null) {
                GlowButton(
                    text = if (secretlyCurious) "Marked secretly curious" else "Secretly curious",
                    onClick = onToggleSecret,
                    style = GlowButtonStyle.Quiet,
                    leadingEmoji = "👀",
                    modifier = Modifier
                        .heightIn(min = spacing.touchTarget)
                        .padding(top = spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun AnswerButton(
    label: String,
    value: PreferenceValue,
    onAnswer: (PreferenceValue) -> Unit,
    style: GlowButtonStyle,
) {
    GlowButton(
        text = label,
        onClick = { onAnswer(value) },
        style = style,
        modifier = Modifier.heightIn(min = AfterhoursTheme.spacing.touchTarget),
    )
}
