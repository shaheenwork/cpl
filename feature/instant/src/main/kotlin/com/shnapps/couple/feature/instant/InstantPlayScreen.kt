package com.shnapps.couple.feature.instant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.shnapps.couple.core.designsystem.component.CountdownRing
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.engine.InstantCard
import com.shnapps.couple.core.model.Heat
import com.shnapps.couple.core.ui.BackRow
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.SecureScreen

/**
 * One instruction at a time (REDESIGN_PROMPT.md §5.3).
 *
 * This is the product. Not a night with chapters, not a shape that hides itself, not a progress
 * line — a card, and three things you can do about it: do it, skip it, or say it is not your kind of
 * thing. The last of those is how this app learns a limit, and it costs one tap in the moment
 * instead of twenty cards beforehand.
 */
@Composable
fun InstantPlayScreen(
    onBack: () -> Unit,
    viewModel: InstantPlayViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    InstantPlayContent(
        state = state,
        onBack = onBack,
        onDone = viewModel::done,
        onSkip = viewModel::skip,
        onTooFar = viewModel::tooFar,
        onRefuse = viewModel::refuse,
        onTurnUp = viewModel::turnUp,
        onKeepHeat = viewModel::keepHeat,
        onHeat = { heat -> viewModel.turnUp(heat) },
        onStartTimer = viewModel::startTimer,
        onStopTimer = viewModel::stopTimer,
        onKeepGoing = viewModel::keepGoing,
    )
}

@Suppress("LongParameterList") // A screen with one card and every action it offers.
@Composable
internal fun InstantPlayContent(
    state: InstantPlayUiState,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onTooFar: () -> Unit,
    onRefuse: (Boolean) -> Unit,
    onTurnUp: (Heat) -> Unit,
    onKeepHeat: () -> Unit,
    onHeat: (Heat) -> Unit,
    onStartTimer: () -> Unit,
    onStopTimer: () -> Unit,
    onKeepGoing: () -> Unit,
) {
    ScreenColumn {
        BackRow(label = "Back", onBack = onBack)

        when {
            state.refusing != null -> RefusalQuestion(onRefuse = onRefuse)
            state.offeringHotter != null -> HotterQuestion(
                next = state.offeringHotter,
                onTurnUp = onTurnUp,
                onKeep = onKeepHeat,
            )
            state.runComplete -> RunComplete(played = state.played, onKeepGoing = onKeepGoing, onBack = onBack)
            state.card != null -> Playing(
                card = state.card,
                heat = state.heat,
                timer = state.timer,
                position = state.position,
                runLength = state.runLength,
                onDone = onDone,
                onSkip = onSkip,
                onTooFar = onTooFar,
                onStartTimer = onStartTimer,
                onStopTimer = onStopTimer,
            )
            state.loading -> Text(
                text = "One moment.",
                style = MaterialTheme.typography.bodyLarge,
                color = AfterhoursTheme.colors.textMuted,
            )
            else -> Empty()
        }

        if (state.showsHeatPicker) {
            HeatPicker(selected = state.heat, onHeat = onHeat)
        }
    }
}

@Suppress("LongParameterList") // One card, its clock, and everything you can do about it.
@Composable
private fun Playing(
    card: InstantCard,
    heat: Heat,
    timer: CardTimer?,
    position: Int,
    runLength: Int,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onTooFar: () -> Unit,
    onStartTimer: () -> Unit,
    onStopTimer: () -> Unit,
) {
    CinematicCard(modifier = Modifier.fillMaxWidth(), glowing = true) {
        Text(
            text = "${heat.label.uppercase()} · ${card.minutes} MIN",
            style = EyebrowTextStyle,
            color = AfterhoursTheme.colors.brass,
        )
        Text(
            text = card.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = card.body,
            style = MaterialTheme.typography.bodyLarge,
            color = AfterhoursTheme.colors.textMuted,
        )
    }

    Progress(position = position, runLength = runLength)

    if (timer == null) {
        GlowButton(
            text = "Start the ${card.minutes} minutes",
            onClick = onStartTimer,
            style = GlowButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Countdown(timer = timer, onStop = onStopTimer)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.xs),
    ) {
        GlowButton(
            text = "Skip",
            onClick = onSkip,
            style = GlowButtonStyle.Secondary,
            modifier = Modifier.weight(1f),
        )
        GlowButton(text = "Done", onClick = onDone, modifier = Modifier.weight(1f))
    }
    GlowButton(
        text = "Too far — not this kind of thing",
        onClick = onTooFar,
        style = GlowButtonStyle.Quiet,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The clock, once someone started it. Stopping it is one tap and needs no reason. */
@Composable
private fun Countdown(timer: CardTimer, onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.xs),
    ) {
        CountdownRing(
            progress = timer.progress,
            value = if (timer.finished) "Time" else timer.remainingSeconds.clock(),
            unit = if (timer.finished) "" else "left",
        )
        GlowButton(
            text = if (timer.finished) "Put the clock away" else "Stop the clock",
            onClick = onStop,
            style = GlowButtonStyle.Quiet,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Where they are in this run. Not a target — the app offers more when it runs out. */
@Composable
private fun Progress(position: Int, runLength: Int) {
    Text(
        text = "${position.coerceAtMost(runLength)} of $runLength this run",
        style = EyebrowTextStyle,
        color = AfterhoursTheme.colors.textFaint,
    )
}

/** An evening does not end on its own. It asks. */
@Composable
private fun RunComplete(played: Int, onKeepGoing: () -> Unit, onBack: () -> Unit) {
    CinematicCard(modifier = Modifier.fillMaxWidth(), glowing = true) {
        Text(
            text = "That's $played.",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Stop here, or keep going — there is plenty left.",
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
        )
    }
    GlowButton(text = "Keep going", onClick = onKeepGoing, modifier = Modifier.fillMaxWidth())
    GlowButton(
        text = "That's us for tonight",
        onClick = onBack,
        style = GlowButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Seconds as a clock reads them. */
private fun Int.clock(): String = "%d:%02d".format(this / SECONDS_PER_MINUTE, this % SECONDS_PER_MINUTE)

private const val SECONDS_PER_MINUTE = 60

/** Two taps, and nobody is told: the whole of how a limit is set now. */
@Composable
private fun RefusalQuestion(onRefuse: (Boolean) -> Unit) {
    CinematicCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Skip this kind of thing?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Nobody is told. Not tonight, not ever — your partner never sees this.",
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
        )
    }
    GlowButton(text = "Just this one", onClick = { onRefuse(false) }, modifier = Modifier.fillMaxWidth())
    GlowButton(
        text = "Never show me these again",
        onClick = { onRefuse(true) },
        style = GlowButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Offered after a run of yeses, never applied on their behalf. */
@Composable
private fun HotterQuestion(next: Heat, onTurnUp: (Heat) -> Unit, onKeep: () -> Unit) {
    CinematicCard(modifier = Modifier.fillMaxWidth(), glowing = true) {
        Text(
            text = "Turn it up?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "You've both said yes to five in a row.",
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
        )
    }
    GlowButton(
        text = "Yes — ${next.label.lowercase()}",
        onClick = { onTurnUp(next) },
        modifier = Modifier.fillMaxWidth(),
    )
    GlowButton(
        text = "Keep it here",
        onClick = onKeep,
        style = GlowButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Everything at this heat has been refused, which is a real answer rather than an error. */
@Composable
private fun Empty() {
    CinematicCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Nothing left at this heat.",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Turn it up, or come back tomorrow — there is more at the other levels.",
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
        )
    }
}
