package com.shnapps.couple.feature.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.component.WaitingForPartner
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.icon.icon
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.engine.game.GameMoment
import com.shnapps.couple.core.engine.game.Watched
import com.shnapps.couple.core.model.CloseCheckIn
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.PromptKind
import com.shnapps.couple.core.model.Scoring
import com.shnapps.couple.core.model.SessionEndReason
import com.shnapps.couple.core.ui.AfterhoursCard
import com.shnapps.couple.core.ui.PrimitiveRenderer
import com.shnapps.couple.core.ui.ScreenColumn
import com.shnapps.couple.core.ui.ScreenHeading
import com.shnapps.couple.core.ui.WhoIsPlaying

@Composable
internal fun LoadingStage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun MissingStage(actions: GameActions) {
    ScreenColumn {
        ScreenHeading(
            eyebrow = "Games",
            headline = "This game isn't here any more.",
            body = "Start another whenever you like.",
        )
        GlowButton(text = "Back to games", onClick = actions.onGames, modifier = Modifier.fillMaxWidth())
    }
}

/** The game's shape before it starts: how many rounds, how far, and who goes first. */
@Composable
internal fun IntroStage(state: GameUiState, actions: GameActions) {
    val released = state.started?.released ?: return
    val game = released.game
    ScreenColumn {
        ScreenHeading(
            eyebrow = "${released.rounds.size} rounds · up to ${peakOf(state)}",
            headline = game.title,
            body = game.description,
        )
        Text(
            text = if (state.twoPhones) {
                "You'll each answer on your own phone, and see both answers together."
            } else {
                "${state.players.first} goes first, then ${state.players.second}."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        GlowButton(
            text = "Start the game",
            onClick = actions.onOpen,
            enabled = !state.busy,
            leadingIcon = AfterhoursIcons.Dice,
            modifier = Modifier.fillMaxWidth(),
        )
        GlowButton(text = "Back to games", onClick = actions.onGames, style = GlowButtonStyle.Quiet)
    }
}

/** BOLD or WILD: each partner's "I'm in", on their own phone (§3.1). */
@Composable
internal fun ConsentStage(state: GameUiState, actions: GameActions) {
    val session = state.session ?: return
    val peak = peakOf(state)
    ScreenColumn {
        if (!session.consented) {
            ScreenHeading(
                eyebrow = "Before we begin",
                headline = if (session.isHost) "This one goes up to $peak." else "Your partner has a game ready.",
                body = if (session.isHost) {
                    "You both need to say you're in — each of you, on your own phone. Not tonight is always an answer."
                } else {
                    "It goes up to $peak. Are you in? Not tonight is always an answer."
                },
            )
            GlowButton(
                text = "I'm in",
                onClick = actions.onConsent,
                leadingIcon = AfterhoursIcons.Flame,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            WaitingForPartner(
                message = "You're in.",
                detail = "Waiting for your partner's \"I'm in\" — on their phone.",
            )
        }
        GlowButton(
            text = "Not tonight",
            onClick = actions.onDecline,
            style = GlowButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun ReadyStage(state: GameUiState, actions: GameActions) {
    ScreenColumn {
        ScreenHeading(
            eyebrow = "Ready",
            headline = "Here we go.",
            body = if (state.twoPhones) {
                "Answer on your own phone. Neither of you sees the other's until you've both answered."
            } else {
                "${state.players.first} goes first. Pass the phone when it asks — and only look when it's yours."
            },
        )
        GlowButton(
            text = "Let's play",
            onClick = actions.onPlay,
            enabled = !state.busy,
            leadingIcon = AfterhoursIcons.Dice,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Who is passing the phone (D-121).
 *
 * Here rather than before the game because this is the first moment the app is about to say a name
 * out loud. Skipping is a real answer: without names a vote stays relative and every line that would
 * have said a name says something true instead.
 */
@Composable
private fun AskNames(state: GameUiState, actions: GameActions) {
    WhoIsPlaying(
        first = state.firstName,
        second = state.secondName,
        onFirst = actions.onFirstName,
        onSecond = actions.onSecondName,
    )
    GlowButton(
        text = "That's us",
        onClick = actions.onConfirmNames,
        enabled = state.namesReady,
        modifier = Modifier.fillMaxWidth(),
    )
    GlowButton(
        text = "Not now",
        onClick = actions.onSkipNames,
        style = GlowButtonStyle.Quiet,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Passing the phone (DESIGN_PROMPT.md §6.3).
 *
 * Its own composable because it is its own moment: one line, one action, and nothing of what comes
 * next. With no names there is nobody to name — the phone is going to whoever is not holding it,
 * which both of them already know, and a stand-in name here would be a pronoun pretending to be a
 * person (see `GamePlayers`).
 */
@Composable
private fun HandOff(players: GamePlayers, seat: Int, onProceed: () -> Unit) {
    val name = players.name(seat).takeIf { players.named }
    ScreenHeading(
        eyebrow = "Pass the phone",
        headline = name?.let { "Over to $it." } ?: "Over to them.",
        body = name?.let { "Only $it should look." } ?: "Only they should look.",
    )
    GlowButton(
        text = name?.let { "I'm $it" } ?: "I've got it",
        onClick = onProceed,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The game itself: whatever the interpreter says comes next. */
@Composable
internal fun PlayingStage(moment: GameMoment, state: GameUiState, actions: GameActions) {
    ScreenColumn {
        when (moment) {
            // Asked once, at the first hand-off, and never again (D-121).
            is GameMoment.HandOff -> if (state.askingNames) {
                AskNames(state, actions)
            } else {
                HandOff(state.players, moment.seat, actions.onProceed)
            }
            // Watching them play their turn, or simply waiting on an answer they are keeping to
            // themselves until the reveal (§12).
            is GameMoment.Waiting -> TheirTurn(moment, state)
            is GameMoment.Gather -> {
                ScreenHeading(
                    eyebrow = "Answers are in",
                    headline = "Together now.",
                    body = "Put the phone where you can both see it.",
                )
                GlowButton(text = "Show us", onClick = actions.onProceed, modifier = Modifier.fillMaxWidth())
            }
            is GameMoment.Turn -> {
                PrimitiveRenderer(moment.primitive, state.players, actions.onSubmit)
                if (moment.canSkip) {
                    GlowButton(text = "Not this one", onClick = actions.onSkip, style = GlowButtonStyle.Quiet)
                }
                state.keepOut?.let { KeepOutOffer(it, actions) }
            }
            is GameMoment.RoundOver -> RoundOver(moment, state, actions)
            is GameMoment.Finished -> {
                ScreenHeading(eyebrow = "Game over", headline = "That's the game.", body = scoreLine(state))
            }
        }
    }
}

/** The end of a round: what it came to, and the way on — once there is one. */
@Composable
private fun RoundOver(moment: GameMoment.RoundOver, state: GameUiState, actions: GameActions) {
    val last = moment.round >= state.roundCount - 1
    ScreenHeading(
        eyebrow = "Round ${moment.round + 1} of ${state.roundCount}",
        headline = if (last) "That's the last one." else "Round done.",
        body = scoreLine(state),
    )
    // A round is over for both only once both have looked (§12.1): rather than a button that
    // does nothing, say what is being waited for.
    if (state.waitingOnPartner) {
        WaitingOnThem(
            message = "Still with them.",
            detail = "They're still looking at the answers.",
            away = state.partnerAway,
        )
    } else {
        GlowButton(
            text = if (last) "See how it went" else "Next round",
            onClick = actions.onNextRound,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Their turn: what they drew, when it is open, and otherwise only that it is theirs. */
@Composable
private fun TheirTurn(moment: GameMoment.Waiting, state: GameUiState) {
    val watched = moment.watching
    if (watched != null) {
        WatchingThem(watched = watched, players = state.players, seat = moment.seat, away = state.partnerAway)
        return
    }
    WaitingOnThem(
        message = when {
            state.twoPhones -> "Over to them."
            state.players.named -> "${state.players.name(moment.seat)} is answering."
            else -> "They're answering."
        },
        detail = "You'll both see the answers at once.",
        away = state.partnerAway,
    )
}

/**
 * Their turn, and what they drew (§12).
 *
 * The whole point of pairing for a game of turns: the dare is on both phones, so one of them is
 * doing it and the other can see what it was. It is read-only by construction — this phone has no
 * step in the round at all — and it stays until the other finishes and the session moves on.
 */
@Composable
private fun WatchingThem(watched: Watched, players: GamePlayers, seat: Int, away: Boolean) {
    val who = if (players.named) players.name(seat) else "They"
    val drew = when (watched.kind) {
        PromptKind.TRUTH -> "a truth"
        PromptKind.DARE -> "a dare"
        null -> "this"
    }
    Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.md)) {
        AfterhoursCard(
            eyebrow = if (players.named) "${who.uppercase()}'S TURN" else "THEIR TURN",
            title = watched.text,
            body = if (players.named) "$who drew $drew." else "They drew $drew.",
        )
        Text(
            text = if (away) {
                "Your partner dropped out — waiting for them to come back."
            } else {
                "Doing it now. The round moves on when they're done."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
        )
    }
}

/** Waiting on the other phone — and saying so plainly when it has gone quiet (§12.3). */
@Composable
private fun WaitingOnThem(message: String, detail: String, away: Boolean) {
    WaitingForPartner(
        message = message,
        detail = if (away) "Your partner dropped out — waiting for them to come back." else detail,
    )
}

/** After "Not this one": keep what it was about out of the rest of this game (D-071). */
@Composable
private fun KeepOutOffer(offer: KeepOutOffer, actions: GameActions) {
    CinematicCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "KEEP IT OUT OF THIS GAME?", style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
        offer.themes.forEach { (id, title) ->
            GlowButton(
                text = title,
                onClick = { actions.onKeepOut(id) },
                style = GlowButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        GlowButton(text = "No, carry on", onClick = actions.onDismissKeepOut, style = GlowButtonStyle.Quiet)
        Text(
            text = "For good, either of you can set it in your own limits.",
            style = MaterialTheme.typography.bodySmall,
            color = AfterhoursTheme.colors.textMuted,
        )
    }
}

/** The other phone: the game is being played on this couple's other one. */
@Composable
internal fun ElsewhereStage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = AfterhoursTheme.spacing.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
        ) {
            Text(
                text = "This one's on the other phone.",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "It asks one of you per round, so it is played on one phone and passed " +
                    "over. Either of you can pause or stop it from here, any time.",
                style = MaterialTheme.typography.bodyLarge,
                color = AfterhoursTheme.colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Stopped, declined or run out: warm, and never who (§3.1). */
@Composable
internal fun EndedStage(reason: SessionEndReason, actions: GameActions) {
    val (headline, body) = when (reason) {
        SessionEndReason.COMPLETED -> "That's the game." to "Nicely played, both of you."
        SessionEndReason.STOPPED -> "Ending here. All good." to "Nothing more to do. Come back whenever you like."
        SessionEndReason.DECLINED -> "Not tonight — and that's fine." to "There's always another time."
        SessionEndReason.EXPIRED -> "This game timed out." to "Start another whenever you like."
    }
    ScreenColumn {
        ScreenHeading(eyebrow = "Games", headline = headline, body = body)
        GlowButton(text = "Home", onClick = actions.onHome, modifier = Modifier.fillMaxWidth())
    }
}

/** "How are we doing?" (§3.1): the check-in at the close, before any score. */
@Composable
internal fun HowAreWeStage(state: GameUiState, actions: GameActions) {
    ScreenColumn {
        ScreenHeading(
            eyebrow = "Game complete",
            headline = "How are we doing?",
            body = "Just a moment for the two of you.",
        )
        val answer = state.closeAnswer
        if (answer == null) {
            CloseCheckIn.entries.forEach { option ->
                GlowButton(
                    text = option.label,
                    onClick = { actions.onAnswerClose(option) },
                    leadingIcon = option.icon(),
                    style = GlowButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Text(
                text = answer.followUp,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (answer == CloseCheckIn.A_BIT_MUCH) {
                GlowButton(
                    text = "Adjust my limits",
                    onClick = actions.onAdjustLimits,
                    style = GlowButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            GlowButton(text = "Continue", onClick = actions.onToResults, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun ResultsStage(state: GameUiState, actions: GameActions) {
    ScreenColumn {
        ScreenHeading(eyebrow = "Game complete", headline = headlineOf(state), body = scoreLine(state))
        GlowButton(
            text = "Another game",
            onClick = actions.onGames,
            leadingIcon = AfterhoursIcons.Dice,
            modifier = Modifier.fillMaxWidth(),
        )
        GlowButton(
            text = "Home",
            onClick = actions.onHome,
            style = GlowButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** How far the game goes: its most intense round. */
private fun peakOf(state: GameUiState): String =
    state.started?.released?.rounds?.flatMap { it.prompts }?.maxOfOrNull { it.intensity.level }
        ?.let { level -> Intensity.ofLevel(level).name } ?: Intensity.HOT.name

/** The score so far, as the game keeps it — or nothing, for a game that keeps none. */
private fun scoreLine(state: GameUiState): String? {
    val run = state.run ?: return null
    val score = run.score
    val players = state.players
    return when (run.game.scoring) {
        Scoring.NONE -> null
        Scoring.MATCHES -> "You've matched on ${score.matches} of ${score.compared}."
        Scoring.POINTS -> "${players.first} ${score.points[0]} · ${players.second} ${score.points[1]}"
    }
}

private fun headlineOf(state: GameUiState): String {
    val run = state.run ?: return "That's the game."
    val score = run.score
    return when (run.game.scoring) {
        Scoring.NONE -> "Every round played."
        Scoring.MATCHES -> {
            val inSync = score.compared > 0 && score.matches * 2 >= score.compared
            if (inSync) "In sync." else "Full of surprises."
        }
        Scoring.POINTS -> when {
            score.points[0] > score.points[1] -> "${state.players.first} takes it."
            score.points[1] > score.points[0] -> "${state.players.second} takes it."
            else -> "A draw."
        }
    }
}
