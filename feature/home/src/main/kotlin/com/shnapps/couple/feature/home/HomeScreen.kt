package com.shnapps.couple.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.designsystem.theme.ProvideTier
import com.shnapps.couple.core.model.ExperienceType
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.SessionState
import com.shnapps.couple.core.ui.ChoiceRow
import com.shnapps.couple.core.ui.Entrance
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.FreeRunBanner
import com.shnapps.couple.core.ui.LockedLevelCard
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.ScreenScaffold
import com.shnapps.couple.core.ui.SecureScreen
import com.shnapps.couple.core.ui.StatusCard
import com.shnapps.couple.core.ui.TierPicker
import com.shnapps.couple.core.ui.Wordmark
import com.shnapps.couple.core.ui.rememberNudge

@Composable
fun HomeScreen(
    actions: HomeActions,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.opened) {
        when (val opened = state.opened) {
            null -> Unit
            is Opened.Dealt -> {
                viewModel.onShown()
                actions.onDealtGame(opened.gameId)
            }
            is Opened.Session -> {
                viewModel.onShown()
                actions.onOpen(opened.experienceId, opened.type, opened.twoPhones)
            }
        }
    }

    HomeContent(
        state = state,
        actions = actions.copy(
            onPlay = viewModel::play,
            onTier = viewModel::setTier,
            onMode = viewModel::setMode,
            onDismissError = viewModel::dismissError,
            onLockedTier = viewModel::tapLocked,
            onDismissLocked = viewModel::dismissLocked,
        ),
        modifier = modifier,
    )
}

/** Everywhere home can send you. The screen knows the words; the host knows the routes. */
data class HomeActions(
    /** The cards: the shortest way into an evening, and the one that needs nothing at all. */
    val onCards: () -> Unit = {},
    /** A guided evening: warm-up, build, peak, aftercare (M5). */
    val onGameNight: () -> Unit = {},
    val onSettings: () -> Unit = {},
    /** Signed in, but nobody joined yet: the pairing handshake. */
    val onPairPartner: () -> Unit = {},
    /** A game dealt on this phone, for anyone the server does not know. */
    val onDealtGame: (gameId: String) -> Unit = {},
    /** Something the server built, and the session both phones follow. */
    val onOpen: (experienceId: String, type: ExperienceType, twoPhones: Boolean) -> Unit = { _, _, _ -> },
    val onPlay: (gameId: String) -> Unit = {},
    val onTier: (Intensity) -> Unit = {},
    /** A level they tapped that they have not bought: the card, then the store (V2 rule 5). */
    val onLockedTier: (Intensity) -> Unit = {},
    val onDismissLocked: () -> Unit = {},
    /** The store, opened on a particular pack. */
    val onStore: (Intensity) -> Unit = {},
    /** Together in a room, or apart on two phones: which half of the app this is. */
    val onMode: (Mode) -> Unit = {},
    val onDismissError: () -> Unit = {},
)

@Composable
internal fun HomeContent(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    // Everything on this screen takes tonight's tier (DESIGN_PROMPT.md §4.4).
    ProvideTier(state.tier) {
        // The one primary action docks, so it is in reach whatever the list below has grown to.
        // It used to sit at the top of a column that scrolled past it.
        val dock: (@Composable ColumnScope.() -> Unit) = { Start(state = state, actions = actions) }

        ScreenScaffold(
            modifier = modifier,
            trailing = {
                // Material's IconButton is 40 dp; a target is 48 (§20).
                IconButton(
                    onClick = actions.onSettings,
                    modifier = Modifier.size(AfterhoursTheme.spacing.touchTarget),
                ) {
                    Icon(
                        imageVector = AfterhoursIcons.Settings,
                        contentDescription = "Settings",
                        tint = AfterhoursTheme.colors.textMuted,
                    )
                }
            },
            belowBar = { Wordmark(modifier = Modifier.padding(horizontal = AfterhoursTheme.spacing.gutter)) },
            dock = dock,
        ) {
            Entrance(index = 0) {
                ScreenHeading(
                    eyebrow = if (state.company.paired) "Tonight, the two of you" else "Tonight",
                    headline = if (state.mode == Mode.TOGETHER) {
                        "One phone, both of you."
                    } else {
                        "Two phones, one evening."
                    },
                )
            }

            state.error?.let { error -> ErrorBanner(message = error.message(), onDismiss = actions.onDismissError) }

            // Everything is open, and when that stops. A date, never a countdown (V2 rule 5).
            state.access.freeUntilMillis?.let { until ->
                Entrance(index = 1) { FreeRunBanner(untilMillis = until) }
            }

            // What is already happening comes before anything that could be started, and it is
            // the one lit object on the screen when it is there.
            state.active?.let { session ->
                UnderWay(session) { id, type -> actions.onOpen(id, type, session.kind.isTwoPhones) }
            }

            // Which half of the app this is, before anything about tonight: together in a room,
            // or apart on two phones. A toggle rather than two home screens, because the app has
            // one home and the brief has one Home.
            Entrance(index = 1) {
                ModeToggle(selected = state.mode, onMode = actions.onMode)
            }

            // What the toggle actually does, said under it. It decides whether a game opens on one
            // phone or on both, and a couple who paired two phones and were handed one of them had
            // no way of knowing that from the two words above (§12).
            Entrance(index = 1) {
                Text(
                    text = state.mode.describesPlay,
                    style = MaterialTheme.typography.bodySmall,
                    color = AfterhoursTheme.colors.textFaint,
                )
            }

            // Apart with nobody paired is not a screen with a warning on it: there is nothing to play
            // a phone each *with* yet. The heat and the games wait for the pair (D-146).
            if (state.needsPairing) {
                Entrance(index = 2) { NeedsPairing(onPair = actions.onPairPartner) }
                return@ScreenScaffold
            }

            // Apart says what it needs of them, in one line under the control it belongs to: these
            // two phones are a pair, and they are (V2 rule 2).
            if (state.mode == Mode.APART) {
                Entrance(index = 1) { PairedLine() }
            }

            // How far tonight goes belongs with the room it sets, directly under the toggle.
            Entrance(index = 2) {
                TierPicker(
                    selected = state.tier,
                    onTier = actions.onTier,
                    locked = state.access.locked.toSet(),
                    onLocked = actions.onLockedTier,
                )
            }

            // What a locked level costs, said where the tap happened and nowhere else.
            state.lockedLevel?.let { level ->
                Entrance(index = 2) {
                    LockedLevelCard(
                        level = level,
                        price = state.lockedPrice,
                        onUnlock = { actions.onStore(level) },
                        onDismiss = actions.onDismissLocked,
                    )
                }
            }

            // One grid, both modes. The toggle says how a game is played — one phone between
            // them, or a phone each — not which half of an app they are in (V2 rule 3): there is
            // no second surface any more, so there is nothing else for it to switch to.
            Section("Or play something", index = 3) {
                // Game Night is first and is not one of the games in the catalogue: it is a plan
                // over the whole of it, so it has no entry in games.json (M5).
                ChoiceRow(
                    title = "Game Night",
                    subtitle = "A guided evening: warm up, build, peak, then land.",
                    icon = AfterhoursIcons.Clock,
                    onClick = actions.onGameNight,
                )
                state.games.forEach { game ->
                    ChoiceRow(
                        title = game.title,
                        // Every game the app ships can be played a phone each now. The note stays
                        // for the next one that cannot: a round where something is private and
                        // only one of them is asked would strand the other phone (§12).
                        subtitle = if (state.mode == Mode.APART && !game.playableApart) {
                            "${game.tagline} · One phone between you"
                        } else {
                            game.tagline
                        },
                        icon = AfterhoursIcons.Spark,
                        onClick = { actions.onPlay(game.id) },
                    )
                }
            }
        }
    }
}

/**
 * Together in a room, or apart on two phones.
 *
 * The app used to show both at once — an Apart section on a Together home — on the reasoning that a
 * mode switch hides half the app behind a control nobody presses. That was right about the old
 * switch, which replaced the whole surface. This one changes what the list below is and nothing
 * else: the toggle, the tier and Play Now stay where they are.
 */
@Composable
private fun ModeToggle(selected: Mode, onMode: (Mode) -> Unit) {
    val spacing = AfterhoursTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(spacing.xs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Mode.entries.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = spacing.touchTarget)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(if (isSelected) AfterhoursTheme.tier.container else Color.Transparent)
                    .selectable(selected = isSelected, onClick = { onMode(mode) }, role = Role.RadioButton)
                    .semantics { contentDescription = mode.label },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) AfterhoursTheme.tier.onContainer else AfterhoursTheme.colors.textFaint,
                )
            }
        }
    }
}

/**
 * Play Now (the brief's zero-friction rule).
 *
 * One button, and it deals a card. It used to open a sheet of five shapes — cards, surprise us, a
 * mystery, fifteen minutes, plan the evening — which meant a person arriving at eleven at night had
 * to make a decision before anything happened. Four of those five needed an account, so for anyone
 * who had not signed up the sheet was one real choice and four locked rows.
 *
 * The first card is now two taps from opening the app: this, and whatever the card says.
 */
@Composable
private fun Start(state: HomeUiState, actions: HomeActions) {
    // Apart, with nobody to be apart with: the button that would deal a card says what is missing
    // instead, and does the one thing that fixes it.
    if (state.needsPairing) {
        GlowButton(
            text = "Pair to play apart",
            onClick = actions.onPairPartner,
            leadingIcon = AfterhoursIcons.Link,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    GlowButton(
        text = if (state.making) "Getting it ready…" else "Play Now",
        onClick = actions.onCards,
        enabled = state.canStart,
        leadingIcon = AfterhoursIcons.Flame,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Apart, with a pair: said under the toggle that asks it, in one line (V2 rule 2). */
@Composable
private fun PairedLine() {
    Text(
        text = "Paired with their phone.",
        style = MaterialTheme.typography.bodyMedium,
        color = AfterhoursTheme.colors.textMuted,
    )
}

/**
 * What Apart is waiting for (D-146).
 *
 * Not a line and a quiet word any more: with nobody paired, the games and the heat are not shown,
 * because a phone each needs the other phone and there is no one-phone way to play it. The button in
 * the dock says the same thing; this says what pairing is, and that it happens once.
 */
@Composable
private fun NeedsPairing(onPair: () -> Unit) {
    StatusCard(
        icon = AfterhoursIcons.Phones,
        title = "Apart needs both phones",
        body = "Pair once, and every game opens on both. Until then, Together is one tap away.",
    ) {
        GlowButton(
            text = "Pair your phones",
            onClick = onPair,
            style = GlowButtonStyle.Secondary,
            leadingIcon = AfterhoursIcons.Link,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** What the couple already has going, from either phone: the way back into it. */
@Composable
private fun UnderWay(session: NightSession, onOpen: (String, ExperienceType) -> Unit) {
    val what = if (session.experienceType == ExperienceType.GAME) "game" else "evening"
    // Theirs, and waiting on this phone. The one case worth interrupting somebody for: they did
    // not start it, they may not be holding the phone, and nothing else on this screen moves.
    val theirs = !session.isHost
    val line = when {
        session.state == SessionState.PAUSED -> "Your $what is paused. Either of you can pick it up."
        session.state == SessionState.WAITING_FOR_PARTNER && !session.consented ->
            "Your $what is waiting for an \"I'm in\"."
        session.isHost -> "Your $what is waiting. Pick up where you left off."
        else -> "They've started a $what. It's waiting for you."
    }

    val nudge = rememberNudge()
    // Once, when it appears — not on every recomposition, and not again while it sits there.
    LaunchedEffect(session.experienceId, theirs) {
        if (theirs) nudge()
    }

    CinematicCard(
        modifier = Modifier.fillMaxWidth(),
        glowing = true,
        pulsing = theirs,
        contentDescription = line,
    ) {
        Text(text = "UNDER WAY", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
        Text(text = line, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
    GlowButton(
        text = "Pick it up",
        onClick = { onOpen(session.experienceId, session.experienceType) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Section(title: String, index: Int = 0, content: @Composable () -> Unit) {
    val spacing = AfterhoursTheme.spacing
    // A heading belongs to what follows it: `tight` below, and the `group` the scaffold already
    // puts between children above. Equal gaps on both sides is what made every screen look
    // unfinished (DESIGN_PROMPT.md §4.6).
    Entrance(index = index, modifier = Modifier.padding(top = spacing.item)) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.tight)) {
            Text(text = title.uppercase(), style = EyebrowTextStyle, color = AfterhoursTheme.colors.textFaint)
            Column(verticalArrangement = Arrangement.spacedBy(spacing.item)) { content() }
        }
    }
}

private fun HomeError.message(): String = when (this) {
    HomeError.OFFLINE -> "You're offline. That one needs the network."
    HomeError.NOT_ENOUGH_CONTENT -> "Your limits leave too little for an evening right now. " +
        "You can loosen one in Settings."
    HomeError.BUSY -> "You've already got something under way."
    HomeError.OTHER -> "That didn't work. Try again."
}
