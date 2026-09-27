package com.shnapps.couple.core.ui

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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.CountdownRing
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.component.RevealCard
import com.shnapps.couple.core.designsystem.component.RevealState
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.designsystem.theme.LocalReduceMotion
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
//
// Shared rather than owned by the games feature, because two players now render the same
// primitives: the session player, where a game is a night the server started, and the local one,
// where a game is dealt on a phone with no account (REDESIGN_PROMPT.md §4.1). A feature may not
// import another feature, and duplicating a renderer would be the one thing §11.1 forbids.

/** The one place a primitive is matched to its renderer. */
@Composable
fun PrimitiveRenderer(primitive: RoundPrimitive, players: GamePlayers, onSubmit: (Answer) -> Unit) {
    when (primitive) {
        is RoundPrimitive.Question -> QuestionRenderer(primitive, players, onSubmit)
        is RoundPrimitive.Choice -> ChoiceRenderer(primitive, players, onSubmit)
        is RoundPrimitive.Prediction -> PredictionRenderer(primitive, players, onSubmit)
        is RoundPrimitive.Vote -> VoteRenderer(primitive, players, onSubmit)
        is RoundPrimitive.Reveal -> RevealRenderer(primitive, players, onSubmit)
        is RoundPrimitive.Timer -> TimerRenderer(primitive, players, onSubmit)
        is RoundPrimitive.Challenge -> ChallengeRenderer(primitive, players, onSubmit)
        is RoundPrimitive.Reaction -> ReactionRenderer(primitive, players, onSubmit)
        is RoundPrimitive.Draw -> DrawRenderer(primitive, players, onSubmit)
        is RoundPrimitive.Dial -> DialRenderer(primitive, players, onSubmit)
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

/**
 * Chance, landing.
 *
 * The result is already decided before this composes — the interpreter chose it from the round
 * itself — so the spin is honest animation rather than a draw of its own. It cycles the pool for a
 * moment, slowing down, and stops on what was decided.
 */
@Composable
internal fun DrawRenderer(draw: RoundPrimitive.Draw, players: GamePlayers, onSubmit: (Answer) -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    var showing by remember(draw.step) { mutableStateOf(if (reduceMotion) draw.landed else draw.options.first()) }
    var settled by rememberSaveable(draw.step) { mutableStateOf(reduceMotion) }

    LaunchedEffect(draw.step) {
        if (settled) return@LaunchedEffect
        var wait = SPIN_START_MS
        while (wait < SPIN_END_MS) {
            showing = draw.options.random()
            delay(wait)
            wait = (wait * SPIN_EASING).toLong()
        }
        showing = draw.landed
        settled = true
    }

    Asked(whoFor(draw.seat, players), draw.caption)
    CinematicCard(modifier = Modifier.fillMaxWidth(), glowing = settled) {
        Text(
            text = if (settled) "IT SAYS" else "SPINNING",
            style = EyebrowTextStyle,
            color = AfterhoursTheme.colors.brass,
        )
        Text(
            text = (if (settled) draw.landed else showing).label,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    GlowButton(
        text = if (settled) "Done" else "Spinning…",
        onClick = { onSubmit(Answer.Done) },
        enabled = settled,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * How much, without saying it.
 *
 * The number is never shown to the other partner and never leaves the phone as a number: only a
 * reveal's gap is shared, which is why the dial can be answered honestly.
 */
@Composable
internal fun DialRenderer(dial: RoundPrimitive.Dial, players: GamePlayers, onSubmit: (Answer) -> Unit) {
    var value by rememberSaveable(dial.step) { mutableIntStateOf(DIAL_START) }

    Asked(whoFor(dial.seat, players), dial.text)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
        Slider(
            value = value.toFloat(),
            onValueChange = { value = it.toInt() },
            valueRange = RoundPrimitive.RANGE.first.toFloat()..RoundPrimitive.RANGE.last.toFloat(),
            steps = RoundPrimitive.RANGE.last - RoundPrimitive.RANGE.first - 1,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = dial.text },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = dial.low, style = MaterialTheme.typography.bodySmall, color = AfterhoursTheme.colors.textMuted)
            Text(text = dial.high, style = MaterialTheme.typography.bodySmall, color = AfterhoursTheme.colors.textMuted)
        }
    }
    GlowButton(
        text = "That's my answer",
        onClick = { onSubmit(Answer.Dialed(value)) },
        modifier = Modifier.fillMaxWidth(),
    )
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
    is Verdict.Apart -> gapLine(verdict.gap)
    // Nothing to say about it: the answers are the point, and a verdict would make it a contest.
    Verdict.Shown -> ""
    Verdict.AwaitingJudge -> "Close enough?"
}

/** Where a dial starts: the middle, so neither end is the default answer. */
/** A gap this small reads as agreement rather than a difference. */
private const val NEAR_GAP = 2

/**
 * The gap, never who was higher: the direction is the part that would make someone answer the dial
 * dishonestly next time.
 */
private fun gapLine(gap: Int): String = when {
    gap < 0 -> "Waiting on you both."
    gap == 0 -> "Exactly the same number."
    gap <= NEAR_GAP -> "Close — $gap apart."
    else -> "$gap apart."
}

private const val DIAL_START = 5

/** A spin that slows: each step waits longer than the last, until it stops. */
private const val SPIN_START_MS = 60L
private const val SPIN_END_MS = 420L
private const val SPIN_EASING = 1.35

private const val SECOND_MS = 1_000L
private const val REVEAL_BEAT_MS = 700L
private const val MAX_ANSWER = 140
