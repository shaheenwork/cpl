package com.shnapps.couple.core.engine.game

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.model.AnswerKind
import com.shnapps.couple.core.model.ChapterReaction
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
 * The same interpreter, a phone each (BUILD_PROMPT.md §12): nothing is handed over, each phone
 * takes only its own steps, and what the other answered arrives as the server's reveal —
 * after which the round reads exactly as it does when one phone holds both answers.
 */
class TwoPhoneRunTest {

    private val players = GamePlayers("Alex", "Sam")

    @Test
    fun `each phone answers first and waits after, with no hand-off between them`() {
        var mine = run(seat = 0)
        var theirs = run(seat = 1)

        // No "pass the phone", and neither waits to be let in: each starts on its own turn.
        assertThat(((mine.moment as GameMoment.Turn).primitive as RoundPrimitive.Choice).seat).isEqualTo(0)
        assertThat(((theirs.moment as GameMoment.Turn).primitive as RoundPrimitive.Choice).seat).isEqualTo(1)

        mine = mine.submit(Answer.Picked("1"))!!
        assertThat(mine.moment).isEqualTo(GameMoment.Waiting(0, seat = 1))
        assertThat(mine.answerToSend()).isEqualTo(Answer.Picked("1"))

        // The second seat waits for the first in exactly the same way: the reveal comes only
        // once both have answered, so a phone that waited first would wait for ever.
        theirs = theirs.submit(Answer.Picked("1"))!!
        assertThat(theirs.moment).isEqualTo(GameMoment.Waiting(0, seat = 0))
        assertThat(theirs.answerToSend()).isEqualTo(Answer.Picked("1"))
    }

    @Test
    fun `the reveal reads the same on both phones once the server has said what each answered`() {
        var mine = run(seat = 0).submit(Answer.Picked("1"))!!
        // The server's reveal: what the other phone sent, put back where it belongs.
        mine = mine.withPartnerAnswer(step = 0, seat = 1, answer = Answer.Picked("1")).proceed()
        val reveal = (mine.moment as GameMoment.Turn).primitive as RoundPrimitive.Reveal
        assertThat(reveal.verdict).isEqualTo(Verdict.Matched(true))
        assertThat(reveal.shown.map { it.label }).containsExactly("Go out", "Go out")

        var theirs = run(seat = 1).submit(Answer.Picked("1"))!!
        theirs = theirs.withPartnerAnswer(step = 0, seat = 0, answer = Answer.Picked("1")).proceed()
        assertThat(((theirs.moment as GameMoment.Turn).primitive as RoundPrimitive.Reveal).verdict)
            .isEqualTo(Verdict.Matched(true))
    }

    @Test
    fun `a phone that only pressed on has nothing to send`() {
        val spoken = game(
            "TOGETHER_TALK",
            step(GamePrimitive.QUESTION, StepAudience.TOGETHER, text = StepText.Prompt, answer = AnswerKind.SPOKEN),
        )
        val run = start(spoken, seat = 0).submit(Answer.Done)!!
        assertThat(run.answerToSend()).isNull()
        assertThat(run.moment).isInstanceOf(GameMoment.RoundOver::class.java)
    }

    @Test
    fun `an answer travels as one short string and comes back the same`() {
        val answers = listOf(
            Answer.Picked("2"),
            Answer.Text("Pasta, every time"),
            Answer.Statements(listOf("I can juggle", "I hate tea", "I sang on stage"), lie = 1),
            Answer.Reacted(ChapterReaction.LOVED),
        )
        for (answer in answers) {
            val encoded = answer.encode()
            assertThat(encoded).isNotNull()
            assertThat(decodeAnswer(encoded!!)).isEqualTo(answer)
        }
        assertThat(Answer.Done.encode()).isNull()
        assertThat(Answer.Judged(close = true).encode()).isNull()
        assertThat(decodeAnswer("nonsense from a newer app")).isNull()
        assertThat(decodeAnswer("Snot-a-numberonetwothree")).isNull()
    }

    @Test
    fun `typed words keep their meaning, whatever is in them`() {
        val awkward = Answer.Text("onetwo — three")
        assertThat(decodeAnswer(awkward.encode()!!)).isEqualTo(Answer.Text("one two — three"))
    }

    // ---- helpers -------------------------------------------------------------------------------

    private fun run(seat: Int) = start(wouldYouRather, seat)

    private fun start(game: Game, seat: Int): GameRun {
        val prompts = (1..3).map { prompt("p$it", game.id) }
        val byId = prompts.associateBy { it.id }
        val released = ReleasedGame.fromServer(
            ServerGame(game.id, prompts.map { listOf(it.id) }, emptyList(), effectiveIntensity = 5),
            game,
        ) { byId[it] }
        return GameRun.start(released, players, firstSeat = 0, fromRound = 0, phone = seat)
    }

    private companion object {
        @Suppress("LongParameterList") // A step, with only what the test sets named.
        fun step(
            primitive: GamePrimitive,
            who: StepAudience,
            isPrivate: Boolean = false,
            text: StepText? = null,
            options: StepOptions? = null,
            answer: AnswerKind? = null,
            of: List<Int> = emptyList(),
            outcome: RevealOutcome? = null,
        ) = GameStep(primitive, who, isPrivate, text, options, answer, of, outcome, seconds = null, kindFrom = null)

        fun game(id: String, vararg steps: GameStep) = Game(
            id = id,
            title = id,
            tagline = "",
            description = "",
            modes = setOf(Mode.TOGETHER),
            rounds = 3,
            turns = TurnOrder.SHARED,
            scoring = Scoring.MATCHES,
            promptKinds = emptyList(),
            steps = steps.toList(),
        )

        val wouldYouRather = game(
            "SHAPED_LIKE_WOULD_YOU_RATHER",
            step(
                GamePrimitive.CHOICE,
                StepAudience.EACH,
                isPrivate = true,
                text = StepText.Prompt,
                options = StepOptions.FromPrompt,
            ),
            step(GamePrimitive.REVEAL, StepAudience.TOGETHER, of = listOf(0), outcome = RevealOutcome.MATCH),
        )

        fun prompt(id: String, game: String) = GamePrompt(
            id = id,
            version = 1,
            game = game,
            kind = null,
            text = "Would you rather…",
            options = listOf("Stay in", "Go out"),
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
    }
}
