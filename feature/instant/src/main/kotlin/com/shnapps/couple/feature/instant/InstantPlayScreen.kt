package com.shnapps.couple.feature.instant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.CinematicCard
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
            state.card != null -> Playing(
                card = state.card,
                heat = state.heat,
                onDone = onDone,
                onSkip = onSkip,
                onTooFar = onTooFar,
            )
            state.loading -> Text(
                text = "One moment.",
                style = MaterialTheme.typography.bodyLarge,
                color = AfterhoursTheme.colors.textMuted,
            )
            else -> Empty()
        }

        if (state.card != null && state.refusing == null && state.offeringHotter == null) {
            HeatPicker(selected = state.heat, onHeat = onHeat)
        }
    }
}

@Composable
private fun Playing(
    card: InstantCard,
    heat: Heat,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onTooFar: () -> Unit,
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
