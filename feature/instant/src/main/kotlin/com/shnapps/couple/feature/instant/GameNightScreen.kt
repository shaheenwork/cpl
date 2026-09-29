package com.shnapps.couple.feature.instant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.ProvideTier
import com.shnapps.couple.core.engine.NightLength
import com.shnapps.couple.core.engine.NightPhase
import com.shnapps.couple.core.ui.AfterhoursCard
import com.shnapps.couple.core.ui.Dealt
import com.shnapps.couple.core.ui.RunProgress
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.ScreenScaffold
import com.shnapps.couple.core.ui.SecureScreen

/**
 * Game Night (the brief's game 12): an evening with a shape.
 *
 * Three states in one screen, and only ever one at a time — choose a length, walk the cards, then
 * the card written to land on. The plan is made once and not re-decided, so nothing here offers a
 * tier, a lane or a skip: those belong to the deck, where a card at a time is the whole idea. What
 * this has instead is the one thing a deck cannot give, which is an ending.
 */
@Composable
fun GameNightScreen(
    onBack: () -> Unit,
    viewModel: GameNightViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    GameNightContent(
        state = state,
        onLength = viewModel::setLength,
        onPlan = viewModel::plan,
        onNext = viewModel::next,
        onAgain = viewModel::again,
        onBack = onBack,
    )
}

@Suppress("LongParameterList") // One screen, four states, every way through them.
@Composable
internal fun GameNightContent(
    state: GameNightUiState,
    onLength: (NightLength) -> Unit,
    onPlan: () -> Unit,
    onNext: () -> Unit,
    onAgain: () -> Unit,
    onBack: () -> Unit,
) {
    ProvideTier(state.tier) {
        val playing = state.night != null && !state.finished

        ScreenScaffold(
            title = if (playing) "Game Night" else null,
            onBack = onBack,
            backLabel = "Leave the evening",
            belowBar = if (playing && state.total > 0) {
                { RunProgress(current = state.position, total = state.total) }
            } else {
                null
            },
            dock = dockFor(state, onLength, onPlan, onNext, onAgain, onBack),
            centred = true,
        ) {
            when {
                state.outOfNights -> AfterhoursCard(
                    eyebrow = "That's this week",
                    title = "One guided evening a week on the free plan.",
                    body = "Cards are still there, and every tier and every game is free. " +
                        "Premium takes the count off for both of you.",
                )
                state.nothingToPlan -> AfterhoursCard(
                    eyebrow = "Nothing to plan",
                    title = "Not enough at this tier for a whole evening.",
                    body = "Turn it up, or take it a card at a time instead.",
                )
                state.night == null -> ScreenHeading(
                    eyebrow = "Game Night",
                    headline = "How long have you got?",
                    body = "It warms up, builds, peaks, and lands somewhere gentle. " +
                        "Nothing in it goes past ${state.tier.label}.",
                )
                state.finished -> AfterhoursCard(
                    eyebrow = "That's the evening",
                    title = "Nicely done, both of you.",
                    body = "Plan another when you want one, or go back to a card at a time.",
                )
                else -> Playing(state)
            }
        }
    }
}

@Composable
private fun Playing(state: GameNightUiState) {
    val card = state.card ?: return

    Dealt(key = card.card.id) {
        AfterhoursCard(
            eyebrow = "${card.phase.label} · ${card.card.minutes} min",
            title = card.card.title,
            body = card.card.body,
            // The last card says so rather than counting: an ending should read as one.
            meta = if (card.phase == NightPhase.AFTERCARE) "The last one" else "${state.remaining} to go",
            index = state.position,
            // Only the peak is lit. A phase that looks like every other phase is not a shape.
            lit = card.phase == NightPhase.PEAK,
        )
    }
}

/** What the dock offers, which is one question: which of the screen's four states it is in. */
@Suppress("LongParameterList") // Four states, four ways on.
@Composable
private fun dockFor(
    state: GameNightUiState,
    onLength: (NightLength) -> Unit,
    onPlan: () -> Unit,
    onNext: () -> Unit,
    onAgain: () -> Unit,
    onBack: () -> Unit,
): (@Composable ColumnScope.() -> Unit) = when {
    state.outOfNights || state.nothingToPlan -> {
        {
            GlowButton(
                text = "Back",
                onClick = onBack,
                leadingIcon = AfterhoursIcons.Forward,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    state.night == null -> {
        {
            LengthPicker(selected = state.length, onLength = onLength)
            GlowButton(
                text = if (state.planning) "Planning…" else "Start the evening",
                onClick = onPlan,
                enabled = !state.planning,
                leadingIcon = AfterhoursIcons.Flame,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    state.finished -> {
        {
            GlowButton(
                text = "Plan another",
                onClick = onAgain,
                leadingIcon = AfterhoursIcons.Dice,
                modifier = Modifier.fillMaxWidth(),
            )
            GlowButton(
                text = "That's us for tonight",
                onClick = onBack,
                style = GlowButtonStyle.Quiet,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    else -> {
        {
            GlowButton(
                text = if (state.remaining <= 1) "Finish" else "Done",
                onClick = onNext,
                leadingIcon = AfterhoursIcons.Check,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Thirty, forty-five or an hour. Three across, like every other choice in the app. */
@Composable
private fun LengthPicker(selected: NightLength, onLength: (NightLength) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.xs),
    ) {
        NightLength.entries.forEach { length ->
            GlowButton(
                text = length.label,
                onClick = { onLength(length) },
                style = if (length == selected) GlowButtonStyle.Secondary else GlowButtonStyle.Quiet,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
