package com.shnapps.couple.core.engine.game

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.GamePrimitive
import com.shnapps.couple.core.model.GamePrompt
import com.shnapps.couple.core.model.GameStep
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.RevealOutcome
import com.shnapps.couple.core.model.Scoring
import com.shnapps.couple.core.model.StepAudience
import com.shnapps.couple.core.model.StepOptions
import com.shnapps.couple.core.model.StepText
import com.shnapps.couple.core.model.TurnOrder
import org.junit.Test

/**
 * The two primitives chance and desire are built from (REDESIGN_PROMPT.md §4).
 *
 * `DRAW` is the one place a game lets chance decide, so the thing worth proving is that it is not
 * really chance at all: the landing is computed from the round, which is what lets a test assert
 * it, two phones agree on it without exchanging anything, and a restarted phone come back to the
 * same result (DECISIONS.md D-047, D-075).
 *
 * `DIAL` is how the app asks "how much" without making anyone say a number out loud. What it must
 * never do is tell one partner they wanted something more than the other: only the gap is shared,
 * and the gap has no direction.
 */
class DrawAndDialTest {

    @Test
    fun `a draw lands on one of the prompt's own options`() {
        val run = startSpin()
        val draw = run.draw()

        assertThat(draw.options.map { it.label }).containsExactly("Neck", "Wrist", "Shoulder", "Hip").inOrder()
        assertThat(draw.options).contains(draw.landed)
    }

    @Test
    fun `the same round always lands the same way`() {
        // The phone is restarted mid-game, or the other phone reads the same round: both must see
        // the same result, and neither sends anything to make that true.
        val first = startSpin().draw().landed
        val again = startSpin().draw().landed

        assertThat(again).isEqualTo(first)
    }

    @Test
    fun `different rounds do not all land the same way`() {
        val landings = (0 until ROUNDS).map { round -> startSpin(fromRound = round).draw().landed.label }

        assertThat(landings.toSet().size).isGreaterThan(1)
    }

    @Test
    fun `a draw is taken by pressing on, not by choosing`() {
        val draw = startSpin().draw()

        // Nothing the person does changes where it landed: the only answer a draw accepts is
        // "seen it".
        assertThat(draw.accepts(Answer.Done)).isTrue()
        assertThat(draw.accepts(Answer.Picked(draw.options.first().id))).isFalse()
    }

    @Test
    fun `a dial takes any number on its scale and nothing else`() {
        val dial = startRating().dial()

        assertThat(RoundPrimitive.RANGE.all { dial.accepts(Answer.Dialed(it)) }).isTrue()
        assertThat(dial.accepts(Answer.Dialed(RoundPrimitive.RANGE.last + 1))).isFalse()
        assertThat(dial.accepts(Answer.Dialed(-1))).isFalse()
        assertThat(dial.accepts(Answer.Done)).isFalse()
    }

    @Test
    fun `a reveal shows the gap and never who wanted it more`() {
        val reveal = rated(mine = 8, theirs = 3).reveal()

        assertThat(reveal.verdict).isEqualTo(Verdict.Apart(5))
        // Neither number is attributed to a seat, and neither appears on its own.
        assertThat(reveal.shown.map { it.seat }).containsExactly(null)
        assertThat(reveal.shown.single().label).doesNotContain("8")
    }

    @Test
    fun `the same number both ways is a match, and the gap is symmetrical`() {
        assertThat(rated(mine = 7, theirs = 7).reveal().verdict).isEqualTo(Verdict.Apart(0))
        assertThat(rated(mine = 2, theirs = 9).reveal().verdict)
            .isEqualTo(rated(mine = 9, theirs = 2).reveal().verdict)
    }

    @Test
    fun `landing on the same number scores as a match`() {
        val finished = rated(mine = 4, theirs = 4).take(Answer.Done)

        assertThat(finished.score.matches).isEqualTo(1)
        assertThat(finished.score.compared).isEqualTo(1)
        assertThat(finished.score.points).containsExactly(0, 0).inOrder()
    }

    @Test
    fun `a dial survives the trip between two phones`() {
        val sent = Answer.Dialed(7).encode()

        assertThat(sent).isNotNull()
        assertThat(decodeAnswer(sent!!)).isEqualTo(Answer.Dialed(7))
    }

    @Test
    fun `a dial from an older phone that means nothing here is refused`() {
        assertThat(decodeAnswer("D\u001F99")).isNull()
        assertThat(decodeAnswer("D\u001Fseven")).isNull()
    }

    // ---- fixtures -------------------------------------------------------------------------------

    private fun GameRun.draw() = (moment as GameMoment.Turn).primitive as RoundPrimitive.Draw

    private fun GameRun.dial() = (ready().moment as GameMoment.Turn).primitive as RoundPrimitive.Dial

    /** Past the "pass the phone" and "both look" beats, to the next thing to answer. */
    private fun GameRun.ready(): GameRun {
        var run = this
        while (run.moment is GameMoment.HandOff || run.moment is GameMoment.Gather) run = run.proceed()
        return run
    }

    private fun GameRun.reveal() = (ready().moment as GameMoment.Turn).primitive as RoundPrimitive.Reveal

    /** Both dials in, standing on the reveal. */
    private fun rated(mine: Int, theirs: Int): GameRun =
        startRating().take(Answer.Dialed(mine)).take(Answer.Dialed(theirs))

    /** The run after an answer the primitive must accept: a refusal here is the test failing. */
    private fun GameRun.take(answer: Answer): GameRun {
        val at = ready()
        return checkNotNull(at.submit(answer)) { "the run refused ${'$'}answer at ${'$'}{at.moment}" }
    }

    private fun startSpin(fromRound: Int = 0): GameRun = start(spin, spinRounds, fromRound)

    private fun startRating(): GameRun = start(rating, ratingRounds, 0)

    private fun start(game: Game, rounds: List<List<GamePrompt>>, fromRound: Int): GameRun {
        val all = rounds.flatten().associate { it.id to it.copy(game = game.id) }
        val released = ReleasedGame.fromServer(
            ServerGame(game.id, rounds.map { round -> round.map { it.id } }, emptyList(), effectiveIntensity = 5),
            game,
        ) { all[it] }
        return GameRun.start(released, GamePlayers("Alex", "Sam"), firstSeat = 0, fromRound = fromRound)
    }

    private companion object {
        const val ROUNDS = 8

        val spin = Game(
            id = "SHAPED_LIKE_SPIN",
            title = "Spin",
            tagline = "",
            description = "",
            modes = setOf(Mode.TOGETHER),
            rounds = ROUNDS,
            turns = TurnOrder.ALTERNATE,
            scoring = Scoring.NONE,
            promptKinds = emptyList(),
            steps = listOf(
                GameStep(
                    primitive = GamePrimitive.DRAW,
                    who = StepAudience.TOGETHER,
                    isPrivate = false,
                    text = StepText.Prompt,
                    options = StepOptions.FromPrompt,
                    answer = null,
                    of = emptyList(),
                    outcome = null,
                    seconds = null,
                    kindFrom = null,
                ),
            ),
        )

        val rating = Game(
            id = "SHAPED_LIKE_RATE_IT",
            title = "Rate It",
            tagline = "",
            description = "",
            modes = setOf(Mode.TOGETHER),
            rounds = 1,
            turns = TurnOrder.SHARED,
            scoring = Scoring.MATCHES,
            promptKinds = emptyList(),
            steps = listOf(
                GameStep(
                    primitive = GamePrimitive.DIAL,
                    who = StepAudience.EACH,
                    isPrivate = true,
                    text = StepText.Prompt,
                    options = null,
                    answer = null,
                    of = emptyList(),
                    outcome = null,
                    seconds = null,
                    kindFrom = null,
                ),
                GameStep(
                    primitive = GamePrimitive.REVEAL,
                    who = StepAudience.TOGETHER,
                    isPrivate = false,
                    text = null,
                    options = null,
                    answer = null,
                    of = listOf(0),
                    outcome = RevealOutcome.DISTANCE,
                    seconds = null,
                    kindFrom = null,
                ),
            ),
        )

        fun prompt(id: String, options: List<String> = emptyList()) = GamePrompt(
            id = id,
            version = 1,
            game = "ANY",
            kind = null,
            text = "The prompt $id.",
            options = options,
            seconds = null,
            category = "communication",
            tags = setOf("game"),
            intensity = Intensity.MILD,
            modes = setOf(Mode.TOGETHER),
            moods = emptySet(),
            requiredMutualPreferences = emptySet(),
            boostedByPreferences = emptySet(),
            excludedByBoundaries = setOf("mood_gentle"),
            status = ContentStatus.PUBLISHED,
        )

        val spinRounds = (0 until ROUNDS).map {
            listOf(prompt("spin_$it", listOf("Neck", "Wrist", "Shoulder", "Hip")))
        }

        val ratingRounds = listOf(listOf(prompt("rate_0")))
    }
}
