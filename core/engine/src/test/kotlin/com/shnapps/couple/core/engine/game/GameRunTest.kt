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
import com.shnapps.couple.core.model.PromptKind
import com.shnapps.couple.core.model.RevealOutcome
import com.shnapps.couple.core.model.Scoring
import com.shnapps.couple.core.model.StepAudience
import com.shnapps.couple.core.model.StepOptions
import com.shnapps.couple.core.model.StepSeconds
import com.shnapps.couple.core.model.StepText
import com.shnapps.couple.core.model.TurnOrder
import org.junit.Test

/**
 * The game interpreter (BUILD_PROMPT.md §11), walked through every primitive and every reveal
 * with small games shaped like the shipped ones. :core:data's file test plays the real games
 * from the bundle through the same interpreter.
 */
class GameRunTest {

    private val players = GamePlayers("Alex", "Sam")

    // ---- choices, matches and hand-offs -----------------------------------------------------

    @Test
    fun `private answers are each taken behind a hand-off, then gathered for the reveal`() {
        var run = start(wouldYouRather, rounds = listOf(listOf(choicePrompt("a"))))

        assertThat(run.moment).isEqualTo(GameMoment.HandOff(0, seat = 0))
        run = run.proceed()
        val first = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Choice
        assertThat(first.seat).isEqualTo(0)
        assertThat(first.options.map { it.label }).containsExactly("Stay in", "Go out").inOrder()
        run = run.submit(Answer.Picked("1"))!!

        assertThat(run.moment).isEqualTo(GameMoment.HandOff(0, seat = 1))
        run = run.proceed().submit(Answer.Picked("1"))!!
        assertThat(run.moment).isEqualTo(GameMoment.Gather(0))
        run = run.proceed()

        val reveal = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Reveal
        assertThat(reveal.verdict).isEqualTo(Verdict.Matched(true))
        assertThat(reveal.shown.map { it.label }).containsExactly("Go out", "Go out")
        run = run.submit(Answer.Done)!!
        assertThat(run.moment).isEqualTo(GameMoment.RoundOver(0, playedPromptId = "a"))
        assertThat(run.score.matches).isEqualTo(1)
        assertThat(run.score.compared).isEqualTo(1)
    }

    @Test
    fun `turns alternate - the second round starts with the other partner`() {
        var run = start(wouldYouRather, rounds = listOf(listOf(choicePrompt("a")), listOf(choicePrompt("b"))))
        run = playMatchRound(run, "0", "1").nextRound()
        assertThat(run.moment).isEqualTo(GameMoment.HandOff(1, seat = 1))
        assertThat(run.score).isEqualTo(Score(matches = 0, compared = 1))
    }

    @Test
    fun `whoever goes first can be the partner`() {
        val run = start(wouldYouRather, rounds = listOf(listOf(choicePrompt("a"))), firstSeat = 1)
        assertThat(run.moment).isEqualTo(GameMoment.HandOff(0, seat = 1))
    }

    @Test
    fun `an answer outside the turn's contract is refused`() {
        val run = start(wouldYouRather, rounds = listOf(listOf(choicePrompt("a")))).proceed()
        assertThat(run.submit(Answer.Picked("7"))).isNull()
        assertThat(run.submit(Answer.Done)).isNull()
        assertThat(run.submit(Answer.Text("Go out"))).isNull()
    }

    // ---- votes: "me" and "you" from the voter's side ------------------------------------------

    @Test
    fun `two votes agree when they name the same partner, whoever cast them`() {
        var run = start(meOrYou, rounds = listOf(listOf(plainPrompt("m"))))
        run = run.proceed()
        val vote = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Vote
        assertThat(vote.options.map { it.label }).containsExactly("Alex", "Sam").inOrder()
        run = run.submit(Answer.Picked("ME"))!!.proceed()
        val theirs = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Vote
        assertThat(theirs.options.map { it.label }).containsExactly("Sam", "Alex").inOrder()
        // Sam says "you": Alex. Both name Alex.
        run = run.submit(Answer.Picked("YOU"))!!.proceed()
        val reveal = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Reveal
        assertThat(reveal.verdict).isEqualTo(Verdict.Matched(true))
        assertThat(reveal.shown.map { it.label }).containsExactly("Alex", "Alex")
    }

    /**
     * The bug a couple found on a device.
     *
     * With no names given, the stand-ins used to be the words "You" and "Them" — pronouns used as
     * fixed names. On the *second* player's turn the option meaning themselves rendered as "Them"
     * and the one meaning their partner rendered as "You", so the screen said the opposite of what
     * it meant, and two people who agreed looked like they had not.
     *
     * A vote with no names stays relative: both of them see "Me" and "You" from their own side,
     * which is unambiguous because only the person whose turn it is can see it. What the reveal
     * then compares is the *seat* each vote names, which was always right.
     */
    @Test
    fun `an unnamed vote reads from the voter's own side, and still agrees`() {
        var run = start(meOrYou, rounds = listOf(listOf(plainPrompt("m"))), players = GamePlayers.UNNAMED)
            .proceed()

        val first = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Vote
        assertThat(first.options.map { it.label }).containsExactly("Me", "You").inOrder()

        // The first player says "me". The second says "you" — and means the first player.
        run = run.submit(Answer.Picked("ME"))!!.proceed()
        val second = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Vote
        assertThat(second.options.map { it.label }).containsExactly("Me", "You").inOrder()

        run = run.submit(Answer.Picked("YOU"))!!.proceed()
        val reveal = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Reveal
        assertThat(reveal.verdict).isEqualTo(Verdict.Matched(true))
    }

    /** Both naming themselves is two different people, however it is worded. */
    @Test
    fun `an unnamed vote disagrees when each names themselves`() {
        var run = start(meOrYou, rounds = listOf(listOf(plainPrompt("m"))), players = GamePlayers.UNNAMED)
            .proceed()
        run = run.submit(Answer.Picked("ME"))!!.proceed().submit(Answer.Picked("ME"))!!.proceed()

        val reveal = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Reveal
        assertThat(reveal.verdict).isEqualTo(Verdict.Matched(false))
    }

    /** A stand-in name is never a pronoun: that is the whole of what went wrong. */
    @Test
    fun `the stand-in names are not pronouns`() {
        val pronouns = setOf("you", "them", "me", "us", "they", "him", "her")
        assertThat(pronouns).doesNotContain(GamePlayers.UNNAMED.first.lowercase())
        assertThat(pronouns).doesNotContain(GamePlayers.UNNAMED.second.lowercase())
        assertThat(GamePlayers.UNNAMED.named).isFalse()
    }

    // ---- predictions ---------------------------------------------------------------------------

    @Test
    fun `a right prediction scores for whoever predicted it`() {
        var run = start(predictMyAnswer, rounds = listOf(listOf(choicePrompt("p"))))
        run = run.proceed().submit(Answer.Picked("0"))!!
        assertThat(run.moment).isEqualTo(GameMoment.HandOff(0, seat = 1))
        run = run.proceed()
        val prediction = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Prediction
        assertThat(prediction.text).isEqualTo("What did they choose?")
        assertThat(prediction.options!!.map { it.label }).containsExactly("Stay in", "Go out").inOrder()
        run = run.submit(Answer.Picked("0"))!!.proceed()
        val reveal = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Reveal
        assertThat(reveal.verdict).isEqualTo(Verdict.Point(1))
        run = run.submit(Answer.Done)!!
        assertThat(run.score.points).containsExactly(0, 1).inOrder()
    }

    @Test
    fun `a wrong prediction scores for nobody`() {
        var run = start(predictMyAnswer, rounds = listOf(listOf(choicePrompt("p"))))
        run = run.proceed().submit(Answer.Picked("0"))!!.proceed().submit(Answer.Picked("1"))!!.proceed()
        val reveal = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Reveal
        assertThat(reveal.verdict).isEqualTo(Verdict.Point(null))
    }

    @Test
    fun `a typed guess is judged by the one it was about`() {
        var run = start(whoKnowsWho, rounds = listOf(listOf(plainPrompt("k"))))
        run = run.proceed()
        assertThat(run.submit(Answer.Text("  "))).isNull()
        run = run.submit(Answer.Text("Pasta"))!!.proceed()
        val guess = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Prediction
        assertThat(guess.options).isNull()
        run = run.submit(Answer.Text("Pizza"))!!
        assertThat(run.moment).isEqualTo(GameMoment.Gather(0))
        run = run.proceed()

        val reveal = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Reveal
        assertThat(reveal.seat).isEqualTo(0)
        assertThat(reveal.verdict).isEqualTo(Verdict.AwaitingJudge)
        assertThat(reveal.shown.map { it.label }).containsExactly("Pasta", "Pizza").inOrder()
        assertThat(run.submit(Answer.Done)).isNull()
        run = run.submit(Answer.Judged(close = true))!!
        assertThat(run.results.single().verdict).isEqualTo(Verdict.Point(1))
    }

    // ---- statements -----------------------------------------------------------------------------

    @Test
    fun `the lie, found or not, scores for the finder or the liar`() {
        var run = start(twoTruths, rounds = listOf(listOf(plainPrompt("t")), listOf(plainPrompt("u"))))
        run = run.proceed()
        assertThat(run.submit(Answer.Statements(listOf("a", "b"), lie = 0))).isNull()
        assertThat(run.submit(Answer.Statements(listOf("a", "b", " "), lie = 0))).isNull()
        assertThat(run.submit(Answer.Statements(listOf("a", "b", "c"), lie = 3))).isNull()
        run = run.submit(Answer.Statements(listOf("I can juggle", "I hate tea", "I sang on stage"), lie = 1))!!
        assertThat(run.moment).isEqualTo(GameMoment.Gather(0))
        run = run.proceed()
        val choice = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Choice
        assertThat(choice.seat).isEqualTo(1)
        assertThat(choice.options.map { it.label }).containsExactly(
            "I can juggle",
            "I hate tea",
            "I sang on stage",
        ).inOrder()
        run = run.submit(Answer.Picked("1"))!!
        val found = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Reveal
        assertThat(found.verdict).isEqualTo(Verdict.Point(1))
        assertThat(found.shown.single { it.highlighted }.label).isEqualTo("I hate tea")
        run = run.submit(Answer.Done)!!.nextRound()

        // Round two is Sam's: Alex guesses, and misses.
        run = run.proceed().submit(Answer.Statements(listOf("x", "y", "z"), lie = 2))!!.proceed()
        run = run.submit(Answer.Picked("0"))!!
        val missed = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Reveal
        assertThat(missed.verdict).isEqualTo(Verdict.Point(1))
        run = run.submit(Answer.Done)!!
        assertThat(run.score.points).containsExactly(0, 2).inOrder()
    }

    // ---- kinds: truth or dare -------------------------------------------------------------------

    @Test
    fun `the chosen kind decides which prompt is played, and is reported as played`() {
        val truth = kindPrompt("t1", PromptKind.TRUTH)
        val dare = kindPrompt("d1", PromptKind.DARE)
        var run = start(truthOrDare, rounds = listOf(listOf(truth, dare)))
        val choice = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Choice
        assertThat(choice.options.map { it.label }).containsExactly("Truth", "Dare").inOrder()
        assertThat((run.moment as GameMoment.Turn).canSkip).isFalse()
        run = run.submit(Answer.Picked("DARE"))!!
        val challenge = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Challenge
        assertThat(challenge.text).isEqualTo(dare.text)
        assertThat(challenge.kind).isEqualTo(PromptKind.DARE)
        run = run.submit(Answer.Done)!!
        val reaction = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Reaction
        assertThat(reaction.seat).isEqualTo(1)
        run = run.submit(Answer.Reacted(ChapterReaction.LOVED))!!
        assertThat(run.moment).isEqualTo(GameMoment.RoundOver(0, playedPromptId = "d1"))
    }

    // ---- battles: timers and a named winner --------------------------------------------------------

    @Test
    fun `a battle times its prompt, and the couple names the winner or calls a tie`() {
        val first = plainPrompt("b1", seconds = 90)
        var run = start(coupleBattle, rounds = listOf(listOf(first), listOf(plainPrompt("b2", seconds = 30))))
        run = run.submit(Answer.Done)!!
        val timer = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Timer
        assertThat(timer.seconds).isEqualTo(90)
        assertThat(timer.caption).isEqualTo(first.text)
        run = run.submit(Answer.Done)!!
        val vote = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Vote
        assertThat(vote.seat).isNull()
        assertThat(vote.options.map { it.label }).containsExactly("Alex", "Sam", "A tie").inOrder()
        run = run.submit(Answer.Picked("YOU"))!!
        assertThat(run.reveal().verdict).isEqualTo(Verdict.Point(1))
        run = run.submit(Answer.Done)!!.nextRound()

        run = run.submit(Answer.Done)!!.submit(Answer.Done)!!.submit(Answer.Picked("TIE"))!!
        assertThat(run.reveal().verdict).isEqualTo(Verdict.Point(null))
        run = run.submit(Answer.Done)!!.nextRound()
        assertThat(run.moment).isEqualTo(GameMoment.Finished(2, Score(points = listOf(0, 1))))
    }

    // ---- "Not this one" ---------------------------------------------------------------------------

    @Test
    fun `not this one swaps the prompt and takes the round up again from its first showing`() {
        val alternate = choicePrompt("alt")
        var run = start(wouldYouRather, rounds = listOf(listOf(choicePrompt("a"))), alternates = listOf(alternate))
        run = run.proceed().submit(Answer.Picked("0"))!!.proceed()
        assertThat((run.moment as GameMoment.Turn).canSkip).isTrue()

        run = run.skipPrompt()
        assertThat(run.moment).isEqualTo(GameMoment.HandOff(0, seat = 0))
        assertThat(run.promptInPlay()?.id).isEqualTo(alternate.id)
        // The first answer was to the prompt swapped out, so it is asked again.
        run = run.proceed().submit(Answer.Picked("1"))!!.proceed().submit(Answer.Picked("1"))!!.proceed()
        assertThat(run.reveal().verdict).isEqualTo(Verdict.Matched(true))
    }

    @Test
    fun `with nothing to swap in, the round is passed over`() {
        var run = start(wouldYouRather, rounds = listOf(listOf(choicePrompt("a")), listOf(choicePrompt("b")))).proceed()
        run = run.skipPrompt()
        assertThat(run.moment).isEqualTo(GameMoment.RoundOver(0, playedPromptId = null))
        assertThat(run.nextRound().moment).isEqualTo(GameMoment.HandOff(1, seat = 1))
    }

    @Test
    fun `a swap keeps to the prompt's kind and never climbs above it`() {
        val dare = kindPrompt("d1", PromptKind.DARE, Intensity.FLIRTY)
        val alternates = listOf(
            kindPrompt("t_alt", PromptKind.TRUTH),
            kindPrompt("d_hot", PromptKind.DARE, Intensity.HOT),
            kindPrompt("d_soft", PromptKind.DARE, Intensity.MILD),
            kindPrompt("d_flirty", PromptKind.DARE, Intensity.FLIRTY),
        )
        var run = start(
            truthOrDare,
            rounds = listOf(listOf(kindPrompt("t1", PromptKind.TRUTH), dare)),
            alternates = alternates,
        )
        run = run.submit(Answer.Picked("DARE"))!!.skipPrompt()
        // Taken up again at the dare: the choice of kind stands.
        val challenge = (run.moment as GameMoment.Turn).primitive as RoundPrimitive.Challenge
        assertThat(challenge.text).isEqualTo(alternates[3].text)
        run = run.skipPrompt()
        assertThat(run.challenge().text).isEqualTo(alternates[2].text)
        assertThat(run.skipPrompt().moment).isEqualTo(GameMoment.RoundOver(0, playedPromptId = null))
    }

    @Test
    fun `a reveal, a timer or a reaction cannot be skipped`() {
        var run = start(
            coupleBattle,
            rounds = listOf(listOf(plainPrompt("b1", seconds = 30))),
            alternates = listOf(plainPrompt("b2", seconds = 30)),
        )
        run = run.submit(Answer.Done)!!
        assertThat((run.moment as GameMoment.Turn).canSkip).isFalse()
        assertThat(run.skipPrompt()).isEqualTo(run)
    }

    // ---- limits set mid-game ---------------------------------------------------------------------

    @Test
    fun `a blocked prompt ahead is swapped for a released one, or its round passed over`() {
        val later = choicePrompt("later", themes = setOf("theme_x"))
        val last = choicePrompt("last", themes = setOf("theme_x"))
        val alternate = choicePrompt("alt")
        var run = start(
            wouldYouRather,
            rounds = listOf(listOf(choicePrompt("now")), listOf(later), listOf(last)),
            alternates = listOf(alternate, choicePrompt("alt_x", themes = setOf("theme_x"))),
        )
        assertThat(run.promptsAbout("theme_x")).containsExactly("later", "last", "alt_x")
        run = run.withBlocked(run.promptsAbout("theme_x"), fromRound = 1)

        run = playMatchRound(run, "0", "0").nextRound()
        assertThat(run.promptInPlay()?.id).isEqualTo(alternate.id)
        run = playMatchRound(run, "0", "0").nextRound()
        assertThat(run.unplayable).containsExactly(2)
        assertThat(run.moment).isEqualTo(GameMoment.RoundOver(2, playedPromptId = null))
        assertThat(run.nextRound().moment).isInstanceOf(GameMoment.Finished::class.java)
    }

    @Test
    fun `a limit that reaches the round under way starts it again`() {
        var run = start(
            wouldYouRather,
            rounds = listOf(listOf(choicePrompt("now", themes = setOf("theme_x")))),
            alternates = listOf(choicePrompt("alt")),
        )
        run = run.proceed().submit(Answer.Picked("0"))!!
        run = run.withBlocked(setOf("now"))
        assertThat(run.moment).isEqualTo(GameMoment.HandOff(0, seat = 0))
        assertThat(run.promptInPlay()?.id).isEqualTo("alt")
    }

    // ---- what the server released, and nothing else -----------------------------------------------

    @Test
    fun `only what the server released, and could have permitted, is ever played`() {
        val released = ReleasedGame.fromServer(
            ServerGame(
                gameId = "SHAPED_LIKE_TRUTH_OR_DARE",
                rounds = listOf(
                    listOf("t1", "d1"),
                    listOf("t2", "d_draft"),
                    listOf("d1b", "t1b"),
                    listOf("t3", "d_hot"),
                ),
                alternates = listOf("d_apart", "t_alt", "t_alt"),
                effectiveIntensity = 3,
            ),
            truthOrDare,
        ) { id ->
            when (id) {
                "d_draft" -> kindPrompt(id, PromptKind.DARE).copy(status = ContentStatus.DRAFT)
                "d_hot" -> kindPrompt(id, PromptKind.DARE, Intensity.HOT)
                "d_apart" -> kindPrompt(id, PromptKind.DARE).copy(modes = setOf(Mode.APART))
                "unknown" -> null
                else -> kindPrompt(id, if (id.startsWith("t")) PromptKind.TRUTH else PromptKind.DARE)
            }
        }
        assertThat(released.rounds.map { it.playable }).containsExactly(true, false, false, false).inOrder()
        assertThat(released.alternates.map { it.id }).containsExactly("t_alt")

        val run = GameRun.start(released, players)
        assertThat(run.unplayable).containsExactly(1, 2, 3)
    }

    @Test
    fun `a game restarted mid-session picks up at the session's round`() {
        val run = start(wouldYouRather, rounds = List(4) { listOf(choicePrompt("r$it")) }, fromRound = 2)
        assertThat(run.moment).isEqualTo(GameMoment.HandOff(2, seat = 0))
        assertThat(run.promptInPlay()?.id).isEqualTo("r2")
    }

    // ---- helpers -------------------------------------------------------------------------------------

    private fun GameRun.reveal() = (moment as GameMoment.Turn).primitive as RoundPrimitive.Reveal

    private fun GameRun.challenge() = (moment as GameMoment.Turn).primitive as RoundPrimitive.Challenge

    private fun playMatchRound(start: GameRun, mine: String, theirs: String): GameRun {
        var run = start
        assertThat(run.moment).isEqualTo(GameMoment.HandOff(run.round, seat = run.activeSeat()))
        run = run.proceed().submit(Answer.Picked(mine))!!.proceed().submit(Answer.Picked(theirs))!!.proceed()
        return run.submit(Answer.Done)!!
    }

    private fun start(
        game: Game,
        rounds: List<List<GamePrompt>>,
        alternates: List<GamePrompt> = emptyList(),
        firstSeat: Int = 0,
        fromRound: Int = 0,
        players: GamePlayers = this.players,
    ): GameRun {
        // Every prompt belongs to the game under test: anything else would be dropped as released for another.
        val all = (rounds.flatten() + alternates).associate { it.id to it.copy(game = game.id) }
        val released = ReleasedGame.fromServer(
            ServerGame(
                game.id,
                rounds.map { round -> round.map { it.id } },
                alternates.map { it.id },
                effectiveIntensity = 5,
            ),
            game,
        ) { all[it] }
        return GameRun.start(released, players, firstSeat, fromRound)
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
            seconds: StepSeconds? = null,
            kindFrom: Int? = null,
        ) = GameStep(primitive, who, isPrivate, text, options, answer, of, outcome, seconds, kindFrom)

        fun game(
            id: String,
            turns: TurnOrder,
            scoring: Scoring,
            vararg steps: GameStep,
            kinds: List<PromptKind> = emptyList(),
        ) =
            Game(id, id, "", "", setOf(Mode.TOGETHER), rounds = 8, turns, scoring, kinds, steps.toList())

        val wouldYouRather = game(
            "SHAPED_LIKE_WOULD_YOU_RATHER",
            TurnOrder.SHARED,
            Scoring.MATCHES,
            step(
                GamePrimitive.CHOICE,
                StepAudience.EACH,
                isPrivate = true,
                text = StepText.Prompt,
                options = StepOptions.FromPrompt,
            ),
            step(GamePrimitive.REVEAL, StepAudience.TOGETHER, of = listOf(0), outcome = RevealOutcome.MATCH),
        )

        val meOrYou = game(
            "SHAPED_LIKE_ME_OR_YOU",
            TurnOrder.SHARED,
            Scoring.MATCHES,
            step(
                GamePrimitive.VOTE,
                StepAudience.EACH,
                isPrivate = true,
                text = StepText.Prompt,
                options = StepOptions.Fixed(listOf("ME", "YOU")),
            ),
            step(GamePrimitive.REVEAL, StepAudience.TOGETHER, of = listOf(0), outcome = RevealOutcome.AGREEMENT),
        )

        val predictMyAnswer = game(
            "SHAPED_LIKE_PREDICT_MY_ANSWER",
            TurnOrder.ALTERNATE,
            Scoring.POINTS,
            step(
                GamePrimitive.CHOICE,
                StepAudience.ACTIVE,
                isPrivate = true,
                text = StepText.Prompt,
                options = StepOptions.FromPrompt,
            ),
            step(
                GamePrimitive.PREDICTION,
                StepAudience.OTHER,
                isPrivate = true,
                text = StepText.Literal("What did they choose?"),
                options = StepOptions.FromPrompt,
                of = listOf(0),
            ),
            step(GamePrimitive.REVEAL, StepAudience.TOGETHER, of = listOf(0, 1), outcome = RevealOutcome.PREDICTED),
        )

        val whoKnowsWho = game(
            "SHAPED_LIKE_WHO_KNOWS_WHO",
            TurnOrder.ALTERNATE,
            Scoring.POINTS,
            step(
                GamePrimitive.QUESTION,
                StepAudience.ACTIVE,
                isPrivate = true,
                text = StepText.Prompt,
                answer = AnswerKind.TEXT,
            ),
            step(
                GamePrimitive.PREDICTION,
                StepAudience.OTHER,
                isPrivate = true,
                text = StepText.Literal("What did they answer?"),
                answer = AnswerKind.TEXT,
                of = listOf(0),
            ),
            step(GamePrimitive.REVEAL, StepAudience.ACTIVE, of = listOf(0, 1), outcome = RevealOutcome.JUDGED),
        )

        val twoTruths = game(
            "SHAPED_LIKE_TWO_TRUTHS",
            TurnOrder.ALTERNATE,
            Scoring.POINTS,
            step(
                GamePrimitive.QUESTION,
                StepAudience.ACTIVE,
                isPrivate = true,
                text = StepText.Prompt,
                answer = AnswerKind.STATEMENTS,
            ),
            step(
                GamePrimitive.CHOICE,
                StepAudience.OTHER,
                text = StepText.Literal("Which one is the lie?"),
                options = StepOptions.FromStatements,
                of = listOf(0),
            ),
            step(GamePrimitive.REVEAL, StepAudience.TOGETHER, of = listOf(0, 1), outcome = RevealOutcome.LIE_FOUND),
        )

        val truthOrDare = game(
            "SHAPED_LIKE_TRUTH_OR_DARE",
            TurnOrder.ALTERNATE,
            Scoring.NONE,
            step(
                GamePrimitive.CHOICE,
                StepAudience.ACTIVE,
                text = StepText.Literal("Truth or dare?"),
                options = StepOptions.Fixed(listOf("TRUTH", "DARE")),
            ),
            step(GamePrimitive.CHALLENGE, StepAudience.ACTIVE, text = StepText.Prompt, kindFrom = 0),
            step(GamePrimitive.REACTION, StepAudience.OTHER, text = StepText.Literal("How did they do?")),
            kinds = listOf(PromptKind.TRUTH, PromptKind.DARE),
        )

        val coupleBattle = game(
            "SHAPED_LIKE_COUPLE_BATTLE",
            TurnOrder.SHARED,
            Scoring.POINTS,
            step(GamePrimitive.CHALLENGE, StepAudience.TOGETHER, text = StepText.Prompt),
            step(GamePrimitive.TIMER, StepAudience.TOGETHER, seconds = StepSeconds.FromPrompt),
            step(
                GamePrimitive.VOTE,
                StepAudience.TOGETHER,
                text = StepText.Literal("Who won?"),
                options = StepOptions.Fixed(listOf("ME", "YOU", "TIE")),
            ),
            step(GamePrimitive.REVEAL, StepAudience.TOGETHER, of = listOf(2), outcome = RevealOutcome.WINNER),
        )

        fun prompt(
            id: String,
            game: String,
            intensity: Intensity = Intensity.MILD,
            kind: PromptKind? = null,
            options: List<String> = emptyList(),
            seconds: Int? = null,
            themes: Set<String> = setOf("mood_gentle"),
        ) = GamePrompt(
            id = id,
            version = 1,
            game = game,
            kind = kind,
            text = "The prompt $id.",
            options = options,
            seconds = seconds,
            category = "communication",
            tags = setOf("game"),
            intensity = intensity,
            modes = setOf(Mode.TOGETHER),
            moods = emptySet(),
            requiredMutualPreferences = emptySet(),
            boostedByPreferences = emptySet(),
            excludedByBoundaries = themes,
            status = ContentStatus.PUBLISHED,
        )

        fun choicePrompt(id: String, themes: Set<String> = setOf("mood_gentle")) =
            prompt(id, "ANY", options = listOf("Stay in", "Go out"), themes = themes)

        fun plainPrompt(id: String, seconds: Int? = null) = prompt(id, "ANY", seconds = seconds)

        fun kindPrompt(id: String, kind: PromptKind, intensity: Intensity = Intensity.MILD) =
            prompt(id, "SHAPED_LIKE_TRUTH_OR_DARE", intensity, kind)
    }
}
