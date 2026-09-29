package com.shnapps.couple.feature.boundaries

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.decorativeTween
import com.shnapps.couple.core.model.LimitAnswer
import com.shnapps.couple.core.model.LimitTag
import com.shnapps.couple.core.ui.BackRow
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.SecureScreen

/**
 * The limits profile (M3).
 *
 * Eight rows, three answers each, and no way to get it wrong: there is no save button, no required
 * field and no order to answer in. Anything left alone stays unanswered, which is a real state —
 * the tier is what governs an evening until somebody says otherwise.
 *
 * A `SecureScreen`, because this is the most sensitive list in the product: what one person has
 * ruled out. It is written to their own documents, which the partner can never read (§5.1), and
 * the couple's filter is computed on the server from both sides without either seeing the other's
 * (§3.2, D-032).
 */
@Composable
fun LimitsScreen(
    onBack: () -> Unit,
    viewModel: LimitsViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LimitsContent(
        state = state,
        onAnswer = viewModel::answer,
        onDismissError = viewModel::dismissError,
        onBack = onBack,
    )
}

@Composable
internal fun LimitsContent(
    state: LimitsUiState,
    onAnswer: (LimitTag, LimitAnswer) -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
) {
    ScreenColumn {
        BackRow(label = "Back", onBack = onBack)
        ScreenHeading(
            eyebrow = "Your limits",
            headline = "What are you up for?",
            body = "Only you ever see this. Your partner is never told what you said, and " +
                "nothing you mark as no will come up for either of you.",
        )

        state.error?.let { error -> ErrorBanner(message = error.message(), onDismiss = onDismissError) }

        state.rows.forEach { row ->
            LimitRowView(row = row, onAnswer = { answer -> onAnswer(row.tag, answer) })
        }

        Text(
            text = if (state.answered == 0) {
                "Nothing set. Leave it that way if you like — how far tonight goes is the tier."
            } else {
                "${state.answered} of ${state.rows.size} answered. You can change any of them any time."
            },
            style = MaterialTheme.typography.bodySmall,
            color = AfterhoursTheme.colors.textFaint,
        )
    }
}

/**
 * What went wrong, said in the terms of this screen.
 *
 * Kept here rather than in the data layer for the reason `AppError.Validation` carries a code
 * instead of a sentence: the copy belongs with the screen that shows it. Every one of these says
 * the same thing underneath — the answer is safe on this phone, only the account did not get it.
 */
private fun AppError.message(): String = when (this) {
    is AppError.Network -> "Saved here, but not to your account yet. It will sync when you are back online."
    is AppError.Unauthenticated -> "Saved on this phone. Sign in and it will follow you."
    else -> "Saved on this phone. We could not reach your account just now."
}

@Composable
private fun LimitRowView(row: LimitRow, onAnswer: (LimitAnswer) -> Unit) {
    val spacing = AfterhoursTheme.spacing

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = row.tag.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = row.tag.asks,
            style = MaterialTheme.typography.bodySmall,
            color = AfterhoursTheme.colors.textMuted,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(spacing.xs)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            LimitAnswer.entries.forEach { answer ->
                AnswerSegment(
                    answer = answer,
                    selected = row.answer == answer,
                    tag = row.tag,
                    onClick = { onAnswer(answer) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AnswerSegment(
    answer: LimitAnswer,
    selected: Boolean,
    tag: LimitTag,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val spacing = AfterhoursTheme.spacing
    val reduceMotion = AfterhoursTheme.reduceMotion
    val colors = AfterhoursTheme.colors

    // A no is the answer that removes content, so it is the one that reads as a stop rather than
    // as an accent. Nothing else on this screen borrows the tier's colour: a limit is not a mood.
    val fill by animateColorAsState(
        targetValue = when {
            !selected -> Color.Transparent
            answer == LimitAnswer.NO -> MaterialTheme.colorScheme.errorContainer
            else -> AfterhoursTheme.tier.container
        },
        animationSpec = decorativeTween(AfterhoursTheme.motion.standard, reduceMotion, AfterhoursEasing.Standard),
        label = "answerFill",
    )
    val label by animateColorAsState(
        targetValue = when {
            !selected -> colors.textFaint
            answer == LimitAnswer.NO -> MaterialTheme.colorScheme.onErrorContainer
            else -> AfterhoursTheme.tier.onContainer
        },
        animationSpec = decorativeTween(AfterhoursTheme.motion.standard, reduceMotion, AfterhoursEasing.Standard),
        label = "answerLabel",
    )

    Box(
        modifier = modifier
            .heightIn(min = spacing.touchTarget)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(fill)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = "${tag.label}: ${answer.label}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = answer.label, style = MaterialTheme.typography.labelLarge, color = label)
    }
}
