package com.shnapps.couple.feature.games

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.component.raised
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.ProvideHeat
import com.shnapps.couple.core.engine.game.Answer
import com.shnapps.couple.core.engine.game.GameMoment
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.ui.AfterhoursCard
import com.shnapps.couple.core.ui.PrimitiveRenderer
import com.shnapps.couple.core.ui.RunProgress
import com.shnapps.couple.core.ui.ScreenScaffold
import com.shnapps.couple.core.ui.SecureScreen
import com.shnapps.couple.core.ui.WhoIsPlaying
import com.shnapps.couple.core.ui.voice.LocalVoiceRecorder

/**
 * A game dealt on this phone and played on it, by two people passing it (REDESIGN_PROMPT.md §5.2).
 *
 * Every primitive is drawn by the shared renderers, so this screen knows nothing about any game —
 * only the order the interpreter hands them over in. Leaving is simply leaving: there is no session
 * to end, and nobody on the other side waiting for it.
 *
 * It is on the same frame as the card player (DESIGN_PROMPT.md §6.3): the same bar, the same
 * progress rail, the same docked action. A game and an evening should not feel like two apps, and
 * a person should not be able to tell which player they are in.
 */
@Composable
fun DealtGameScreen(
    onBack: () -> Unit,
    viewModel: DealtGameViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // A round that records needs something to record with. Provided here rather than reached for
    // inside the renderer, so a screenshot test and a preview both draw the same screen without one.
    CompositionLocalProvider(LocalVoiceRecorder provides viewModel.recorder) {
        DealtGameContent(
            state = state,
            onBack = onBack,
            onProceed = viewModel::proceed,
            onAnswer = viewModel::answer,
            onSkipPrompt = viewModel::skipPrompt,
            onNextRound = viewModel::nextRound,
            onPlayAgain = viewModel::deal,
            onFirstName = viewModel::setFirstName,
            onSecondName = viewModel::setSecondName,
            onConfirmNames = viewModel::confirmNames,
            onSkipNames = viewModel::skipNames,
        )
    }
}

@Suppress("LongParameterList") // A game screen: the moment, and every way past it.
@Composable
internal fun DealtGameContent(
    state: DealtGameUiState,
    onBack: () -> Unit,
    onProceed: () -> Unit,
    onAnswer: (Answer) -> Unit,
    onSkipPrompt: () -> Unit,
    onNextRound: () -> Unit,
    onPlayAgain: () -> Unit,
    onFirstName: (String) -> Unit = {},
    onSecondName: (String) -> Unit = {},
    onConfirmNames: () -> Unit = {},
    onSkipNames: () -> Unit = {},
) {
    val finished = state.moment is GameMoment.Finished
    val playing = !state.loading && !state.nothingToPlay && !finished

    val dock = dockFor(state, onBack, onSkipPrompt, onPlayAgain, onConfirmNames, onSkipNames)

    ProvideHeat(state.heat) {
        ScreenScaffold(
            title = state.title.takeIf { it.isNotEmpty() && playing && !state.askingNames },
            onBack = onBack,
            backLabel = "Leave the game",
            belowBar = if (playing && state.rounds > 0 && !state.askingNames) {
                { RunProgress(current = state.round, total = state.rounds) }
            } else {
                null
            },
            dock = dock,
            centred = true,
        ) {
            when {
                state.loading -> Dealing()
                // Asked once, at the first hand-off, and never again (D-121).
                state.askingNames -> WhoIsPlaying(
                    first = state.firstName,
                    second = state.secondName,
                    onFirst = onFirstName,
                    onSecond = onSecondName,
                )
                state.nothingToPlay -> NothingToPlay()
                else -> Moment(
                    moment = state.moment,
                    players = state.players,
                    onProceed = onProceed,
                    onAnswer = onAnswer,
                    onNextRound = onNextRound,
                )
            }
        }
    }
}

@Composable
private fun Moment(
    moment: GameMoment?,
    players: GamePlayers,
    onProceed: () -> Unit,
    onAnswer: (Answer) -> Unit,
    onNextRound: () -> Unit,
) {
    when (moment) {
        null -> Unit

        is GameMoment.HandOff -> Pass(
            line = if (players.named) "Pass the phone to ${players.name(moment.seat)}." else "Pass the phone over.",
            button = "I've got it",
            onNext = onProceed,
        )

        is GameMoment.Gather -> Pass(line = "Both of you, look.", button = "We're both here", onNext = onProceed)

        // One phone between two people: nobody waits for a network here, so this moment only
        // appears if a two-phone game is ever played locally. It says the truth either way.
        is GameMoment.Waiting -> Pass(
            line = if (players.named) "Waiting for ${players.name(moment.seat)}." else "Waiting for them.",
            button = "Carry on",
            onNext = onProceed,
        )

        is GameMoment.Turn -> PrimitiveRenderer(
            primitive = moment.primitive,
            players = players,
            onSubmit = onAnswer,
        )

        is GameMoment.RoundOver -> Pass(line = "That's the round.", button = "Next", onNext = onNextRound)

        is GameMoment.Finished -> AfterhoursCard(
            eyebrow = "That's the game",
            title = "Nicely played, both of you.",
            body = "Play it again for a different set, or go back and pick another.",
        )
    }
}

/**
 * The hand-off: the one moment in the app with no chrome at all (§6.3).
 *
 * One line, one action, and nothing of what comes next anywhere on the screen. Whatever is about
 * to be asked belongs to the person the phone is going to, and a preview of it would give the
 * moment away.
 */
@Composable
private fun Pass(line: String, button: String, onNext: () -> Unit) {
    AfterhoursCard(title = line, lit = true)
    GlowButton(
        text = button,
        onClick = onNext,
        leadingIcon = AfterhoursIcons.Check,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Dealing. A skeleton in the card's own shape, not a word in grey (§5.8). */
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

/** Everything this game had is above the heat, or has been refused. Said plainly. */
@Composable
private fun NothingToPlay() {
    AfterhoursCard(
        eyebrow = "Nothing here",
        title = "Nothing to deal at this heat.",
        body = "Turn it up, or try another game — there is plenty at the other levels.",
        lit = false,
    )
}

private val SKELETON = 240.dp

/**
 * What the dock offers, which is one question: which of the screen's five states it is in.
 *
 * Its own function so the screen itself stays readable — the content is a `when` over the same
 * states, and two of them side by side is where a reader loses the thread.
 */
@Suppress("LongParameterList") // Five states, five ways out.
@Composable
private fun dockFor(
    state: DealtGameUiState,
    onBack: () -> Unit,
    onSkipPrompt: () -> Unit,
    onPlayAgain: () -> Unit,
    onConfirmNames: () -> Unit,
    onSkipNames: () -> Unit,
): (@Composable ColumnScope.() -> Unit)? = when {
    // Asked once, at the first hand-off, and never again (D-121).
    state.askingNames -> {
        {
            GlowButton(
                text = "That's us",
                onClick = onConfirmNames,
                enabled = state.namesReady,
                leadingIcon = AfterhoursIcons.Check,
                modifier = Modifier.fillMaxWidth(),
            )
            GlowButton(
                text = "Not now",
                onClick = onSkipNames,
                style = GlowButtonStyle.Quiet,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    state.nothingToPlay -> {
        {
            GlowButton(
                text = "Pick something else",
                onClick = onBack,
                leadingIcon = AfterhoursIcons.Forward,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    state.moment is GameMoment.Finished -> {
        {
            GlowButton(
                text = "Play it again",
                onClick = onPlayAgain,
                leadingIcon = AfterhoursIcons.Dice,
                modifier = Modifier.fillMaxWidth(),
            )
            GlowButton(
                text = "Pick something else",
                onClick = onBack,
                style = GlowButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    state.moment is GameMoment.Turn -> {
        {
            if ((state.moment as GameMoment.Turn).canSkip) {
                GlowButton(
                    text = "Not this one",
                    onClick = onSkipPrompt,
                    style = GlowButtonStyle.Quiet,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    else -> null
}
