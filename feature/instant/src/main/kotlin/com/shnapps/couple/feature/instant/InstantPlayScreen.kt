package com.shnapps.couple.feature.instant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.component.raised
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.ProvideHeat
import com.shnapps.couple.core.engine.InstantCard
import com.shnapps.couple.core.model.Heat
import com.shnapps.couple.core.ui.AfterhoursCard
import com.shnapps.couple.core.ui.BarAction
import com.shnapps.couple.core.ui.Dealt
import com.shnapps.couple.core.ui.HeatPicker
import com.shnapps.couple.core.ui.RunProgress
import com.shnapps.couple.core.ui.ScreenScaffold
import com.shnapps.couple.core.ui.SecureScreen
import com.shnapps.couple.core.ui.TimerRing

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
    onKeepThis: () -> Unit,
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
        onKeepThis = onKeepThis,
    )
}

@Suppress("LongParameterList", "CyclomaticComplexMethod") // One screen, one card, every way past it.
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
    onKeepThis: () -> Unit,
) {
    // Everything on this screen takes the run's heat: the card's rim, the primary action,
    // the eyebrow (DESIGN_PROMPT.md §4.4). One setting, and the whole room changes.
    ProvideHeat(state.heat) {
        val playing = state.card != null && state.refusing == null &&
            state.offeringHotter == null && !state.runComplete

        // Typed up front rather than inline: a `when` whose branches are composable lambdas
        // needs the expected type to be stated somewhere, and a slot is easier to read here.
        val trailing: (@Composable () -> Unit)? = if (playing) {
            { BarAction(label = "Too far", icon = AfterhoursIcons.Shield, onClick = onTooFar) }
        } else {
            null
        }
        val belowBar: (@Composable () -> Unit)? = if (playing) {
            // `position` is already the 0-based index of the card on screen. Subtracting one
            // pinned the rail to the first segment for the whole run — found on the device.
            { RunProgress(current = state.position, total = state.runLength) }
        } else {
            null
        }
        val dock: (@Composable ColumnScope.() -> Unit)? = when {
            state.refusing != null || state.offeringHotter != null -> null
            state.runComplete -> {
                {
                    GlowButton(
                        text = "Keep going",
                        onClick = onKeepGoing,
                        leadingIcon = AfterhoursIcons.Flame,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GlowButton(
                        text = "Keep tonight, and find each other",
                        onClick = onKeepThis,
                        style = GlowButtonStyle.Secondary,
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
            playing -> {
                {
                    Row(horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
                        GlowButton(
                            text = "Skip",
                            onClick = onSkip,
                            style = GlowButtonStyle.Secondary,
                            leadingIcon = AfterhoursIcons.Skip,
                            modifier = Modifier.weight(SKIP_WIDTH),
                        )
                        GlowButton(
                            text = "Done",
                            onClick = onDone,
                            leadingIcon = AfterhoursIcons.Check,
                            modifier = Modifier.weight(DONE_WIDTH),
                        )
                    }
                }
            }
            else -> null
        }

        ScreenScaffold(
            title = if (playing) "Tonight" else null,
            onBack = onBack,
            // A safety control belongs in the bar, always reachable and never competing with
            // the thing a couple is actually doing (§6.2).
            trailing = trailing,
            belowBar = belowBar,
            dock = dock,
            // One card, centred in what is left: this screen is an object, not a list.
            centred = true,
        ) {
            when {
                state.refusing != null -> RefusalQuestion(onRefuse = onRefuse)
                state.offeringHotter != null -> HotterQuestion(
                    next = state.offeringHotter,
                    onTurnUp = onTurnUp,
                    onKeep = onKeepHeat,
                )
                state.runComplete -> RunComplete(played = state.played)
                state.card != null -> Playing(
                    card = state.card,
                    heat = state.heat,
                    timer = state.timer,
                    position = state.position,
                    onStartTimer = onStartTimer,
                    onStopTimer = onStopTimer,
                )
                state.loading -> Dealing()
                else -> Empty()
            }

            if (state.showsHeatPicker) {
                HeatPicker(selected = state.heat, onHeat = onHeat)
            }
        }
    }
}

@Composable
private fun Playing(
    card: InstantCard,
    heat: Heat,
    timer: CardTimer?,
    position: Int,
    onStartTimer: () -> Unit,
    onStopTimer: () -> Unit,
) {
    val spacing = AfterhoursTheme.spacing

    // The one moment the app performs: a card being laid down, restarting on each new card.
    Dealt(key = card.id) {
        AfterhoursCard(
            eyebrow = "${heat.label} · ${card.minutes} min",
            title = card.title,
            body = card.body,
            meta = "Whoever's holding the phone",
            index = position,
        )
    }

    // A clock is a shape, not a third call to action (§5.7). It only appears once somebody
    // asked for one, and tapping it is how it stops.
    if (timer == null) {
        GlowButton(
            text = "Start the ${card.minutes} minutes",
            onClick = onStartTimer,
            style = GlowButtonStyle.Quiet,
            leadingIcon = AfterhoursIcons.Clock,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Box(modifier = Modifier.fillMaxWidth().padding(top = spacing.sm), contentAlignment = Alignment.Center) {
            TimerRing(
                remainingSeconds = timer.remainingSeconds,
                totalSeconds = timer.totalSeconds,
                onToggle = onStopTimer,
                running = !timer.finished,
            )
        }
    }
}

/** Dealing. A skeleton in the card's own shape, not the word "Dealing." in grey (§5.8). */
@Composable
private fun Dealing() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SKELETON)
            .raised(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.extraLarge,
            )
            .semantics { contentDescription = "Dealing" },
    )
}

/**
 * An evening does not end on its own. It asks — and this is the first moment an account is worth
 * anything, so it is the first moment one is offered (REDESIGN_PROMPT.md §4.2).
 */
@Composable
private fun RunComplete(played: Int) {
    AfterhoursCard(
        eyebrow = "That's $played",
        title = "Stop here, or keep going?",
        body = "There is plenty left, and an evening does not end on its own.",
        meta = "An account keeps what you saved and lets you play when you're apart. " +
            "What you've said no to comes with you.",
    )
}

/** Seconds as a clock reads them. */
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

/** Skip is narrower than Done on purpose: they are not equal choices. */
private const val SKIP_WIDTH = 0.4f
private const val DONE_WIDTH = 0.6f

/** A dealing skeleton stands in for a card, so the screen does not jump when one arrives. */
private val SKELETON = 240.dp
