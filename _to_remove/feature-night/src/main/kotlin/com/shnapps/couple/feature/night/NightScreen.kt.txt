package com.shnapps.couple.feature.night

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.StopPauseBar
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.engine.RemixModifier
import com.shnapps.couple.core.model.BoundaryLevel
import com.shnapps.couple.core.model.ChapterReaction
import com.shnapps.couple.core.model.CloseCheckIn
import com.shnapps.couple.core.model.ReportReason
import com.shnapps.couple.core.ui.ErrorBanner
import com.shnapps.couple.core.ui.SecureScreen

/**
 * The experience player (BUILD_PROMPT.md §14.6): one chapter at a time, the shape up front and
 * never the contents, "Something interesting is coming…" between chapters, and PAUSE and STOP
 * on every screen of a night that is under way (§3.1). A `SecureScreen`: this is the most
 * private thing the app shows.
 */
@Composable
fun NightScreen(
    onHome: () -> Unit,
    onAdjustLimits: () -> Unit,
    onKeepIt: (experienceId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NightViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NightContent(
        state = state,
        actions = NightActions(
            onBegin = viewModel::begin,
            onConsent = viewModel::consent,
            onDecline = viewModel::decline,
            onCountdownDone = viewModel::onCountdownDone,
            onKeepGoing = viewModel::keepGoing,
            onOpen = viewModel::open,
            onDone = viewModel::done,
            onBetweenDone = viewModel::onBetweenDone,
            onNotThisOne = viewModel::notThisOne,
            onKeepOut = viewModel::keepOut,
            onDismissKeepOut = viewModel::dismissKeepOut,
            onDismissNothingElse = viewModel::dismissNothingElse,
            onMakeIt = viewModel::makeIt,
            onDismissRemixFailed = viewModel::dismissRemixFailed,
            onTogglePause = viewModel::togglePause,
            onStop = viewModel::stop,
            onAnswerClose = viewModel::answerClose,
            onToFeedback = viewModel::toFeedback,
            onReact = viewModel::react,
            onSaveFeedback = viewModel::saveFeedback,
            onSkipFeedback = viewModel::skipFeedback,
            onReport = viewModel::report,
            onSendReport = viewModel::sendReport,
            onDismissReport = viewModel::dismissReport,
            onDismissError = viewModel::dismissError,
            onHome = onHome,
            onAdjustLimits = onAdjustLimits,
            onKeepIt = { state.night?.experienceId?.let(onKeepIt) },
        ),
        modifier = modifier,
    )
}

/** Everything the player can ask for, gathered so each stage stays easy to preview. */
internal data class NightActions(
    val onBegin: () -> Unit = {},
    val onConsent: () -> Unit = {},
    val onDecline: () -> Unit = {},
    val onCountdownDone: () -> Unit = {},
    val onKeepGoing: (Int) -> Unit = {},
    val onOpen: (Int) -> Unit = {},
    val onDone: (Int) -> Unit = {},
    val onBetweenDone: (Int) -> Unit = {},
    val onNotThisOne: (Int) -> Unit = {},
    val onKeepOut: (String, BoundaryLevel, Int) -> Unit = { _, _, _ -> },
    val onDismissKeepOut: () -> Unit = {},
    val onDismissNothingElse: () -> Unit = {},
    val onMakeIt: (RemixModifier) -> Unit = {},
    val onDismissRemixFailed: () -> Unit = {},
    val onTogglePause: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onAnswerClose: (CloseCheckIn) -> Unit = {},
    val onToFeedback: () -> Unit = {},
    val onReact: (Int, ChapterReaction) -> Unit = { _, _ -> },
    val onSaveFeedback: () -> Unit = {},
    val onSkipFeedback: () -> Unit = {},
    val onReport: (Int) -> Unit = {},
    val onSendReport: (ReportReason) -> Unit = {},
    val onDismissReport: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onHome: () -> Unit = {},
    val onAdjustLimits: () -> Unit = {},
    /** "Save to Our Memories" at the close of a night (§14.8). */
    val onKeepIt: () -> Unit = {},
)

/** The stages a night is under way in: each carries PAUSE and STOP (§3.1). */
private val NightStage.isLive: Boolean
    get() = this is NightStage.Countdown || this is NightStage.CheckIn || this is NightStage.Chapter ||
        this is NightStage.Between || this is NightStage.Elsewhere

@Composable
internal fun NightContent(state: NightUiState, actions: NightActions, modifier: Modifier = Modifier) {
    val motion = AfterhoursTheme.motion
    Column(modifier = modifier.fillMaxSize()) {
        if (state.stage.isLive && state.chapterCount > 0) ChapterProgress(state)
        state.error?.let { error ->
            Box(modifier = Modifier.padding(horizontal = AfterhoursTheme.spacing.gutter)) {
                ErrorBanner(message = error.message(), onDismiss = actions.onDismissError)
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            // Chapters cross-dissolve, never snap (§15.2). A pause takes the night off the screen
            // entirely — out of sight, out of reach, out of TalkBack — but never its controls.
            AnimatedContent(
                targetState = if (state.paused && state.stage.isLive) null else state.stage,
                transitionSpec = { fadeIn(tween(motion.reveal)) togetherWith fadeOut(tween(motion.slow)) },
                label = "nightStage",
            ) { stage ->
                if (stage == null) PausedVeil() else Stage(stage, state, actions)
            }
        }
        if (state.stage.isLive) {
            StopPauseBar(onPauseToggle = actions.onTogglePause, onStop = actions.onStop, paused = state.paused)
        }
    }
}

@Composable
private fun Stage(stage: NightStage, state: NightUiState, actions: NightActions) {
    when (stage) {
        NightStage.Loading -> LoadingStage()
        NightStage.Missing -> MissingStage(actions.onHome)
        NightStage.Shape -> ShapeStage(state, actions)
        NightStage.Consent -> ConsentStage(state, actions)
        NightStage.Countdown -> CountdownStage(state, actions)
        is NightStage.CheckIn -> CheckInStage(stage.index, state, actions)
        is NightStage.Chapter -> ChapterStage(stage.index, stage.open, state, actions)
        is NightStage.Between -> BetweenStage(stage.nextIndex, actions)
        NightStage.Elsewhere -> ElsewhereStage(state)
        is NightStage.Ended -> EndedStage(stage.reason, actions.onHome)
        NightStage.HowAreWe -> HowAreWeStage(state, actions)
        NightStage.Feedback -> FeedbackStage(state, actions)
        NightStage.Done -> DoneStage(actions.onKeepIt, actions.onHome)
    }
}

/**
 * "Chapter 2 of 6", with the night's shape as a row of marks: done, now, still to come. A
 * Mystery Night counts up instead: which chapter you are on, and never how many there are
 * (§14.2, D-077).
 */
@Composable
private fun ChapterProgress(state: NightUiState) {
    val colors = AfterhoursTheme.colors
    val spacing = AfterhoursTheme.spacing
    val mystery = state.night?.mystery == true
    val current = state.chapterNumber.coerceIn(0, state.chapterCount)
    val said = progressLine(mystery, current, state.chapterCount)
    val shown = progressEyebrow(mystery, current, state.chapterCount)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter, vertical = spacing.md)
            .semantics { contentDescription = said },
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(text = shown, style = EyebrowTextStyle, color = colors.brass)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            // A mystery shows only what has been played: a row of marks would be the shape.
            repeat(if (mystery) current else state.chapterCount) { index ->
                val mark = when {
                    index < current - 1 -> colors.textMuted
                    index == current - 1 -> MaterialTheme.colorScheme.primary
                    else -> colors.textFaint.copy(alpha = FAINT_MARK_ALPHA)
                }
                Box(
                    modifier = Modifier
                        .size(spacing.sm)
                        .background(mark, CircleShape),
                )
            }
        }
    }
}

/** What the progress says to TalkBack. A mystery never says how many there are (D-077). */
private fun progressLine(mystery: Boolean, current: Int, count: Int): String = when {
    mystery && current == 0 -> "Tonight"
    mystery -> "Card $current"
    current == 0 -> "Tonight has $count cards"
    else -> "Card $current of $count"
}

/** The same, as the eyebrow above the marks. */
private fun progressEyebrow(mystery: Boolean, current: Int, count: Int): String = when {
    mystery && current == 0 -> "TONIGHT"
    mystery -> "CHAPTER $current"
    current == 0 -> "TONIGHT · $count CHAPTERS"
    else -> "CHAPTER $current OF $count"
}

/**
 * Paused, for both phones: everything waits, and nothing is lost (§3.1). The night itself is
 * off the screen — a pause is often someone at the door.
 */
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

private fun NightError.message(): String = when (this) {
    NightError.OFFLINE -> "We couldn't reach the server. Your evening is waiting right where you left it."
    NightError.MOVED_ON -> "Your evening moved on from the other phone. Here's where it is now."
    NightError.OTHER -> "Something went wrong. Try that again."
}

private const val FAINT_MARK_ALPHA = 0.5f
