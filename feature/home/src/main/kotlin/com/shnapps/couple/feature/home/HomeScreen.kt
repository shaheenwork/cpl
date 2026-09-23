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
import com.shnapps.couple.core.model.Mode
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
    val onCuriosities: () -> Unit = {},
    val onMutual: () -> Unit = {},
    val onLimits: () -> Unit = {},
    val onAppLock: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onUnpair: () -> Unit = {},
    /** Debug builds only: the component gallery and the content inspector. */
    val onGallery: (() -> Unit)? = null,
    val onInspector: (() -> Unit)? = null,
    val onOpen: (experienceId: String, type: ExperienceType) -> Unit = { _, _ -> },
    val onSurprise: (mystery: Boolean) -> Unit = {},
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

            when (state.mode) {
                Mode.TOGETHER -> TogetherHome(state, actions)
                Mode.APART -> ApartHome()
            }

            Section("Yours") {
                Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
                    Quiet("Your limits", actions.onLimits)
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

/** Apart is a first-class mode, built in Phase 14 — not a reskin, and not a dead button (§2.6). */
@Composable
private fun ApartHome() {
    Section("Apart") {
        CinematicCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "SOON", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
            Text(
                text = "Apart is being built.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Voice notes, surprises that wait for them, countdowns and multi-day arcs. " +
                    "For now, everything here is for the same room.",
                style = MaterialTheme.typography.bodyMedium,
                color = AfterhoursTheme.colors.textMuted,
            )
        }
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
