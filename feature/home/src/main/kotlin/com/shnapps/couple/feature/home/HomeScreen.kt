package com.shnapps.couple.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.ExperienceType
import com.shnapps.couple.core.model.Gate
import com.shnapps.couple.core.model.Heat
import com.shnapps.couple.core.model.MediaItem
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.NightPlan
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.SessionState
import com.shnapps.couple.core.ui.ApartThread
import com.shnapps.couple.core.ui.ChoiceRow
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.HeatPicker
import com.shnapps.couple.core.ui.PremiumNudge
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.SecureScreen

/**
 * Home — the only one (REDESIGN_PROMPT.md §6).
 *
 * One heading, one filled button, one list of games, one heat. Everything a person could be asked
 * is behind the button; everything an account would add is on the screen from the first minute,
 * saying what it needs rather than appearing later on a screen they have never seen.
 */
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
            onSurprise = viewModel::surprise,
            onHeat = viewModel::setHeat,
            onOpenWaiting = viewModel::openWaiting,
            onDismissError = viewModel::dismissError,
            onDismissGate = viewModel::dismissGate,
        ),
        modifier = modifier,
    )
}

/** Everywhere home can send you. The screen knows the words; the host knows the routes. */
data class HomeActions(
    /** The cards: the shortest way into an evening, and the one that needs nothing at all. */
    val onCards: () -> Unit = {},
    /** Plan the evening: the old six-field form, now one choice inside Start. */
    val onPlan: () -> Unit = {},
    val onExpress: () -> Unit = {},
    val onApart: () -> Unit = {},
    val onPlanForThem: () -> Unit = {},
    val onMemories: () -> Unit = {},
    val onCuriosities: () -> Unit = {},
    val onSettings: () -> Unit = {},
    /** Signed in, but nobody joined yet: the pairing handshake. */
    val onPairPartner: () -> Unit = {},
    /** Something that needs an account, tapped. It says what it buys before it asks. */
    val onAccount: () -> Unit = {},
    val onPaywall: (Gate) -> Unit = {},
    /** A game dealt on this phone, for anyone the server does not know. */
    val onDealtGame: (gameId: String) -> Unit = {},
    /** Something the server built, and the session both phones follow. */
    val onOpen: (experienceId: String, type: ExperienceType) -> Unit = { _, _ -> },
    /** Debug builds only: the component gallery and the content inspector. */
    val onGallery: (() -> Unit)? = null,
    val onInspector: (() -> Unit)? = null,
    val onPlay: (gameId: String) -> Unit = {},
    val onSurprise: (mystery: Boolean) -> Unit = {},
    val onHeat: (Heat) -> Unit = {},
    val onOpenWaiting: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onDismissGate: () -> Unit = {},
)

@Composable
internal fun HomeContent(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    // The one way to start, opened. Screen state, because it is a question this screen asked.
    var choosing by remember { mutableStateOf(false) }

    ScreenColumn(modifier = modifier) {
        ScreenHeading(
            eyebrow = if (state.company.paired) "Tonight, the two of you" else "Tonight",
            headline = "Pick something. Or let us pick.",
            body = "One phone between you, passed back and forth.",
        )

        state.error?.let { error -> ErrorBanner(message = error.message(), onDismiss = actions.onDismissError) }
        // A limit, said where the tap happened — never over an evening that has started (§19).
        state.gate?.let { gate ->
            PremiumNudge(
                gate = gate,
                onSeeWhatsIncluded = { actions.onPaywall(gate) },
                onDismiss = actions.onDismissGate,
            )
        }

        // What is already happening comes before anything that could be started.
        state.active?.let { session -> UnderWay(session, actions.onOpen) }
        state.waitingForMe?.let { plan -> SomethingWaiting(plan, state.canStart, actions.onOpenWaiting) }
        state.unopenedMedia?.let { item -> TheySentSomething(item, actions.onApart) }

        Start(state = state, choosing = choosing, onChoosing = { choosing = it }, actions = actions)

        Section("How far tonight") {
            HeatPicker(selected = state.heat, onHeat = actions.onHeat)
            Text(
                text = "Change it any time, mid-game, with one thumb.",
                style = MaterialTheme.typography.bodyMedium,
                color = AfterhoursTheme.colors.textMuted,
            )
        }

        Apart(state, actions)

        Section("Or play something") {
            state.games.forEach { game ->
                ChoiceRow(
                    title = game.title,
                    subtitle = game.tagline,
                    onClick = { actions.onPlay(game.id) },
                )
            }
        }

        GlowButton(
            text = "Settings",
            onClick = actions.onSettings,
            style = GlowButtonStyle.Quiet,
            modifier = Modifier.fillMaxWidth(),
        )
        actions.onGallery?.let { Quiet("Component gallery", it) }
        actions.onInspector?.let { Quiet("Content inspector", it) }
    }
}

/**
 * One way to start, and every shape of an evening behind it (REDESIGN_PROMPT.md §6).
 *
 * There were five buttons here, two of them loud, and a card underneath that was a sixth. A person
 * arriving at eleven at night had to decide between "Build tonight", "Surprise us", "Play a game",
 * "We have 15 minutes" and "Mystery Night" before anything had happened. Now there is one button,
 * and the five are what it opens.
 */
@Composable
private fun Start(state: HomeUiState, choosing: Boolean, onChoosing: (Boolean) -> Unit, actions: HomeActions) {
    val signedIn = state.company.signedIn

    GlowButton(
        text = when {
            state.making -> "Getting it ready…"
            choosing -> "Never mind"
            else -> "Start now"
        },
        onClick = { onChoosing(!choosing) },
        // Once the choices are open they are the decision; nothing should shout over them.
        style = if (choosing) GlowButtonStyle.Secondary else GlowButtonStyle.Primary,
        enabled = state.canStart,
        modifier = Modifier.fillMaxWidth(),
    )

    AnimatedVisibility(visible = choosing && state.canStart) {
        CinematicCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "HOW DO YOU WANT IT?", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
            // The one that needs nothing at all goes first, for everyone: it is the fastest way
            // from opening the app to doing something.
            ChoiceRow(
                title = "Just deal us cards",
                subtitle = "One at a time. Do it, skip it, or say it went too far.",
                trailing = "Deal",
                onClick = actions.onCards,
            )
            ChoiceRow(
                title = "Surprise us",
                subtitle = "We choose everything — the shape, the length, all of it.",
                trailing = "Go",
                locked = !signedIn,
                onClick = if (signedIn) {
                    { actions.onSurprise(false) }
                } else {
                    actions.onAccount
                },
            )
            ChoiceRow(
                title = "A mystery",
                subtitle = "The same, and we don't tell you the shape.",
                trailing = "Go",
                locked = !signedIn,
                onClick = if (signedIn) {
                    { actions.onSurprise(true) }
                } else {
                    actions.onAccount
                },
            )
            ChoiceRow(
                title = "We have 15 minutes",
                subtitle = "Short, and it knows it.",
                trailing = "Go",
                locked = !signedIn,
                onClick = if (signedIn) actions.onExpress else actions.onAccount,
            )
            ChoiceRow(
                title = "Plan the evening",
                subtitle = "Three questions, then we build it around them.",
                trailing = "Plan",
                locked = !signedIn,
                onClick = if (signedIn) actions.onPlan else actions.onAccount,
            )
        }
    }
}

/**
 * When they are not together (§13) — on the screen from the first minute.
 *
 * It used to be a mode switch that replaced the whole surface, so half the app was behind a control
 * nobody pressed. It is a section now, and it says what it needs of you rather than hiding.
 */
@Composable
private fun Apart(state: HomeUiState, actions: HomeActions) {
    Section(if (state.threadWaiting > 0) "Waiting for you" else "When you're not together") {
        when (state.company) {
            Company.ALONE -> ChoiceRow(
                title = "Apart",
                subtitle = "Send them something, leave something for later, count down to a date.",
                locked = true,
                onClick = actions.onAccount,
            )
            Company.SIGNED_IN -> ChoiceRow(
                title = "Apart",
                subtitle = "Connect your partner, and all of this works between you.",
                trailing = "Connect",
                onClick = actions.onPairPartner,
            )
            Company.PAIRED -> {
                ChoiceRow(
                    title = "Apart",
                    subtitle = "What you sent, what they left, what is still sealed.",
                    trailing = "Open",
                    onClick = actions.onApart,
                )
                ChoiceRow(
                    title = "Plan one for them",
                    subtitle = "You choose the shape. They only know something is coming.",
                    trailing = "Plan",
                    onClick = actions.onPlanForThem,
                )
            }
        }

        ChoiceRow(
            title = "Our memories",
            subtitle = if (state.company.signedIn) {
                "The evenings you kept, in your own words."
            } else {
                "Keeping an evening, and finding each other on a new phone."
            },
            trailing = "Open",
            locked = !state.company.signedIn,
            onClick = if (state.company.signedIn) actions.onMemories else actions.onAccount,
        )

        if (state.company.paired) {
            Curiosities(answered = state.answered, onPlay = actions.onCuriosities)
            if (state.thread.isNotEmpty()) ApartThread(entries = state.thread, onOpen = { actions.onApart() })
            state.plannedByMe?.let { plan ->
                Text(
                    text = if (plan.opened) {
                        "They've opened what you planned."
                    } else {
                        "You've planned something. They haven't found it yet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = AfterhoursTheme.colors.brass,
                )
            }
        }
    }
}

/**
 * The private deck, offered rather than demanded (REDESIGN_PROMPT.md §4.2).
 *
 * It used to run straight after pairing: twenty cards before a couple had done anything together.
 * The engine builds an evening with none of them answered and a better one with some, so this is a
 * thing to play when they feel like it — and it says where they are rather than how far they have
 * to go, because there is no end anyone has to reach.
 */
@Composable
private fun Curiosities(answered: Int, onPlay: () -> Unit) {
    ChoiceRow(
        title = if (answered == 0) "What are you curious about?" else "More you might be curious about",
        subtitle = when (answered) {
            0 -> "A few cards, in private. They never see them — only what you both said yes to."
            1 -> "One answered so far. Every one makes tonight a little more yours."
            else -> "$answered answered so far. Every one makes tonight a little more yours."
        },
        trailing = "Play",
        onClick = onPlay,
    )
}

/** A voice note or a photo they sent that nobody has opened yet (§13). */
@Composable
private fun TheySentSomething(item: MediaItem, onOpen: () -> Unit) {
    val what = if (item.kind == MediaKind.VOICE) "a voice note" else "a photo"
    CinematicCard(
        modifier = Modifier.fillMaxWidth(),
        glowing = true,
        contentDescription = "They sent you $what.",
    ) {
        Text(text = "FROM THEM", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
        Text(
            text = "They sent you $what.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (item.burnAfterView) {
            Text(
                text = "Once only — it goes when you open it.",
                style = MaterialTheme.typography.bodyMedium,
                color = AfterhoursTheme.colors.textMuted,
            )
        }
    }
    GlowButton(text = "Open it", onClick = onOpen, modifier = Modifier.fillMaxWidth())
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
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
        Text(text = title.uppercase(), style = EyebrowTextStyle, color = AfterhoursTheme.colors.textFaint)
        content()
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
