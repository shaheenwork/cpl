package com.shnapps.couple.feature.instant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.component.RailAction
import com.shnapps.couple.core.designsystem.component.SafetyRail
import com.shnapps.couple.core.designsystem.component.TurnOverCard
import com.shnapps.couple.core.designsystem.component.raised
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.ProvideTier
import com.shnapps.couple.core.engine.InstantCard
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.ui.AfterhoursCard
import com.shnapps.couple.core.ui.AfterhoursSheet
import com.shnapps.couple.core.ui.Dealt
import com.shnapps.couple.core.ui.HeatChip
import com.shnapps.couple.core.ui.LaneChip
import com.shnapps.couple.core.ui.LanePicker
import com.shnapps.couple.core.ui.LockedLevelCard
import com.shnapps.couple.core.ui.PausedContent
import com.shnapps.couple.core.ui.PausedDock
import com.shnapps.couple.core.ui.RefusalChoices
import com.shnapps.couple.core.ui.RunProgress
import com.shnapps.couple.core.ui.ScreenScaffold
import com.shnapps.couple.core.ui.SecureScreen
import com.shnapps.couple.core.ui.TierPicker
import com.shnapps.couple.core.ui.TimerRing

/**
 * One instruction at a time (REDESIGN_PROMPT.md §5.3).
 *
 * This is the product. Not a night with chapters, not a shape that hides itself, not a progress
 * line — a card, and three things you can do about it: do it, skip it, or say it is not your kind of
 * thing. The last of those is how this app learns a limit, and it costs one tap in the moment
 * instead of twenty cards beforehand.
 *
 * The card arrives face down and is turned over by a tap (D-143). The heat and the lane are chips
 * that open their pickers, rather than two dials under the card; Stop and Too far live on the rail
 * at the bottom, where a thumb already is.
 */
@Composable
fun InstantPlayScreen(
    onBack: () -> Unit,
    onStore: (Intensity) -> Unit = {},
    viewModel: InstantPlayViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    InstantPlayContent(
        state = state,
        actions = DeckActions(
            onBack = onBack,
            onReveal = viewModel::reveal,
            onDone = viewModel::done,
            onSkip = viewModel::skip,
            onTooFar = viewModel::tooFar,
            onRefuse = viewModel::refuse,
            onTurnUp = viewModel::turnUp,
            onKeepHeat = viewModel::keepTier,
            onTier = { tier -> viewModel.turnUp(tier) },
            onLocked = viewModel::tapLocked,
            onDismiss = viewModel::dismissSheet,
            onStore = { state.lockedLevel?.let(onStore) },
            onToggleTimer = viewModel::toggleTimer,
            onKeepGoing = viewModel::keepGoing,
            onLane = viewModel::setLane,
            onStop = viewModel::stop,
            onGentle = viewModel::carryOnGently,
            onOpenSheet = viewModel::showSheet,
            onCloseSheet = { viewModel.showSheet(null) },
        ),
    )
}

/** Every way past a card, gathered so the content is easy to draw in a test. */
internal data class DeckActions(
    val onBack: () -> Unit = {},
    val onReveal: () -> Unit = {},
    val onDone: () -> Unit = {},
    val onSkip: () -> Unit = {},
    val onTooFar: () -> Unit = {},
    val onRefuse: (Boolean) -> Unit = {},
    val onTurnUp: (Intensity) -> Unit = {},
    val onKeepHeat: () -> Unit = {},
    val onTier: (Intensity) -> Unit = {},
    val onLocked: (Intensity) -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onStore: () -> Unit = {},
    val onToggleTimer: () -> Unit = {},
    val onKeepGoing: () -> Unit = {},
    val onLane: (String?) -> Unit = {},
    val onStop: () -> Unit = {},
    val onGentle: () -> Unit = {},
    val onOpenSheet: (DeckSheet) -> Unit = {},
    val onCloseSheet: () -> Unit = {},
)

@Composable
internal fun InstantPlayContent(state: InstantPlayUiState, actions: DeckActions) {
    // Everything on this screen takes the run's tier: the card's rim, the primary action,
    // the eyebrow (DESIGN_PROMPT.md §4.4). One setting, and the whole room changes.
    ProvideTier(state.tier) {
        Box(modifier = Modifier.fillMaxSize()) {
            ScreenScaffold(
                calm = state.stopped,
                title = if (state.playing) "Tonight" else null,
                onBack = actions.onBack,
                trailing = if (state.showsTierPicker) {
                    { HeatChip(tier = state.tier, onClick = { actions.onOpenSheet(DeckSheet.HEAT) }) }
                } else {
                    null
                },
                belowBar = if (state.playing) {
                    { DeckHeader(state = state, actions = actions) }
                } else {
                    null
                },
                dock = dockFor(state, actions),
                // One card, centred in what is left: this screen is an object, not a list.
                centred = true,
                // Stop is on the rail whatever else is on the screen, except once it has been used.
                rail = if (state.stopped) null else { { DeckRail(state = state, actions = actions) } },
            ) {
                DeckBody(state = state, actions = actions)
            }
            DeckSheets(state = state, actions = actions)
        }
    }
}

/** Stop, always; Too far while a card is up. */
@Composable
private fun DeckRail(state: InstantPlayUiState, actions: DeckActions) {
    SafetyRail(onStop = actions.onStop) {
        if (state.playing) RailAction(label = "Too far", icon = AfterhoursIcons.Skip, onClick = actions.onTooFar)
    }
}

/** The heat, the lanes and "too far", each rising over the card it is about. */
@Composable
private fun BoxScope.DeckSheets(state: InstantPlayUiState, actions: DeckActions) {
    AfterhoursSheet(
        visible = state.sheet == DeckSheet.HEAT,
        onDismiss = actions.onCloseSheet,
        title = "How far tonight",
    ) {
        TierPicker(
            selected = state.tier,
            onTier = actions.onTier,
            locked = state.access.locked.toSet(),
            onLocked = { level ->
                actions.onCloseSheet()
                actions.onLocked(level)
            },
        )
    }
    AfterhoursSheet(visible = state.sheet == DeckSheet.LANES, onDismiss = actions.onCloseSheet, title = "A lane") {
        LanePicker(lanes = state.lanes, selected = state.lane, onLane = actions.onLane)
    }
    AfterhoursSheet(visible = state.refusing != null, onDismiss = actions.onDismiss, title = "Too far") {
        RefusalChoices(onRefuse = actions.onRefuse, onKeep = actions.onDismiss)
    }
}

/** Where the run is, and which lane it deals from. */
@Composable
private fun DeckHeader(state: InstantPlayUiState, actions: DeckActions) {
    Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.xs)) {
        // `position` is already the 0-based index of the card on screen. Subtracting one
        // pinned the rail to the first segment for the whole run — found on the device.
        RunProgress(current = state.position, total = state.runLength)
        if (state.lanes.isNotEmpty()) {
            LaneChip(
                label = state.laneLabel,
                icon = AfterhoursIcons.Lanes,
                onClick = { actions.onOpenSheet(DeckSheet.LANES) },
                modifier = Modifier.padding(horizontal = AfterhoursTheme.spacing.gutter),
            )
        }
    }
}

@Composable
private fun DeckBody(state: InstantPlayUiState, actions: DeckActions) {
    when {
        // The stop comes before everything, including a question the screen was asking.
        state.stopped -> PausedContent()
        state.offeringHotter != null -> HotterQuestion(
            next = state.offeringHotter,
            onTurnUp = actions.onTurnUp,
            onKeep = actions.onKeepHeat,
        )
        state.runComplete -> RunComplete(played = state.played, aftercare = state.aftercare)
        state.card != null -> Playing(
            state = state,
            card = state.card,
            onReveal = actions.onReveal,
            onToggleTimer = actions.onToggleTimer,
        )
        state.loading -> Dealing()
        else -> Empty()
    }

    // A level they do not have, tapped. **Not a wall**: the card they were playing is still above
    // it, and "Not now" puts the evening back exactly as it was (V2 rule 5).
    state.lockedLevel?.let { level ->
        LockedLevelCard(
            level = level,
            price = state.lockedPrice,
            onUnlock = actions.onStore,
            onDismiss = actions.onDismiss,
        )
    }
}

/** What the dock offers, which is one question: which state the screen is in. */
private fun dockFor(state: InstantPlayUiState, actions: DeckActions): (@Composable ColumnScope.() -> Unit)? = when {
    // The locked card carries its own two buttons, in the card, where the tap happened.
    state.offeringHotter != null || state.lockedLevel != null -> null
    state.stopped -> {
        { PausedDock(onGentle = actions.onGentle, onEnd = actions.onBack) }
    }
    state.runComplete -> {
        {
            GlowButton(
                text = "Keep going",
                onClick = actions.onKeepGoing,
                leadingIcon = AfterhoursIcons.Flame,
                modifier = Modifier.fillMaxWidth(),
            )
            GlowButton(
                text = "That's us for tonight",
                onClick = actions.onBack,
                style = GlowButtonStyle.Quiet,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    state.card != null -> {
        { PlayingDock(revealed = state.revealed, actions = actions) }
    }
    else -> null
}

@Composable
private fun ColumnScope.PlayingDock(revealed: Boolean, actions: DeckActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
        GlowButton(
            text = "Skip",
            onClick = actions.onSkip,
            style = GlowButtonStyle.Secondary,
            leadingIcon = AfterhoursIcons.Skip,
            modifier = Modifier.weight(SKIP_WIDTH),
        )
        // Done means "we did it", which nobody can say about a card nobody has read.
        GlowButton(
            text = "Done",
            onClick = actions.onDone,
            enabled = revealed,
            leadingIcon = AfterhoursIcons.Check,
            modifier = Modifier.weight(DONE_WIDTH),
        )
    }
    Text(
        text = "Skip is always free — pass, no questions asked.",
        style = MaterialTheme.typography.bodySmall,
        color = AfterhoursTheme.colors.textFaint,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Playing(state: InstantPlayUiState, card: InstantCard, onReveal: () -> Unit, onToggleTimer: () -> Unit) {
    val spacing = AfterhoursTheme.spacing
    val timer = state.timer

    // The one moment the app performs: a card being laid down, restarting on each new card. It
    // lands face down, wearing its own tier, so a run that climbs visibly warms.
    Dealt(key = card.id) {
        ProvideTier(card.intensity) {
            TurnOverCard(
                revealed = state.revealed,
                onReveal = onReveal,
                label = "Card ${(state.position + 1).toString().padStart(2, '0')}",
            ) {
                AfterhoursCard(
                    eyebrow = listOfNotNull(state.laneLabel, card.intensity.label, "${card.minutes} min")
                        .joinToString(" · "),
                    title = card.title,
                    body = card.body,
                    meta = "Whoever's holding the phone",
                    index = state.position,
                )
            }
        }
    }

    // A clock is a shape, not a third call to action (§5.7). It only appears once somebody asked
    // for one, and only for a card that has been read.
    if (timer == null) {
        GlowButton(
            text = "Start the ${card.minutes} minutes",
            onClick = onToggleTimer,
            enabled = state.revealed,
            style = GlowButtonStyle.Quiet,
            leadingIcon = AfterhoursIcons.Clock,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Box(modifier = Modifier.fillMaxWidth().padding(top = spacing.sm), contentAlignment = Alignment.Center) {
            TimerRing(
                remainingSeconds = timer.remainingSeconds,
                totalSeconds = timer.totalSeconds,
                onToggle = onToggleTimer,
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
 * An evening does not end on its own. It asks, and it offers nothing: there is no account to make
 * here and nothing to sign up for (V2 rule 1).
 */
@Composable
private fun RunComplete(played: Int, aftercare: InstantCard?) {
    AfterhoursCard(
        eyebrow = "That's $played",
        title = "Stop here, or keep going?",
        body = "There is plenty left, and an evening does not end on its own.",
        meta = "Nothing is saved anywhere else. What you have said no to stays on this phone.",
    )

    // After a Hot or Extreme run, something written to land on (M7). Offered rather than counted
    // as a card: the run is over, and this is not a seventh of six.
    aftercare?.let { card ->
        AfterhoursCard(
            eyebrow = "Before you put it down",
            title = card.title,
            body = card.body,
            lit = false,
        )
    }
}

/** Offered after a run of yeses, never applied on their behalf. */
@Composable
private fun HotterQuestion(next: Intensity, onTurnUp: (Intensity) -> Unit, onKeep: () -> Unit) {
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
            text = "Nothing left at this tier.",
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
