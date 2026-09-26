package com.shnapps.couple.feature.instant

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.engine.game.Answer
import com.shnapps.couple.core.engine.game.GameMoment
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.ui.BackRow
import com.shnapps.couple.core.ui.PrimitiveRenderer
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.SecureScreen

/**
 * A game, played on one phone by two people, with nobody signed in (REDESIGN_PROMPT.md §5.2).
 *
 * Every primitive is drawn by the shared renderers, so this screen knows nothing about any game —
 * only the order the interpreter hands them over in. Leaving is simply leaving: there is no session
 * to end, and nobody on the other side waiting for it.
 */
@Composable
fun InstantGameScreen(
    onBack: () -> Unit,
    viewModel: InstantGameViewModel = hiltViewModel(),
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    InstantGameContent(
        state = state,
        onBack = onBack,
        onProceed = viewModel::proceed,
        onAnswer = viewModel::answer,
        onSkipPrompt = viewModel::skipPrompt,
        onNextRound = viewModel::nextRound,
        onPlayAgain = viewModel::deal,
    )
}

@Suppress("LongParameterList") // A game screen: the moment, and every way past it.
@Composable
internal fun InstantGameContent(
    state: InstantGameUiState,
    onBack: () -> Unit,
    onProceed: () -> Unit,
    onAnswer: (Answer) -> Unit,
    onSkipPrompt: () -> Unit,
    onNextRound: () -> Unit,
    onPlayAgain: () -> Unit,
) {
    ScreenColumn {
        BackRow(label = "Leave the game", onBack = onBack)

        if (state.title.isNotEmpty()) {
            // Once the game is over there is no round to be on: counting past the last one reads
            // as "round 7 of 6", which is how this was found on the device.
            val finished = state.moment is GameMoment.Finished
            Text(
                text = if (finished) {
                    state.title.uppercase()
                } else {
                    "${state.title.uppercase()} · ROUND ${(state.round + 1).coerceAtMost(state.rounds)} " +
                        "OF ${state.rounds}"
                },
                style = EyebrowTextStyle,
                color = AfterhoursTheme.colors.brass,
            )
        }

        when {
            state.loading -> Text(
                text = "Dealing.",
                style = MaterialTheme.typography.bodyLarge,
                color = AfterhoursTheme.colors.textMuted,
            )
            state.nothingToPlay -> NothingToPlay(onBack = onBack)
            else -> Moment(
                moment = state.moment,
                players = state.players,
                onProceed = onProceed,
                onAnswer = onAnswer,
                onSkipPrompt = onSkipPrompt,
                onNextRound = onNextRound,
                onPlayAgain = onPlayAgain,
                onBack = onBack,
            )
        }
    }
}

@Suppress("LongParameterList") // One moment of a game, and the ways out of it.
@Composable
private fun Moment(
    moment: GameMoment?,
    players: GamePlayers,
    onProceed: () -> Unit,
    onAnswer: (Answer) -> Unit,
    onSkipPrompt: () -> Unit,
    onNextRound: () -> Unit,
    onPlayAgain: () -> Unit,
    onBack: () -> Unit,
) {
    when (moment) {
        null -> Unit

        is GameMoment.HandOff -> Pass(
            line = "Pass the phone to ${players.name(moment.seat)}.",
            button = "I've got it",
            onNext = onProceed,
        )

        is GameMoment.Gather -> Pass(
            line = "Both of you, look.",
            button = "We're both here",
            onNext = onProceed,
        )

        // One phone between two people: nobody waits for a network here, so this moment only
        // appears if a two-phone game is ever played locally. It says the truth either way.
        is GameMoment.Waiting -> Pass(
            line = "Waiting for ${players.name(moment.seat)}.",
            button = "Carry on",
            onNext = onProceed,
        )

        is GameMoment.Turn -> {
            PrimitiveRenderer(primitive = moment.primitive, players = players, onSubmit = onAnswer)
            if (moment.canSkip) {
                GlowButton(
                    text = "Not this one",
                    onClick = onSkipPrompt,
                    style = GlowButtonStyle.Quiet,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        is GameMoment.RoundOver -> Pass(line = "That's the round.", button = "Next", onNext = onNextRound)

        is GameMoment.Finished -> Finished(onPlayAgain = onPlayAgain, onBack = onBack)
    }
}

@Composable
private fun Pass(line: String, button: String, onNext: () -> Unit) {
    CinematicCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = line,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    GlowButton(text = button, onClick = onNext, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Finished(onPlayAgain: () -> Unit, onBack: () -> Unit) {
    CinematicCard(modifier = Modifier.fillMaxWidth(), glowing = true) {
        Text(
            text = "That's the game.",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Play it again for a different set, or go back and pick another.",
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
        )
    }
    GlowButton(text = "Play it again", onClick = onPlayAgain, modifier = Modifier.fillMaxWidth())
    GlowButton(
        text = "Pick something else",
        onClick = onBack,
        style = GlowButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Everything this game had is above the heat, or has been refused. Said plainly. */
@Composable
private fun NothingToPlay(onBack: () -> Unit) {
    CinematicCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Nothing to deal here.",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Turn the heat up, or try another game — there is plenty at the other levels.",
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
        )
    }
    GlowButton(text = "Pick something else", onClick = onBack, modifier = Modifier.fillMaxWidth())
}
