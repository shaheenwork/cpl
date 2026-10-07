package com.shnapps.couple.core.ui

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.component.CountdownRing
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.component.RevealCard
import com.shnapps.couple.core.designsystem.component.RevealState
import com.shnapps.couple.core.designsystem.icon.icon
import com.shnapps.couple.core.designsystem.theme.AfterhoursEasing
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.designsystem.theme.LocalReduceMotion
import com.shnapps.couple.core.designsystem.theme.decorativeTween
import com.shnapps.couple.core.engine.game.Answer
import com.shnapps.couple.core.engine.game.Option
import com.shnapps.couple.core.engine.game.RolledDie
import com.shnapps.couple.core.engine.game.RoundPrimitive
import com.shnapps.couple.core.engine.game.Verdict
import com.shnapps.couple.core.model.AnswerKind
import com.shnapps.couple.core.model.ChapterReaction
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.RevealOutcome
import com.shnapps.couple.core.ui.voice.LocalVoiceRecorder
import com.shnapps.couple.core.ui.voice.VoicePlayer
import com.shnapps.couple.core.ui.voice.VoiceRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

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
        is RoundPrimitive.Dice -> DiceRenderer(primitive, players, onSubmit)
        is RoundPrimitive.Dial -> DialRenderer(primitive, players, onSubmit)
        is RoundPrimitive.Record -> RecordRenderer(primitive, players, onSubmit)
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
    // What they were asked, under what is being asked here: a guess at an answer to a question
    // nobody showed you is not a guess.
    prediction.asked?.let { Quoted(it) }
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
                val owner = shown.seat?.let { if (players.named) players.name(it) else seatWord(it) }
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
    // Keyed on what the round put on it, not only on which step it is: a step's number is the
    // same every round, so round two would come back to round one's spent clock (D-130).
    var remaining by rememberSaveable(timer.step, timer.seconds, timer.caption) {
        mutableIntStateOf(timer.seconds)
    }
    LaunchedEffect(timer.step, timer.seconds, timer.caption) {
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
            leadingIcon = option.icon(),
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
    // Keyed on where it landed as well as which step it is, or the second round's draw would come
    // up already spun (D-130).
    var showing by remember(draw.step, draw.landed) {
        mutableStateOf(if (reduceMotion) draw.landed else draw.options.first())
    }
    var settled by rememberSaveable(draw.step, draw.landed.id) { mutableStateOf(reduceMotion) }

    LaunchedEffect(draw.step, draw.landed) {
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
 * Dice, tumbling.
 *
 * However many the game rolls, read together. They land on what the interpreter decided from the
 * round's own seed — so the tumble is honest animation rather than a draw of its own (D-047), and a
 * reopened phone lands the same way.
 *
 * Nobody waits for them. They roll on arrival rather than asking to be rolled, and while they are
 * still turning the action underneath lands them at once — an animation may not hold a person up
 * (`Motion.MAX_BLOCKING_MS`), and a die that has to be asked to roll is a tap a round for nothing.
 */
@Composable
internal fun DiceRenderer(dice: RoundPrimitive.Dice, players: GamePlayers, onSubmit: (Answer) -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    // The roll this is: a step number repeats every round, and keying on it alone left the second
    // round's dice already landed and its clock already spent (D-130).
    val rolled = dice.dice.joinToString(",") { "${it.label}:${it.landed}" }
    val roll = remember(dice.step, rolled) { Animatable(if (reduceMotion) 1f else 0f) }
    var started by rememberSaveable(dice.step, rolled) { mutableStateOf(false) }
    val settled = roll.value >= 1f

    LaunchedEffect(dice.step, rolled) {
        if (!reduceMotion) roll.animateTo(1f, decorativeTween(ROLL_MS, false, AfterhoursEasing.Decelerate))
    }

    Asked(whoFor(dice.seat, players), dice.caption)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = diceDescription(dice, settled) },
        horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
    ) {
        dice.dice.forEachIndexed { index, die ->
            Die(die = die, progress = progressOf(roll.value, index), modifier = Modifier.weight(1f))
        }
    }

    val seconds = dice.seconds
    if (settled && seconds != null) DiceClock(seconds = seconds, running = started, key = rolled)

    GlowButton(
        text = when {
            !settled -> "Land them"
            seconds != null && !started -> "Start the clock"
            else -> "Done"
        },
        onClick = {
            when {
                !settled -> scope.launch { roll.snapTo(1f) }
                seconds != null && !started -> started = true
                else -> onSubmit(Answer.Done)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** One die: its own tumble, the face it lands on, and the pips that say which face that is. */
@Composable
private fun Die(die: RolledDie, progress: Float, modifier: Modifier = Modifier) {
    val colors = AfterhoursTheme.colors
    val settled = progress >= 1f
    val showing = if (settled) die.face.label else die.faces[flickerOf(progress, die.faces.size)].label

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = die.label.uppercase(),
            style = EyebrowTextStyle,
            color = if (settled) colors.brass else colors.textMuted,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    // Two axes at a different rate per die, so three dice never tumble in unison.
                    val left = 1f - progress
                    rotationX = left * TUMBLE * (die.label.length % TILT + 1)
                    rotationY = left * TUMBLE_SIDE
                    cameraDistance = CAMERA * density
                    val bounce = 1f + LAND_BOUNCE * left * progress
                    scaleX = bounce
                    scaleY = bounce
                }
                .clip(MaterialTheme.shapes.medium)
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surface),
                    ),
                )
                .border(
                    width = EDGE,
                    color = if (settled) colors.brass.copy(alpha = FACE_EDGE) else colors.edgeFaint,
                    shape = MaterialTheme.shapes.medium,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = showing,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = FACE_LINES,
                modifier = Modifier.padding(AfterhoursTheme.spacing.xs),
            )
        }
        Pips(count = die.landed + 1, lit = settled)
    }
}

/** Which face it is, the way a die says it. */
@Composable
private fun Pips(count: Int, lit: Boolean) {
    val colors = AfterhoursTheme.colors
    Row(
        modifier = Modifier.padding(top = AfterhoursTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(PIP_GAP),
    ) {
        repeat(count) {
            Box(
                modifier = Modifier
                    .size(PIP)
                    .clip(CircleShape)
                    .background(if (lit) colors.brass else colors.edgeFaint),
            )
        }
    }
}

/** The clock the dice landed on. Started by them, never by the app (as every timer here is). */
@Composable
private fun DiceClock(seconds: Int, running: Boolean, key: String) {
    var remaining by rememberSaveable(seconds, key) { mutableIntStateOf(seconds) }
    LaunchedEffect(running, seconds, key) {
        if (!running) return@LaunchedEffect
        while (remaining > 0) {
            delay(SECOND_MS)
            remaining -= 1
        }
    }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CountdownRing(
            progress = remaining.toFloat() / seconds,
            value = if (remaining > 0) clockLabel(remaining) else "Time!",
            unit = if (remaining in 1 until A_MINUTE) "seconds" else "",
        )
    }
}

/** Minutes and seconds once there is more than a minute of it, plain seconds under one. */
private fun clockLabel(seconds: Int): String = if (seconds < A_MINUTE) {
    "$seconds"
} else {
    "${seconds / A_MINUTE}:${(seconds % A_MINUTE).toString().padStart(2, PAD)}"
}

/**
 * How far one die is through the roll.
 *
 * One animation for the whole roll, staggered per die, so they land one after another rather than
 * together — and every one of them is landed by the time the roll is.
 */
private fun progressOf(roll: Float, index: Int): Float =
    ((roll - index * DIE_STAGGER) / (1f - LAST_STAGGER)).coerceIn(0f, 1f)

/** Which face flashes past while a die is still turning: quick at first, slowing as it slows. */
private fun flickerOf(progress: Float, faces: Int): Int =
    if (faces <= 1) 0 else (progress * faces * FLICKER).toInt() % faces

/** What a screen reader is told: that they are rolling, and then what they say. */
private fun diceDescription(dice: RoundPrimitive.Dice, settled: Boolean): String = if (settled) {
    dice.dice.joinToString(". ") { "${it.label}: ${it.face.label}" }
} else {
    "Rolling"
}

/** Long enough to be a roll, short enough that nobody is waiting for it. */
private const val ROLL_MS = 1_100

/** Degrees of tumble left to run when a die starts, on each axis. */
private const val TUMBLE = 1_080f
private const val TUMBLE_SIDE = 720f
private const val TILT = 3
private const val CAMERA = 16f
private const val LAND_BOUNCE = 0.5f
private const val FACE_EDGE = 0.6f
private const val FACE_LINES = 3
private const val FLICKER = 9f
private const val DIE_STAGGER = 0.12f
private const val LAST_STAGGER = 0.24f
private const val PAD = '0'
private val EDGE = 1.dp
private val PIP = 4.dp
private val PIP_GAP = 3.dp
private const val A_MINUTE = 60

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

/**
 * Proof, and the only place in this app that media exists (V2 rule 3).
 *
 * There is no inbox and no "send a photo" screen any more: a voice note or a picture happens
 * **inside a round**, because a dare asked for it, and it is over when the round is. A recording
 * goes to this app's cache and a photo is never written down at all — it is the bitmap the camera
 * handed back, shown to the two people who are looking at the phone, and gone with the round.
 *
 * Nothing is uploaded, and the answer that travels carries no path (D-069).
 */
@Composable
internal fun RecordRenderer(record: RoundPrimitive.Record, players: GamePlayers, onSubmit: (Answer) -> Unit) {
    Asked(whoFor(record.seat, players), record.text)
    when (record.kind) {
        MediaKind.PHOTO -> PhotoProof(record, onSubmit)
        else -> VoiceProof(record, onSubmit)
    }
}

/** One picture from the system camera, shown here and kept nowhere. */
@Composable
private fun PhotoProof(record: RoundPrimitive.Record, onSubmit: (Answer) -> Unit) {
    var shot by remember(record.step) { mutableStateOf<Bitmap?>(null) }
    var refused by remember(record.step) { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { taken ->
        // A null means they backed out, or this phone has no camera to open.
        if (taken == null) refused = true else shot = taken
    }

    shot?.let { picture ->
        Image(
            bitmap = picture.asImageBitmap(),
            contentDescription = "The photo just taken, shown here only.",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large),
        )
    }

    if (refused) {
        Text(
            text = "No camera here. Show them instead — it counts the same.",
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
        )
    }

    GlowButton(
        text = if (shot == null) "Take it" else "Take it again",
        onClick = {
            refused = false
            runCatching { camera.launch(null) }.onFailure { refused = true }
        },
        style = if (shot == null) GlowButtonStyle.Secondary else GlowButtonStyle.Quiet,
        modifier = Modifier.fillMaxWidth(),
    )

    GlowButton(
        text = if (shot == null) "Show them instead" else "Done",
        onClick = { onSubmit(Answer.Recorded(if (shot == null) SPOKEN_ALOUD else SHOWN)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Said out loud, and kept on this phone.
 *
 * The recording is this round's answer: it is played back here, for the two people in the room,
 * and it goes with the app's cache.
 *
 * It asks for the microphone itself. It used to rely on another screen having asked first, which
 * stopped being true the moment that screen was deleted — and a renderer that silently cannot
 * record is worse than one that asks.
 */
@Composable
private fun VoiceProof(record: RoundPrimitive.Record, onSubmit: (Answer) -> Unit) {
    val recorder = LocalVoiceRecorder.current
    val take = remember(record.step) { VoiceTake(recorder) }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) take.start() else take.refuse()
    }

    take.file?.let { VoicePlayer(file = it, modifier = Modifier.fillMaxWidth()) }

    if (take.refused) {
        Text(
            text = "This phone wouldn't record. Say it out loud instead — it counts the same.",
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
        )
    }

    GlowButton(
        text = when {
            take.recording -> "Stop"
            take.file != null -> "Record it again"
            else -> "Record"
        },
        onClick = { if (take.recording) take.stop() else microphone.launch(Manifest.permission.RECORD_AUDIO) },
        style = if (take.recording) GlowButtonStyle.Primary else GlowButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )

    GlowButton(
        text = if (take.file == null) "Say it out loud instead" else "Done",
        onClick = { onSubmit(Answer.Recorded(take.file?.absolutePath ?: SPOKEN_ALOUD)) },
        enabled = !take.recording,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * What a voice proof is in the middle of: recording, recorded, or refused.
 *
 * A small object rather than four `remember`s and a pair of lambdas in the composable, because the
 * recorder's two calls each have a failure to fold in and the layout above should read as layout.
 */
private class VoiceTake(private val recorder: VoiceRecorder?) {
    var recording by mutableStateOf(false)
        private set

    var file by mutableStateOf<File?>(null)
        private set

    var refused by mutableStateOf(false)
        private set

    fun start() {
        refused = false
        when (recorder?.start()) {
            is Outcome.Success -> recording = true
            else -> refuse()
        }
    }

    fun stop() {
        recording = false
        when (val stopped = recorder?.stop()) {
            is Outcome.Success -> file = stopped.value
            else -> refuse()
        }
    }

    /** The microphone was refused, or there is none. Saying so is the whole of the fallback. */
    fun refuse() {
        refused = true
    }
}

/**
 * The answer when somebody just says it.
 *
 * A recording is a convenience, not a requirement: a phone with no microphone permission, or two
 * people who would rather speak, must be able to finish the round.
 */
private const val SPOKEN_ALOUD = "spoken"

/** The answer when a photo was taken: that it happened, never where it is. */
private const val SHOWN = "shown"

// ---- pieces the renderers share ------------------------------------------------------------------

/**
 * Whose turn a primitive is: a name, or both of them.
 *
 * With no names, whoever is holding the phone is simply "You" — which is true from the only side
 * that can see it, and is the reason a stand-in name must never be a pronoun (see [GamePlayers]).
 */
private fun whoFor(seat: Int?, players: GamePlayers): String = when {
    seat == null -> "Together"
    players.named -> players.name(seat)
    else -> "You"
}

@Composable
private fun Asked(who: String, text: String) {
    Text(text = who.uppercase(), style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
    if (text.isNotEmpty()) {
        Text(text = text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Somebody else's question, quoted here for the person guessing at its answer. */
@Composable
private fun Quoted(text: String) {
    Text(
        text = "“$text”",
        style = MaterialTheme.typography.titleMedium,
        color = AfterhoursTheme.colors.textMuted,
    )
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
            reveal.outcome == RevealOutcome.LIE_FOUND -> "A point to ${scorer(players, seat)}."
            else -> "${scorer(players, seat)} scores."
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

/**
 * Who scored, when nobody gave a name.
 *
 * A reveal is looked at by both of them at once, so "you" would be ambiguous here in a way it is
 * not on a private turn. The seat is named by its order instead.
 */
private fun scorer(players: GamePlayers, seat: Int): String =
    if (players.named) players.name(seat) else seatWord(seat)

private fun seatWord(seat: Int): String = if (seat == 0) "Whoever went first" else "Whoever went second"
