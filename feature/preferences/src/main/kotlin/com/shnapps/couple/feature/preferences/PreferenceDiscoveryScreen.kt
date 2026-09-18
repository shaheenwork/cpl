package com.shnapps.couple.feature.preferences

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.component.IntensityDial
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.decorativeTween
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.PreferenceAnswer
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.SecureScreen

/**
 * Private preference discovery (BUILD_PROMPT.md §9.5, §14.7).
 *
 * A `SecureScreen`: every card here can carry an answer, and answers are the most private
 * thing in the product (§3.4). No screenshots, no app-switcher thumbnail.
 */
@Composable
fun PreferenceDiscoveryScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PreferenceDiscoveryViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Back steps within discovery first (previous card, out of an edit, out of the review),
    // and only leaves the screen from the intro or the end.
    BackHandler(enabled = state.handlesBack, onBack = viewModel::back)

    PreferenceDiscoveryContent(
        state = state,
        onLevelChange = viewModel::setContentLevel,
        onStart = viewModel::start,
        onAnswer = viewModel::answer,
        onSkip = viewModel::skip,
        onBack = viewModel::back,
        onFinishLater = viewModel::finishLater,
        onReview = viewModel::openReview,
        onEdit = viewModel::edit,
        onClearAnswer = viewModel::clearAnswer,
        onDismissError = viewModel::dismissError,
        onFinished = onFinished,
        modifier = modifier,
    )
}

@Composable
internal fun PreferenceDiscoveryContent(
    state: PreferenceDiscoveryUiState,
    onLevelChange: (Intensity) -> Unit,
    onStart: () -> Unit,
    onAnswer: (PreferenceAnswer) -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onFinishLater: () -> Unit,
    onReview: () -> Unit,
    onEdit: (String) -> Unit,
    onClearAnswer: () -> Unit,
    onDismissError: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = AfterhoursTheme.motion
    val reduceMotion = AfterhoursTheme.reduceMotion

    Box(modifier = modifier.fillMaxSize()) {
        // Keyed on the stage, but carrying the whole state: the outgoing stage keeps
        // rendering its own last state while it fades, rather than the incoming one's.
        AnimatedContent(
            targetState = state,
            contentKey = { it.stage },
            transitionSpec = {
                fadeIn(decorativeTween(motion.slow, reduceMotion)) togetherWith
                    fadeOut(decorativeTween(motion.standard, reduceMotion))
            },
            label = "discoveryStage",
        ) { shown ->
            when (shown.stage) {
                DiscoveryStage.Loading -> Unit
                DiscoveryStage.Intro -> IntroStep(shown, onLevelChange, onStart, onReview, onFinished)
                DiscoveryStage.Answering -> AnsweringStep(shown, onAnswer, onSkip, onBack, onFinishLater)
                DiscoveryStage.Done -> DoneStep(shown, onReview, onFinished)
                DiscoveryStage.Review -> ReviewStep(shown, onEdit, onBack)
                DiscoveryStage.Editing -> EditingStep(shown, onAnswer, onClearAnswer, onBack)
            }
        }

        if (state.errorMessage != null) {
            ErrorBanner(
                message = state.errorMessage,
                onDismiss = onDismissError,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun IntroStep(
    state: PreferenceDiscoveryUiState,
    onLevelChange: (Intensity) -> Unit,
    onStart: () -> Unit,
    onReview: () -> Unit,
    onNotNow: () -> Unit,
) {
    val spacing = AfterhoursTheme.spacing
    ScreenColumn {
        ScreenHeading(
            eyebrow = "Only you",
            headline = "Your private curiosities",
            body = "One card at a time. Your partner never sees your answers. If you both pick " +
                "the same thing, you'll both find out.",
        )
        // The promise Phase 7's matching must keep (DECISIONS.md D-021): choosing "secretly"
        // never reveals more than answering openly would.
        Text(
            text = "👀 Secretly curious stays hidden unless they secretly pick it too.",
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
        )

        CinematicCard {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(
                    text = "How far should the questions go?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IntensityDial(selected = state.contentLevel, onSelect = onLevelChange)
                Text(
                    text = "Change it any time. Together, you only ever go as far as the more " +
                        "careful of you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AfterhoursTheme.colors.textMuted,
                )
            }
        }

        if (state.remainingCount > 0) {
            GlowButton(
                text = "Start · ${cards(state.remainingCount)}",
                onClick = onStart,
                leadingEmoji = "👀",
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = "You've answered every card at this level.",
                style = MaterialTheme.typography.bodyLarge,
                color = AfterhoursTheme.colors.textMuted,
            )
        }
        if (state.answeredCount > 0) {
            GlowButton(
                text = "Review my answers",
                onClick = onReview,
                style = GlowButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        GlowButton(
            text = "Not now",
            onClick = onNotNow,
            style = GlowButtonStyle.Quiet,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun DoneStep(
    state: PreferenceDiscoveryUiState,
    onReview: () -> Unit,
    onContinue: () -> Unit,
) {
    ScreenColumn {
        ScreenHeading(
            eyebrow = "Saved, privately",
            headline = if (state.remainingCount == 0) "That's every card for now." else "Saved. Pick up any time.",
            body = buildString {
                append("You answered ${cards(state.answeredThisSession)} this time. ")
                append(
                    "Nothing is shared unless you both pick the same thing, and even then it " +
                        "comes to light slowly, one at a time.",
                )
            },
        )
        if (state.remainingCount > 0) {
            Text(
                text = "${cards(state.remainingCount).replaceFirstChar { it.uppercase() }} left whenever " +
                    "you're ready.",
                style = MaterialTheme.typography.bodyLarge,
                color = AfterhoursTheme.colors.textMuted,
            )
        }
        GlowButton(text = "Continue", onClick = onContinue, modifier = Modifier.fillMaxWidth())
        if (state.answeredCount > 0) {
            GlowButton(
                text = "Review my answers",
                onClick = onReview,
                style = GlowButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** "1 card", "12 cards". */
internal fun cards(count: Int): String = if (count == 1) "1 card" else "$count cards"
