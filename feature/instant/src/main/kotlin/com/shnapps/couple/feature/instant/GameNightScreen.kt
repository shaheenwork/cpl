package com.shnapps.couple.feature.instant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.component.SafetyRail
import com.shnapps.couple.core.designsystem.component.raised
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.designsystem.theme.ProvideTier
import com.shnapps.couple.core.engine.NightLength
import com.shnapps.couple.core.engine.NightPhase
import com.shnapps.couple.core.ui.AfterhoursCard
import com.shnapps.couple.core.ui.Dealt
import com.shnapps.couple.core.ui.PausedContent
import com.shnapps.couple.core.ui.PausedDock
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
        onStop = viewModel::stop,
        onGentle = viewModel::carryOnGently,
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
    onStop: () -> Unit = {},
    onGentle: () -> Unit = {},
) {
    ProvideTier(state.tier) {
        val playing = state.night != null && !state.finished && !state.stopped

        ScreenScaffold(
            calm = state.stopped,
            title = if (playing) "Game Night" else null,
            onBack = onBack,
            backLabel = "Leave the evening",
            belowBar = if (playing && state.total > 0) {
                { RunProgress(current = state.position, total = state.total) }
            } else {
                null
            },
            dock = if (state.stopped) {
                { PausedDock(onGentle = onGentle, onEnd = onBack) }
            } else {
                dockFor(state, onPlan, onNext, onAgain, onBack)
            },
            centred = true,
            // The safe word, on every screen that plays anything: Game Night had none (D-143).
            rail = if (playing) {
                { SafetyRail(onStop = onStop) }
            } else {
                null
            },
        ) {
            when {
                state.stopped -> PausedContent()
                state.nothingToPlan -> AfterhoursCard(
                    eyebrow = "Nothing to plan",
                    title = "Not enough at this tier for a whole evening.",
                    body = "Turn it up, or take it a card at a time instead.",
                )
                // The length is a choice, not an action, so it is on the screen with the question
                // it answers. Stacked in the dock it sat eight dp off "Start the evening", which
                // read as one control with four extra buttons glued to its top.
                state.night == null -> Asking(state = state, onLength = onLength)
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

/**
 * What the dock offers, which is one question: which of the screen's four states it is in.
 *
 * One control per state. The length used to be in here too, which is what made the dock read as
 * one five-headed control — a dock is for the thing that moves the screen on, and a length is a
 * choice the screen is asking about.
 */
@Composable
private fun dockFor(
    state: GameNightUiState,
    onPlan: () -> Unit,
    onNext: () -> Unit,
    onAgain: () -> Unit,
    onBack: () -> Unit,
): (@Composable ColumnScope.() -> Unit) = when {
    state.nothingToPlan -> {
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
/**
 * The one question this screen asks before an evening exists.
 *
 * The heading, the shape of the evening, and the four lengths, as one thing. The lengths used to
 * live in the dock directly above the button that acts on them, which is where an action goes, not
 * a choice — and the eight dp between them made the whole dock read as one four-headed control.
 */
@Composable
private fun Asking(state: GameNightUiState, onLength: (NightLength) -> Unit) {
    val spacing = AfterhoursTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.group)) {
        ScreenHeading(
            eyebrow = "Game Night",
            headline = "How long have you got?",
            body = "It warms up, builds, peaks, and lands somewhere gentle. " +
                "Nothing in it goes past ${state.tier.label}.",
        )
        Column(verticalArrangement = Arrangement.spacedBy(spacing.tight)) {
            Text(
                text = "THE EVENING",
                style = EyebrowTextStyle,
                color = AfterhoursTheme.colors.textFaint,
            )
            LengthPicker(selected = state.length, onLength = onLength)
        }
    }
}

/**
 * The three lengths, as three cells of one object.
 *
 * Not three buttons. `GlowButtonStyle.Quiet` draws no container at all, so the two they had *not*
 * chosen read as captions rather than as the other two options — the row looked like one button
 * with some text beside it, which is half of why this screen felt broken.
 */
@Composable
private fun LengthPicker(selected: NightLength, onLength: (NightLength) -> Unit) {
    val spacing = AfterhoursTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        NightLength.entries.forEach { length ->
            LengthCell(
                label = length.label,
                chosen = length == selected,
                onClick = { onLength(length) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LengthCell(label: String, chosen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = AfterhoursTheme.spacing
    val tier = AfterhoursTheme.tier
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .heightIn(min = spacing.xxl)
            .clip(shape)
            .raised(
                color = if (chosen) tier.container else MaterialTheme.colorScheme.surfaceContainer,
                shape = shape,
                rimTop = if (chosen) tier.rim else AfterhoursTheme.colors.rimTop,
            )
            .selectable(selected = chosen, onClick = onClick)
            .padding(horizontal = spacing.sm, vertical = spacing.item),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (chosen) tier.onContainer else AfterhoursTheme.colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}
