package com.shnapps.couple.core.engine.game

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.GameDie
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.GamePrimitive
import com.shnapps.couple.core.model.GamePrompt
import com.shnapps.couple.core.model.GameStep
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.Scoring
import com.shnapps.couple.core.model.StepAudience
import com.shnapps.couple.core.model.StepOptions
import com.shnapps.couple.core.model.StepText
import com.shnapps.couple.core.model.TurnOrder
import org.junit.Test

/**
 * Three dice, rolled at once (D-130).
 *
 * `DICE` is a `DRAW` with more than one pool, and it inherits the rule that matters: the roll is
 * computed from the round rather than made up by the device, so a test can assert it, a reopened
 * phone lands the same way, and two phones agree without exchanging anything (D-047, D-075).
 *
 * What is new is reading three at once. Each die has its own faces — fixed on the step, or the
 * round's own prompt — they land independently of one another, and one of them may carry the clock
 * the round runs on.
 */
class DiceTest {

    @Test
    fun `every die lands on one of its own faces`() {
        val dice = roll().dice

        assertThat(dice).hasSize(3)
        dice.forEach { die ->
            assertThat(die.faces).isNotEmpty()
            assertThat(die.landed).isIn(die.faces.indices.toList())
            assertThat(die.faces).contains(die.face)
        }
    }

    @Test
    fun `a die takes the round's own prompt for its faces`() {
        val where = roll().dice.first { it.label == WHERE }

        assertThat(where.faces.map { it.label }).containsExactly("Hands", "Neck", "Shoulders").inOrder()
    }

    @Test
    fun `the same round rolls the same way, and different rounds do not`() {
        assertThat(roll().dice.map { it.face.label }).isEqualTo(roll().dice.map { it.face.label })

        val rounds = (0 until ROUNDS).map { round -> roll(fromRound = round).dice.map { it.landed } }
        assertThat(rounds.toSet().size).isGreaterThan(1)
    }

    @Test
    fun `a die's face is its own, round after round`() {
        // Found on a device: two of three dice read the same face for three rounds running, because
        // each die's seed was the round's plus a constant (D-130). A die that never moves is not a
        // die, so every one of them has to vary across a game.
        val rolls = (0 until ROUNDS).map { round -> roll(fromRound = round).dice }

        rolls.first().indices.forEach { die ->
            assertThat(rolls.map { it[die].landed }.distinct().size).isGreaterThan(1)
        }
        // And within a round they are independent of each other.
        assertThat(rolls.any { it.first().landed != it.last().landed }).isTrue()
    }

    @Test
    fun `the clock is whatever the clock die landed on`() {
        (0 until ROUNDS).forEach { round ->
            val dice = roll(fromRound = round)
            val clock = dice.dice.first { it.label == HOW_LONG }

            // A face with no clock on it is a real answer: "until they say stop" is not a countdown
            // of zero seconds.
            assertThat(dice.seconds).isEqualTo(clock.seconds?.takeIf { it > 0 })
        }
    }

    @Test
    fun `a roll is refused like any other prompt`() {
        // Its faces come from the prompt, so "not this one" has something to swap — which is why a
        // dice step counts as showing the round's prompt at all.
        val run = started()
        val moment = run.moment as GameMoment.Turn

        assertThat(moment.canSkip).isTrue()
        assertThat(run.skipPrompt().promptInPlay()).isNotEqualTo(run.promptInPlay())
    }

    @Test
    fun `a game of turns hands the phone over before the round it belongs to`() {
        val released = release()
        val run = GameRun.start(released, GamePlayers("Alex", "Sam"))

        // Nothing here is private — both of them watch the dice — so only the turn itself puts the
        // phone in one pair of hands. Without this the game never said whose turn it was (D-130).
        assertThat(run.moment).isEqualTo(GameMoment.HandOff(0, seat = 0))
        val second = GameRun.start(released, GamePlayers("Alex", "Sam"), fromRound = 1)
        assertThat(second.moment).isEqualTo(GameMoment.HandOff(1, seat = 1))
    }

    private fun roll(fromRound: Int = 0): RoundPrimitive.Dice =
        (started(fromRound).moment as GameMoment.Turn).primitive as RoundPrimitive.Dice

    /** Past the hand-off a game of turns opens each round with. */
    private fun started(fromRound: Int = 0): GameRun =
        GameRun.start(release(), GamePlayers.UNNAMED, fromRound = fromRound).proceed()

    private fun release(): ReleasedGame = ReleasedGame.forHeat(heatDice, prompts, ceiling = 5)

    private companion object {
        const val ROUNDS = 4
        const val WHERE = "Where"
        const val HOW_LONG = "How long"

        val heatDice = Game(
            id = "TEST_DICE",
            title = "Heat dice",
            tagline = "Three dice.",
            description = "One says what, one says where, one says how long.",
            modes = setOf(Mode.TOGETHER),
            rounds = ROUNDS,
            turns = TurnOrder.ALTERNATE,
            scoring = Scoring.NONE,
            promptKinds = emptyList(),
            steps = listOf(
                GameStep(
                    primitive = GamePrimitive.DICE,
                    who = StepAudience.ACTIVE,
                    isPrivate = false,
                    text = StepText.Prompt,
                    options = null,
                    answer = null,
                    of = emptyList(),
                    outcome = null,
                    seconds = null,
                    kindFrom = null,
                    dice = listOf(
                        GameDie("What", StepOptions.Fixed(listOf("Kiss", "Tease", "Whisper it", "Their choice"))),
                        GameDie(WHERE, StepOptions.FromPrompt),
                        GameDie(
                            label = HOW_LONG,
                            faces = StepOptions.Fixed(listOf("30 seconds", "1 minute", "Until they say stop")),
                            seconds = listOf(30, 60, 0),
                        ),
                    ),
                ),
            ),
        )

        /** Enough for every round and an alternate to swap in. */
        val prompts = (1..ROUNDS * 2).map { n ->
            GamePrompt(
                id = "dice_$n",
                version = 1,
                game = heatDice.id,
                kind = null,
                text = "The rule for round $n: slowly.",
                options = listOf("Hands", "Neck", "Shoulders"),
                seconds = null,
                category = "exploration",
                tags = setOf("game"),
                intensity = Intensity.MILD,
                modes = setOf(Mode.TOGETHER),
                moods = emptySet(),
                requiredMutualPreferences = emptySet(),
                boostedByPreferences = emptySet(),
                excludedByBoundaries = setOf("mood_gentle"),
                status = ContentStatus.PUBLISHED,
            )
        }
    }
}
