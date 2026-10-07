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
import com.shnapps.couple.core.model.Gate
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.NightPlan
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.SessionState
import com.shnapps.couple.core.ui.ChoiceRow
import com.shnapps.couple.core.ui.Entrance
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.LockedLevelCard
import com.shnapps.couple.core.ui.PremiumNudge
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.ScreenScaffold
import com.shnapps.couple.core.ui.SecureScreen
import com.shnapps.couple.core.ui.TierPicker
import com.shnapps.couple.core.ui.Wordmark

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
                actions.onOpen(opened.experienceId, opened.type)
            }
        }
    }

    HomeContent(
        state = state,
        actions = actions.copy(
            onPlay = viewModel::play,
            onTier = viewModel::setTier,
            onMode = viewModel::setMode,
            onOpenWaiting = viewModel::openWaiting,
            onDismissError = viewModel::dismissError,
            onDismissGate = viewModel::dismissGate,
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
    val onPaywall: (Gate) -> Unit = {},
    /** A game dealt on this phone, for anyone the server does not know. */
    val onDealtGame: (gameId: String) -> Unit = {},
    /** Something the server built, and the session both phones follow. */
    val onOpen: (experienceId: String, type: ExperienceType) -> Unit = { _, _ -> },
    /** Debug builds only: the component gallery and the content inspector. */
    val onGallery: (() -> Unit)? = null,
    val onInspector: (() -> Unit)? = null,
    val onPlay: (gameId: String) -> Unit = {},
    val onTier: (Intensity) -> Unit = {},
    /** A level they tapped that they have not bought: the card, then the store (V2 rule 5). */
    val onLockedTier: (Intensity) -> Unit = {},
    val onDismissLocked: () -> Unit = {},
    /** The store, opened on a particular pack. */
    val onStore: (Intensity) -> Unit = {},
    /** Together in a room, or apart on two phones: which half of the app this is. */
    val onMode: (Mode) -> Unit = {},
    val onOpenWaiting: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onDismissGate: () -> Unit = {},
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
            // A limit, said where the tap happened — never over an evening that has started (§19).
            state.gate?.let { gate ->
                PremiumNudge(
                    gate = gate,
                    onSeeWhatsIncluded = { actions.onPaywall(gate) },
                    onDismiss = actions.onDismissGate,
                )
            }

            // What is already happening comes before anything that could be started, and it is
            // the one lit object on the screen when it is there.
            state.active?.let { session -> UnderWay(session, actions.onOpen) }
            state.waitingForMe?.let { plan -> SomethingWaiting(plan, state.canStart, actions.onOpenWaiting) }

            // Which half of the app this is, before anything about tonight: together in a room,
            // or apart on two phones. A toggle rather than two home screens, because the app has
            // one home and the brief has one Home.
            Entrance(index = 1) {
                ModeToggle(selected = state.mode, onMode = actions.onMode)
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
            if (state.mode == Mode.APART && !state.company.paired) {
                Entrance(index = 3) { PairFirst(state.company, actions) }
            }

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
                        subtitle = game.tagline,
                        icon = AfterhoursIcons.Spark,
                        onClick = { actions.onPlay(game.id) },
                    )
                }
            }

            actions.onGallery?.let { Quiet("Component gallery", it) }
            actions.onInspector?.let { Quiet("Content inspector", it) }
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
    GlowButton(
        text = if (state.making) "Getting it ready…" else "Play Now",
        onClick = actions.onCards,
        enabled = state.canStart,
        leadingIcon = AfterhoursIcons.Flame,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Two phones, and only one of them here yet (V2 rule 2).
 *
 * Apart is not a second half of the app any more — it is the same games, played a phone each — so
 * the only thing this says is the one thing that is actually missing. Everything below it still
 * works on this phone alone, which is why it is a row and not a wall.
 */
@Composable
private fun PairFirst(company: Company, actions: HomeActions) {
    Section("Two phones") {
        ChoiceRow(
            title = "Pair with their phone",
            subtitle = "Twenty seconds, once. After that these two phones stay a pair.",
            icon = AfterhoursIcons.Spark,
            trailing = if (company.signedIn) "Pair" else null,
            onClick = actions.onPairPartner,
        )
    }
}

/**
 * What their partner planned, unopened (§13, §2.5 #2).
 *
 * The card used to be the whole of it: a question, and nothing that answered it. Both answers are
 * here, and so is what happens if neither is given — which is nothing, because it waits.
 */
@Composable
private fun SomethingWaiting(plan: NightPlan, canOpen: Boolean, onOpen: () -> Unit) {
    CinematicCard(
        modifier = Modifier.fillMaxWidth(),
        glowing = true,
        contentDescription = "They planned an evening for you. ${plan.chapterCount} cards, planned by them.",
    ) {
        Text(text = "FOR YOU", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
        Text(
            text = "They planned an evening for you.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "${plan.chapterCount} cards. That's all either of you knows until you open it.",
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
        )
    }
    GlowButton(text = "Open it", onClick = onOpen, enabled = canOpen, modifier = Modifier.fillMaxWidth())
    Text(
        text = "Nothing happens to it if you leave it. It waits.",
        style = MaterialTheme.typography.bodyMedium,
        color = AfterhoursTheme.colors.textMuted,
    )
}

/** What the couple already has going, from either phone: the way back into it. */
@Composable
private fun UnderWay(session: NightSession, onOpen: (String, ExperienceType) -> Unit) {
    val what = if (session.experienceType == ExperienceType.GAME) "game" else "evening"
    val line = when {
        session.state == SessionState.PAUSED -> "Your $what is paused. Either of you can pick it up."
        session.state == SessionState.WAITING_FOR_PARTNER && !session.consented ->
            "Your $what is waiting for an \"I'm in\"."
        session.isHost -> "Your $what is waiting. Pick up where you left off."
        else -> "A $what is playing on the other phone."
    }
    CinematicCard(modifier = Modifier.fillMaxWidth(), glowing = true, contentDescription = line) {
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

@Composable
private fun Quiet(text: String, onClick: () -> Unit) {
    GlowButton(
        text = text,
        onClick = onClick,
        style = GlowButtonStyle.Quiet,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun HomeError.message(): String = when (this) {
    HomeError.OFFLINE -> "You're offline. That one needs the network."
    HomeError.NOT_ENOUGH_CONTENT -> "Your limits leave too little for an evening right now. " +
        "You can loosen one in Settings."
    HomeError.BUSY -> "You've already got something under way."
    HomeError.OTHER -> "That didn't work. Try again."
}
