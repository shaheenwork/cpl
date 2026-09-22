package com.shnapps.couple.feature.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.CountdownRing
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.component.RevealCard
import com.shnapps.couple.core.designsystem.component.RevealState
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.engine.game.Answer
import com.shnapps.couple.core.engine.game.Option
import com.shnapps.couple.core.engine.game.RoundPrimitive
import com.shnapps.couple.core.engine.game.Verdict
import com.shnapps.couple.core.model.AnswerKind
import com.shnapps.couple.core.model.ChapterReaction
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.RevealOutcome
import kotlinx.coroutines.delay

// One renderer per primitive (BUILD_PROMPT.md §11.1), and none for any game: a game is only
// ever the order these appear in. The architecture test holds this file to exactly one
// renderer for each kind of RoundPrimitive.

/** The one place a primitive is matched to its renderer. */
@Composable
internal fun PrimitiveRenderer(primitive: RoundPrimitive, players: GamePlayers, onSubmit: (Answer) -> Unit) {
    when (primitive) {
        is RoundPrimitive.Question -> QuestionRenderer(primitive, players, onSubmit)
        is RoundPrimitive.Choice -> ChoiceRenderer(primitive, players, onSubmit)
        is RoundPrimitive.Prediction -> PredictionRenderer(primitive, players, onSubmit)
        is RoundPrimitive.Vote -> VoteRenderer(primitive, players, onSubmit)
        is RoundPrimitive.Reveal -> RevealRenderer(primitive, players, onSubmit)
        is RoundPrimitive.Timer -> TimerRenderer(primitive, players, onSubmit)
        is RoundPrimitive.Challenge -> ChallengeRenderer(primitive, players, onSubmit)
        is RoundPrimitive.Reaction -> ReactionRenderer(primitive, players, onSubmit)
    }
}

@Composable
internal fun QuestionRenderer(question: RoundPrimitive.Question, players: GamePlayers, onSubmit: (Answer) -> Unit) {
    Asked(whoFor(question.seat, players), question.text)
    when (question.answer) {
        AnswerKind.SPOKEN -> {
            Hint("Say it out loud.")
            GlowButton(text = "Done", onClick = { onSubmit(Answer.Done) }, modifier = Modifier.fillMaxWidth())
        }
        AnswerKind.TEXT -> TypedAnswer(label = "Your answer", onSubmit = { onSubmit(Answer.Text(it)) })
        AnswerKind.STATEMENTS -> Statements(onSubmit = onSubmit)
    }
}

@Composable
internal fun ChoiceRenderer(choice: RoundPrimitive.Choice, players: GamePlayers, onSubmit: (Answer) -> Unit) {
    Asked(whoFor(choice.seat, players), choice.text)
    Options(choice.options) { onSubmit(Answer.Picked(it.id)) }
}

@Composable
internal fun PredictionRenderer(
    prediction: RoundPrimitive.Prediction,
    players: GamePlayers,
    onSubmit: (Answer) -> Unit,
) {
    Asked(whoFor(prediction.seat, players), prediction.text)
    val options = prediction.options
    if (options == null) {
        TypedAnswer(label = "Your guess", onSubmit = { onSubmit(Answer.Text(it)) })
    } else {
        Options(options) { onSubmit(Answer.Picked(it.id)) }
    }
}

@Composable
internal fun VoteRenderer(vote: RoundPrimitive.Vote, players: GamePlayers, onSubmit: (Answer) -> Unit) {
    Asked(whoFor(vote.seat, players), vote.text)
    Options(vote.options) { onSubmit(Answer.Picked(it.id)) }
}

@Composable
internal fun RevealRenderer(reveal: RoundPrimitive.Reveal, players: GamePlayers, onSubmit: (Answer) -> Unit) {
    val spacing = AfterhoursTheme.spacing
    var state by remember(reveal.step) { mutableStateOf(RevealState.Concealed) }
    LaunchedEffect(reveal.step) {
        delay(REVEAL_BEAT_MS)
        state = RevealState.Revealed
    }
    RevealCard(state = state, modifier = Modifier.fillMaxWidth(), concealedLabel = "Ready?") {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            reveal.shown.forEach { shown ->
                val owner = shown.seat?.let { players.name(it) }
                // Statements are shown as they were written: who guessed is in the verdict.
                val named = owner != null && reveal.outcome != RevealOutcome.LIE_FOUND
                val typography = MaterialTheme.typography
                val plain = MaterialTheme.colorScheme.onSurface
                Text(
                    text = if (named) "$owner: ${shown.label}" else shown.label,
                    style = if (shown.highlighted) typography.titleMedium else typography.bodyLarge,
                    color = if (shown.highlighted) AfterhoursTheme.colors.brass else plain,
                )
            }
            Text(
                text = verdictLine(reveal, players),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    if (reveal.verdict == Verdict.AwaitingJudge) {
        GlowButton(
            text = "Close enough",
            onClick = { onSubmit(Answer.Judged(close = true)) },
            modifier = Modifier.fillMaxWidth(),
        )
        GlowButton(
            text = "Not really",
            onClick = { onSubmit(Answer.Judged(close = false)) },
            style = GlowButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        GlowButton(text = "Next", onClick = { onSubmit(Answer.Done) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun TimerRenderer(timer: RoundPrimitive.Timer, players: GamePlayers, onSubmit: (Answer) -> Unit) {
    var remaining by rememberSaveable(timer.step) { mutableIntStateOf(timer.seconds) }
    LaunchedEffect(timer.step) {
        while (remaining > 0) {
            delay(SECOND_MS)
            remaining -= 1
        }
    }
    Asked(whoFor(timer.seat, players), timer.caption)
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CountdownRing(
            progress = remaining.toFloat() / timer.seconds,
            value = if (remaining > 0) "$remaining" else "Time!",
            unit = if (remaining > 0) "seconds" else "",
        )
    }
    GlowButton(
        text = if (remaining > 0) "Done early" else "Done",
        onClick = { onSubmit(Answer.Done) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ChallengeRenderer(challenge: RoundPrimitive.Challenge, players: GamePlayers, onSubmit: (Answer) -> Unit) {
    val kind = challenge.kind?.label?.uppercase()
    CinematicCard(modifier = Modifier.fillMaxWidth(), glowing = true) {
        Text(
            text = listOfNotNull(kind, whoFor(challenge.seat, players).uppercase()).joinToString(" · "),
            style = EyebrowTextStyle,
            color = AfterhoursTheme.colors.brass,
        )
        Text(
            text = challenge.text,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    GlowButton(text = "Done", onClick = { onSubmit(Answer.Done) }, modifier = Modifier.fillMaxWidth())
}

@Composable
internal fun ReactionRenderer(reaction: RoundPrimitive.Reaction, players: GamePlayers, onSubmit: (Answer) -> Unit) {
    Asked(whoFor(reaction.seat, players), reaction.text)
    ChapterReaction.entries.forEach { option ->
        GlowButton(
            text = option.label,
            onClick = { onSubmit(Answer.Reacted(option)) },
            leadingEmoji = option.emoji,
            style = GlowButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---- pieces the renderers share ------------------------------------------------------------------

/** Whose turn a primitive is: a name, or both of them. */
private fun whoFor(seat: Int?, players: GamePlayers): String = seat?.let { players.name(it) } ?: "Together"

@Composable
private fun Asked(who: String, text: String) {
    Text(text = who.uppercase(), style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
    if (text.isNotEmpty()) {
        Text(text = text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = AfterhoursTheme.colors.textMuted)
}

@Composable
private fun Options(options: List<Option>, onPick: (Option) -> Unit) {
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
    ) {
        options.forEach { option ->
            GlowButton(
                text = option.label,
                onClick = { onPick(option) },
                style = GlowButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TypedAnswer(label: String, onSubmit: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(MAX_ANSWER) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
    Hint("Only you can see this until the reveal.")
    GlowButton(
        text = "Lock it in",
        onClick = { onSubmit(text.trim()) },
        enabled = text.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Two truths and a lie: three statements, and which one is the lie. */
@Composable
private fun Statements(onSubmit: (Answer) -> Unit) {
    val statements = remember { mutableStateListOf("", "", "") }
    var lie by rememberSaveable { mutableIntStateOf(-1) }
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
    ) {
        statements.forEachIndexed { index, statement ->
            OutlinedTextField(
                value = statement,
                onValueChange = { statements[index] = it.take(MAX_ANSWER) },
                label = { Text("Statement ${index + 1}") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.selectable(
                    selected = lie == index,
                    onClick = { lie = index },
                    role = Role.RadioButton,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = lie == index, onClick = null)
                Text(text = "This one is the lie", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Hint("Only you can see these until the reveal.")
    GlowButton(
        text = "Lock them in",
        onClick = { onSubmit(Answer.Statements(statements.map { it.trim() }, lie)) },
        enabled = statements.all { it.isNotBlank() } && lie in statements.indices,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun verdictLine(
    reveal: RoundPrimitive.Reveal,
    players: GamePlayers,
): String = when (val verdict = reveal.verdict) {
    is Verdict.Matched -> if (verdict.matched) "You matched." else "Different answers."
    is Verdict.Point -> {
        val seat = verdict.seat
        when {
            seat == null && reveal.outcome == RevealOutcome.WINNER -> "A tie."
            seat == null -> "No point this time."
            reveal.outcome == RevealOutcome.LIE_FOUND -> "A point to ${players.name(seat)}."
            else -> "${players.name(seat)} scores."
        }
    }
    Verdict.AwaitingJudge -> "Close enough?"
}

private const val SECOND_MS = 1_000L
private const val REVEAL_BEAT_MS = 700L
private const val MAX_ANSWER = 140
