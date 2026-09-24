package com.shnapps.couple.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.model.ExperienceType
import com.shnapps.couple.core.model.MediaItem
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.NightPlan
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.SessionState
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.SecureScreen

/**
 * Home (BUILD_PROMPT.md §14.2). It never opens with a questionnaire: it opens with "Tonight
 * could get interesting." and two taps that need no thought at all.
 *
 * One switch drives the surface — the same room, or apart. Apart is built in Phase 14; until
 * then that side says so plainly rather than offering controls that do nothing (§25).
 */
@Composable
fun HomeScreen(
    onNight: (experienceId: String) -> Unit,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.startedExperienceId) {
        state.startedExperienceId?.let {
            viewModel.onShown()
            onNight(it)
        }
    }
    HomeContent(
        state = state,
        actions = actions.copy(
            onSurprise = viewModel::surprise,
            onMode = viewModel::setMode,
            onOpenWaiting = viewModel::openWaiting,
            onDismissError = viewModel::dismissError,
        ),
        modifier = modifier,
    )
}

/** Everywhere home can send you. The screen knows the words; the host knows the routes. */
data class HomeActions(
    val onBuild: () -> Unit = {},
    val onExpress: () -> Unit = {},
    val onGames: () -> Unit = {},
    val onApartNight: () -> Unit = {},
    val onPlanForThem: () -> Unit = {},
    val onSend: () -> Unit = {},
    val onLater: () -> Unit = {},
    val onArc: () -> Unit = {},
    val onCuriosities: () -> Unit = {},
    val onMutual: () -> Unit = {},
    val onLimits: () -> Unit = {},
    val onNotifications: () -> Unit = {},
    val onAppLock: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onUnpair: () -> Unit = {},
    /** Debug builds only: the component gallery and the content inspector. */
    val onGallery: (() -> Unit)? = null,
    val onInspector: (() -> Unit)? = null,
    val onOpen: (experienceId: String, type: ExperienceType) -> Unit = { _, _ -> },
    val onSurprise: (mystery: Boolean) -> Unit = {},
    val onOpenWaiting: () -> Unit = {},
    val onMode: (Mode) -> Unit = {},
    val onDismissError: () -> Unit = {},
)

@Composable
internal fun HomeContent(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ScreenColumn {
            ScreenHeading(eyebrow = "Tonight", headline = "Tonight could get interesting.")
            ModeSwitch(mode = state.mode, onMode = actions.onMode)
            state.error?.let { error -> ErrorBanner(message = error.message(), onDismiss = actions.onDismissError) }
            state.active?.let { session -> UnderWay(session, actions.onOpen) }
            // The strongest thing on this screen when it is there (§2.5 #2).
            state.waitingForMe?.let { plan -> SomethingWaiting(plan, state.canStart, actions.onOpenWaiting) }
            state.unopenedMedia?.let { item -> TheySentSomething(item, actions.onSend) }

            when (state.mode) {
                Mode.TOGETHER -> TogetherHome(state, actions)
                Mode.APART -> ApartHome(state, actions)
            }

            Section("Yours") {
                Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
                    Quiet("Your limits", actions.onLimits)
                    Quiet("Notifications", actions.onNotifications)
                    Quiet("Set up app lock", actions.onAppLock)
                    actions.onGallery?.let { Quiet("Component gallery", it) }
                    actions.onInspector?.let { Quiet("Content inspector", it) }
                    Quiet("Sign out", actions.onSignOut)
                    Quiet("Unpair", actions.onUnpair)
                }
            }
        }
    }
}

/** TOGETHER / APART (§14.2): one prominent switch, and the surface follows it. */
@Composable
private fun ModeSwitch(mode: Mode, onMode: (Mode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
    ) {
        for (option in Mode.entries) {
            GlowButton(
                text = if (option == Mode.TOGETHER) "Together" else "Apart",
                onClick = { onMode(option) },
                style = if (option == mode) GlowButtonStyle.Primary else GlowButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The same room (§14.3): the taps that need no thought first, the ones that ask a little after. */
@Composable
private fun TogetherHome(state: HomeUiState, actions: HomeActions) {
    Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
        GlowButton(
            text = "Build tonight",
            onClick = actions.onBuild,
            leadingEmoji = "🔥",
            enabled = state.canStart,
            modifier = Modifier.fillMaxWidth(),
        )
        GlowButton(
            text = if (state.making) "Deciding…" else "Surprise us",
            onClick = { actions.onSurprise(false) },
            leadingEmoji = "🎲",
            enabled = state.canStart,
            modifier = Modifier.fillMaxWidth(),
        )
        GlowButton(
            text = "Play a game",
            onClick = actions.onGames,
            style = GlowButtonStyle.Secondary,
            leadingEmoji = "🎮",
            enabled = state.canStart,
            modifier = Modifier.fillMaxWidth(),
        )
        GlowButton(
            text = "We have 15 minutes",
            onClick = actions.onExpress,
            style = GlowButtonStyle.Secondary,
            leadingEmoji = "⏱",
            enabled = state.canStart,
            modifier = Modifier.fillMaxWidth(),
        )
        GlowButton(
            text = "Send something",
            onClick = actions.onSend,
            style = GlowButtonStyle.Secondary,
            leadingEmoji = "💌",
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Section("Or let us decide") {
        CinematicCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { if (state.canStart) actions.onSurprise(true) },
            glowing = state.canStart,
            contentDescription = "Mystery night. You'll find out as you go — not how long, not how many.",
        ) {
            Text(text = "🌙 MYSTERY NIGHT", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
            Text(
                text = "You'll find out as you go.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Not how long. Not how many. Just the next thing.",
                style = MaterialTheme.typography.bodyMedium,
                color = AfterhoursTheme.colors.textMuted,
            )
        }
    }
    Section("Between the two of you") {
        Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
            Quiet("Your private curiosities", actions.onCuriosities, "👀")
            Quiet("What you both chose", actions.onMutual)
        }
    }
}

/**
 * Apart (§13): its own way in, not a reskin of the same room. What is here now is a night built
 * for being apart, and one partner planning a night the other finds later (§2.5 #2). Voice,
 * photos, countdowns and multi-day arcs come in the phases that build them.
 */
@Composable
private fun ApartHome(state: HomeUiState, actions: HomeActions) {
    Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
        GlowButton(
            text = "Start tonight",
            onClick = actions.onApartNight,
            leadingEmoji = "🔥",
            enabled = state.canStart,
            modifier = Modifier.fillMaxWidth(),
        )
        GlowButton(
            text = if (state.making) "Deciding…" else "Surprise us",
            onClick = { actions.onSurprise(false) },
            leadingEmoji = "🎲",
            enabled = state.canStart,
            modifier = Modifier.fillMaxWidth(),
        )
        GlowButton(
            text = "Send something",
            onClick = actions.onSend,
            style = GlowButtonStyle.Secondary,
            leadingEmoji = "💌",
            modifier = Modifier.fillMaxWidth(),
        )
        GlowButton(
            text = "Leave something for later",
            onClick = actions.onLater,
            style = GlowButtonStyle.Secondary,
            leadingEmoji = "🎁",
            modifier = Modifier.fillMaxWidth(),
        )
        GlowButton(
            text = "A few days of this",
            onClick = actions.onArc,
            style = GlowButtonStyle.Secondary,
            leadingEmoji = "🌙",
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Section("For them") {
        CinematicCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = actions.onPlanForThem,
            contentDescription = "Plan something for them. They'll only know something is coming.",
        ) {
            Text(text = "👀 PLAN SOMETHING", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
            Text(
                text = "Plan a night they'll find later.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "You choose the shape. They only know something is coming — and you never " +
                    "see it either.",
                style = MaterialTheme.typography.bodyMedium,
                color = AfterhoursTheme.colors.textMuted,
            )
        }
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

/** A voice note or a photo they sent that nobody has opened yet (§13). */
@Composable
private fun TheySentSomething(item: MediaItem, onOpen: () -> Unit) {
    val what = if (item.kind == MediaKind.VOICE) "a voice note" else "a photo"
    CinematicCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        glowing = true,
        contentDescription = "They sent you $what.",
    ) {
        Text(text = "💌 FROM THEM", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
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
}

/** "Something is waiting for you tonight 👀" (§13): what their partner planned, unopened. */
@Composable
private fun SomethingWaiting(plan: NightPlan, canOpen: Boolean, onOpen: () -> Unit) {
    CinematicCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (canOpen) onOpen() },
        glowing = true,
        contentDescription = "Something is waiting for you. ${plan.chapterCount} chapters, planned by them.",
    ) {
        Text(text = "👀 SOMETHING IS WAITING", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
        Text(
            text = "They've planned something for you.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "${plan.chapterCount} chapters. That's all either of you knows until you open it.",
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
        )
    }
}

/** What the couple already has going, from either phone: the way back into it. */
@Composable
private fun UnderWay(session: NightSession, onOpen: (String, ExperienceType) -> Unit) {
    val what = if (session.experienceType == ExperienceType.GAME) "game" else "night"
    val line = when {
        session.state == SessionState.PAUSED -> "Your $what is paused. Either of you can pick it up."
        session.state == SessionState.WAITING_FOR_PARTNER && !session.consented ->
            "Your $what is waiting for an \"I'm in\"."
        session.isHost -> "Your $what is waiting. Pick up where you left off."
        else -> "A $what is playing on the other phone."
    }
    CinematicCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpen(session.experienceId, session.experienceType) },
        glowing = true,
        contentDescription = line,
    ) {
        Text(text = "UNDER WAY", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
        Text(text = line, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
        Text(text = title.uppercase(), style = EyebrowTextStyle, color = AfterhoursTheme.colors.textMuted)
        content()
    }
}

@Composable
private fun Quiet(text: String, onClick: () -> Unit, emoji: String? = null) {
    GlowButton(
        text = text,
        onClick = onClick,
        style = GlowButtonStyle.Quiet,
        leadingEmoji = emoji,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun HomeError.message(): String = when (this) {
    HomeError.OFFLINE -> "You're offline. That one needs the network."
    HomeError.NOT_ENOUGH_CONTENT -> "Your limits leave too little for a night right now. " +
        "You can loosen one in Your limits."
    HomeError.BUSY -> "You've already got something under way."
    HomeError.OTHER -> "That didn't work. Try again."
}
