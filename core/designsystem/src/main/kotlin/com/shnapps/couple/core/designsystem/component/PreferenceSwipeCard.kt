package com.shnapps.couple.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.PreferenceAnswer
import com.shnapps.couple.core.model.PreferenceValue

/**
 * One card in private preference discovery (BUILD_PROMPT.md §11, §14.7).
 *
 * Cards arrive one at a time, and the answer is **private for life** — it is never
 * attributed to a person anywhere in the product (§5.1, §9). The privacy note is part of
 * the component rather than the screen, because that reassurance is the whole reason a
 * user answers honestly, and it should be impossible to forget to show it.
 *
 * "Secretly curious" is an answer in its own right rather than a toggle beside the others:
 * it only makes sense as a curiosity, and a toggle would invite "secretly never".
 *
 * [selected] marks an earlier answer when the user comes back to a card, so changing their
 * mind starts from what they said rather than from a blank.
 *
 * Answers are plain buttons. A swipe can be layered on top by the screen as an
 * accelerator, but it can never be the only way to answer: gesture-only controls are
 * unreachable for switch access and awkward for TalkBack (§20).
 */
@Composable
fun PreferenceSwipeCard(
    prompt: String,
    onAnswer: (PreferenceAnswer) -> Unit,
    modifier: Modifier = Modifier,
    category: String? = null,
    description: String? = null,
    selected: PreferenceAnswer? = null,
    enabled: Boolean = true,
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

            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textMuted,
                    )
                }
            }

            PrivacyNote()

            if (selected != null) {
                Text(
                    text = "YOUR ANSWER: ${selected.label.uppercase()}",
                    style = EyebrowTextStyle,
                    color = colors.brass,
                )
            }

            Answers(selected = selected, enabled = enabled, onAnswer = onAnswer)
        }
    }
}

/** How an answer is named to the one person who ever sees it: the person who gave it. */
val PreferenceAnswer.label: String
    get() = when {
        secret -> "Secretly curious"
        else -> when (value) {
            PreferenceValue.YES -> "Yes"
            PreferenceValue.CURIOUS -> "Curious"
            PreferenceValue.MAYBE -> "Maybe"
            PreferenceValue.NOT_FOR_ME -> "Not for me"
            PreferenceValue.NEVER -> "Never"
        }
    }

@Composable
private fun PrivacyNote() {
    val colors = AfterhoursTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Only you can see this answer. " +
                    "Your partner is told only if you both choose it."
            },
        horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.xs),
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
}

/**
 * The six answers as a fixed grid rather than a flowing row, so every card lays out the
 * same way and a thumb learns where "Yes" lives.
 */
@Composable
private fun Answers(
    selected: PreferenceAnswer?,
    enabled: Boolean,
    onAnswer: (PreferenceAnswer) -> Unit,
) {
    val spacing = AfterhoursTheme.spacing
    val button: @Composable (PreferenceAnswer, GlowButtonStyle, Modifier) -> Unit = { answer, style, modifier ->
        AnswerButton(answer, style, answer == selected, enabled, onAnswer, modifier)
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            button(PreferenceAnswer(PreferenceValue.YES), GlowButtonStyle.Primary, Modifier.weight(1f))
            button(PreferenceAnswer(PreferenceValue.CURIOUS), GlowButtonStyle.Primary, Modifier.weight(1f))
        }
        button(PreferenceAnswer.SECRETLY_CURIOUS, GlowButtonStyle.Secondary, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            button(PreferenceAnswer(PreferenceValue.MAYBE), GlowButtonStyle.Secondary, Modifier.weight(1f))
            button(PreferenceAnswer(PreferenceValue.NOT_FOR_ME), GlowButtonStyle.Secondary, Modifier.weight(1f))
        }
        button(
            PreferenceAnswer(PreferenceValue.NEVER),
            GlowButtonStyle.Quiet,
            Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun AnswerButton(
    answer: PreferenceAnswer,
    style: GlowButtonStyle,
    isSelected: Boolean,
    enabled: Boolean,
    onAnswer: (PreferenceAnswer) -> Unit,
    modifier: Modifier,
) {
    val ring = if (isSelected) {
        Modifier.border(AfterhoursTheme.spacing.xxs, AfterhoursTheme.colors.brass, MaterialTheme.shapes.medium)
    } else {
        Modifier
    }
    GlowButton(
        text = answer.label,
        onClick = { onAnswer(answer) },
        style = style,
        enabled = enabled,
        leadingEmoji = if (answer.secret) SECRET_EMOJI else null,
        modifier = modifier
            .then(ring)
            .semantics { selected = isSelected },
    )
}

private const val SECRET_EMOJI = "👀"
