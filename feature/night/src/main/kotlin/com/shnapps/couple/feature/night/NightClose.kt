package com.shnapps.couple.feature.night

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.ChapterReaction
import com.shnapps.couple.core.model.CloseCheckIn
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading

/**
 * "How are we doing?" (§3.1): the check-in at the close. Aftercare, and a product feature —
 * not a form. Nothing here is stored (DECISIONS.md D-065); it only decides what to say next.
 */
@Composable
internal fun HowAreWeStage(state: NightUiState, actions: NightActions) {
    ScreenColumn {
        ScreenHeading(
            eyebrow = "Night complete",
            headline = "How are we doing?",
            body = "Just a moment for the two of you.",
        )
        val answer = state.closeAnswer
        if (answer == null) {
            CloseCheckIn.entries.forEach { option ->
                GlowButton(
                    text = option.label,
                    onClick = { actions.onAnswerClose(option) },
                    leadingEmoji = option.emoji,
                    style = GlowButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Text(
                text = answer.followUp,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (answer == CloseCheckIn.A_BIT_MUCH) {
                GlowButton(
                    text = "Adjust my limits",
                    onClick = actions.onAdjustLimits,
                    style = GlowButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            GlowButton(text = "Continue", onClick = actions.onToFeedback, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** How each chapter landed — private to whoever says so, and used only to fit future nights. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FeedbackStage(state: NightUiState, actions: NightActions) {
    val night = state.night ?: return
    val spacing = AfterhoursTheme.spacing
    ScreenColumn {
        ScreenHeading(
            eyebrow = "Just for you",
            headline = "How did tonight land?",
            body = "Your reactions are yours alone. They help your next nights fit you better.",
        )
        state.played.forEach { index ->
            val chapter = night.chapters[index]
            CinematicCard {
                Text(
                    text = "Chapter ${index + 1} · ${chapter.kind.label}".uppercase(),
                    style = EyebrowTextStyle,
                    color = AfterhoursTheme.colors.textFaint,
                )
                Text(
                    text = chapter.item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    ChapterReaction.entries.forEach { reaction ->
                        FilterChip(
                            selected = state.reactions[index] == reaction,
                            onClick = { actions.onReact(index, reaction) },
                            modifier = Modifier.heightIn(min = spacing.touchTarget),
                            label = {
                                Text(
                                    text = "${reaction.emoji} ${reaction.label}",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                            shape = MaterialTheme.shapes.small,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }
        }
        GlowButton(
            text = "Save",
            onClick = actions.onSaveFeedback,
            enabled = state.reactions.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
        GlowButton(
            text = "Skip",
            onClick = actions.onSkipFeedback,
            style = GlowButtonStyle.Quiet,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** "NIGHT COMPLETE ❤️" (§14.8). Saving it to Our Memories arrives with the library (Phase 18). */
@Composable
internal fun DoneStage(onHome: () -> Unit) {
    ScreenColumn {
        ScreenHeading(eyebrow = "Night complete", headline = "Night complete ❤️", body = "Sleep well, you two.")
        GlowButton(text = "Back home", onClick = onHome, modifier = Modifier.fillMaxWidth())
    }
}
