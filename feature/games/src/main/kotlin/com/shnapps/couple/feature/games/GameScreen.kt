package com.shnapps.couple.feature.games

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.StopPauseBar
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.engine.game.Answer
import com.shnapps.couple.core.model.CloseCheckIn
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.RunProgress
import com.shnapps.couple.core.ui.SecureScreen

/**
 * A game being played (BUILD_PROMPT.md §11): one phone passed between the partners, each
 * private answer behind a hand-off, and PAUSE and STOP on every screen of a game under way
 * (§3.1). A `SecureScreen`: what either partner answers is shown here and nowhere else.
 */
@Composable
fun GameScreen(
    onGames: () -> Unit,
    onHome: () -> Unit,
    onAdjustLimits: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GameViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GameContent(
        state = state,
        actions = GameActions(
            onOpen = viewModel::open,
            onConsent = viewModel::consent,
            onDecline = viewModel::decline,
            onPlay = viewModel::play,
            onProceed = viewModel::proceed,
            onSubmit = viewModel::submit,
            onSkip = viewModel::skip,
            onKeepOut = viewModel::keepOut,
            onDismissKeepOut = viewModel::dismissKeepOut,
            onNextRound = viewModel::nextRound,
            onTogglePause = viewModel::togglePause,
            onStop = viewModel::stop,
            onAnswerClose = viewModel::answerClose,
            onToResults = viewModel::toResults,
            onDismissError = viewModel::dismissError,
            onGames = onGames,
            onHome = onHome,
            onAdjustLimits = onAdjustLimits,
        ),
        modifier = modifier,
    )
}

/** Everything the player can ask for, gathered so each stage stays easy to preview. */
internal data class GameActions(
    val onOpen: () -> Unit = {},
    val onConsent: () -> Unit = {},
    val onDecline: () -> Unit = {},
    val onPlay: () -> Unit = {},
    val onProceed: () -> Unit = {},
    val onSubmit: (Answer) -> Unit = {},
    val onSkip: () -> Unit = {},
    val onKeepOut: (String) -> Unit = {},
    val onDismissKeepOut: () -> Unit = {},
    val onNextRound: () -> Unit = {},
    val onTogglePause: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onAnswerClose: (CloseCheckIn) -> Unit = {},
    val onToResults: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onGames: () -> Unit = {},
    val onHome: () -> Unit = {},
    val onAdjustLimits: () -> Unit = {},
)

@Composable
internal fun GameContent(state: GameUiState, actions: GameActions, modifier: Modifier = Modifier) {
    val motion = AfterhoursTheme.motion
    val live = GameStages.isLive(state.stage)
    Column(modifier = modifier.fillMaxSize()) {
        if (live && state.roundCount > 0) RoundProgress(state)
        state.error?.let { error ->
            Box(modifier = Modifier.padding(horizontal = AfterhoursTheme.spacing.gutter)) {
                ErrorBanner(message = error.message(), onDismiss = actions.onDismissError)
            }
        }
        // The server's state is the truth; while this phone cannot hear it, say so and keep
        // the last thing it said on screen (§12.3).
        if (state.reconnecting || state.partnerAway) {
            Text(
                text = if (state.reconnecting) "Reconnecting…" else "Your partner dropped out — waiting…",
                style = MaterialTheme.typography.bodyMedium,
                color = AfterhoursTheme.colors.brass,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AfterhoursTheme.spacing.gutter, vertical = AfterhoursTheme.spacing.xs),
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            // A pause takes the game off the screen entirely — out of sight, out of reach, out of
            // TalkBack — but never its controls.
            AnimatedContent(
                targetState = if (state.paused && live) null else state.stage,
                transitionSpec = { fadeIn(tween(motion.reveal)) togetherWith fadeOut(tween(motion.slow)) },
                label = "gameStage",
            ) { stage ->
                if (stage == null) PausedVeil() else Stage(stage, state, actions)
            }
        }
        if (live) StopPauseBar(onPauseToggle = actions.onTogglePause, onStop = actions.onStop, paused = state.paused)
    }
}

@Composable
private fun Stage(stage: GameStage, state: GameUiState, actions: GameActions) {
    when (stage) {
        GameStage.Loading -> LoadingStage()
        GameStage.Missing -> MissingStage(actions)
        GameStage.Intro -> IntroStage(state, actions)
        GameStage.Consent -> ConsentStage(state, actions)
        GameStage.Ready -> ReadyStage(state, actions)
        is GameStage.Playing -> PlayingStage(stage.moment, state, actions)
        GameStage.Elsewhere -> ElsewhereStage()
        is GameStage.Ended -> EndedStage(stage.reason, actions)
        GameStage.HowAreWe -> HowAreWeStage(state, actions)
        GameStage.Results -> ResultsStage(state, actions)
    }
}

/**
 * Where the game is (DESIGN_PROMPT.md §5.6).
 *
 * The same rail the card player uses, so a game and an evening move along the same way. The
 * game's name sits above it, because unlike an evening a game has one.
 */
@Composable
private fun RoundProgress(state: GameUiState) {
    val spacing = AfterhoursTheme.spacing
    Column(modifier = Modifier.padding(top = spacing.sm)) {
        state.started?.released?.game?.title?.let { name ->
            Text(
                text = name.uppercase(),
                style = EyebrowTextStyle,
                color = AfterhoursTheme.colors.textFaint,
                modifier = Modifier.padding(horizontal = spacing.gutter),
            )
        }
        RunProgress(current = state.roundNumber - 1, total = state.roundCount)
    }
}

/** Paused, for both phones: everything waits, and nothing is lost (§3.1). */
@Composable
private fun PausedVeil() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = AfterhoursTheme.spacing.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
        ) {
            Text(
                text = "Paused.",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Take all the time you need. Either of you can carry on, or stop.",
                style = MaterialTheme.typography.bodyLarge,
                color = AfterhoursTheme.colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun GameError.message(): String = when (this) {
    GameError.OFFLINE -> "We couldn't reach the server. Your game is waiting right where you left it."
    GameError.MOVED_ON -> "Your game moved on from the other phone. Here's where it is now."
    GameError.OTHER -> "Something went wrong. Try that again."
}
