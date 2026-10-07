package com.shnapps.couple.core.engine.game

import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.GamePrompt
import com.shnapps.couple.core.model.RevealOutcome

/** Where a game being played stands, and what the phone shows for it. */
sealed interface GameMoment {
    val round: Int

    /** Pass the phone to [seat]: only they should look at what comes next. */
    data class HandOff(override val round: Int, val seat: Int) : GameMoment

    /** The private answers are in: the phone goes where both can see it. */
    data class Gather(override val round: Int) : GameMoment

    /** A primitive to take. [canSkip]: it shows the round's prompt, so "Not this one" applies. */
    data class Turn(override val round: Int, val primitive: RoundPrimitive, val canSkip: Boolean) : GameMoment

    /** On two phones: what the other partner is answering, while this one waits (§12). */
    data class Waiting(override val round: Int, val seat: Int) : GameMoment

    /**
     * The round is over. [playedPromptId] is what was played — the dare, not the truth, when
     * the dare was chosen — or null when the round was passed over.
     */
    data class RoundOver(override val round: Int, val playedPromptId: String?) : GameMoment

    data class Finished(override val round: Int, val score: Score) : GameMoment
}

/** Points per seat, or matches out of rounds compared, as the game scores. */
data class Score(val points: List<Int> = listOf(0, 0), val matches: Int = 0, val compared: Int = 0)

/** What one round's reveal came to. */
data class RoundResult(val round: Int, val outcome: RevealOutcome, val verdict: Verdict)

/**
 * A game being played on one phone, passed between the partners (BUILD_PROMPT.md §11): the
 * interpreter every game runs on. A game's definition is data — steps over primitives — and
 * this plays any of them: whose turn it is, when the phone changes hands, what each reveal
 * compares and who scores. Nothing here knows any game by name, and no screen does either.
 *
 * Immutable: every move returns the next run, so a test can walk any game from start to
 * finish without a device. The answers stay in it and nowhere else (DECISIONS.md D-069).
 *
 * Only ever started from a [ReleasedGame]: the prompts the server released, and the
 * alternates it released for "Not this one" (§3.1, §5.5).
 */
@Suppress("TooManyFunctions") // The interpreter: one function per control a game has.
class GameRun private constructor(private val state: RunState) {

    private val reading get() = RoundReading(state)

    val game: Game get() = state.game
    val players: GamePlayers get() = state.players
    val round: Int get() = state.round
    val roundCount: Int get() = state.rounds.size
    val results: List<RoundResult> get() = state.results
    val finished: Boolean get() = state.round >= state.rounds.size

    /** Rounds passed over: nothing released could play them within the couple's limits. */
    val unplayable: Set<Int> get() = state.unplayable

    val score: Score get() = scoreOf(state.results)

    /** The seat whose turn round [index] is: turns alternate from whoever goes first. */
    fun activeSeat(index: Int = round): Int = state.activeSeat(index)

    val moment: GameMoment
        get() = when {
            finished -> GameMoment.Finished(round, score)
            round in state.unplayable -> GameMoment.RoundOver(round, playedPromptId = null)
            else -> reading.moment()
        }

    /** The prompt the round is played with: the chosen kind's, once chosen, else the first. */
    fun promptInPlay(): GamePrompt? = reading.promptInPlay()

    /** Past a hand-off, a gathering, or the wait for the other phone's answer. */
    fun proceed(): GameRun = when (moment) {
        is GameMoment.HandOff, is GameMoment.Gather, is GameMoment.Waiting ->
            next(state.copy(position = state.position + 1))
        else -> this
    }

    /** Takes [answer] for the current turn; null when the turn's contract refuses it. */
    fun submit(answer: Answer): GameRun? {
        val turn = moment as? GameMoment.Turn ?: return null
        val primitive = turn.primitive
        if (!primitive.accepts(answer)) return null
        val answered = state.copy(answers = state.answers + (AnswerKey(primitive.step, primitive.seat) to answer))
        val results = if (primitive is RoundPrimitive.Reveal) {
            // Read again with the answer in, so a judged reveal records its judgement.
            val settled = RoundReading(answered).primitive(Slot.Take(primitive.step, primitive.seat))
            state.results + RoundResult(round, primitive.outcome, (settled as RoundPrimitive.Reveal).verdict)
        } else {
            state.results
        }
        return next(answered.copy(position = state.position + 1, results = results))
    }

    /** From the end of a round, the start of the next one — or the end of the game. */
    fun nextRound(): GameRun = if (moment is GameMoment.RoundOver) {
        next(state.copy(round = round + 1, position = 0, answers = emptyMap()))
    } else {
        this
    }

    /**
     * "Not this one" (§3.1): the prompt in play swapped for a released alternate of its kind,
     * no more intense than it, and the round taken up again from where that prompt first
     * showed. With nothing to swap in, the round is passed over.
     */
    fun skipPrompt(): GameRun {
        val turn = moment as? GameMoment.Turn
        val current = promptInPlay()
        if (turn == null || !turn.canSkip || current == null) return this
        val avoid = state.avoid + current.id
        val replacement = replacementFor(current, avoid)
            ?: return next(state.copy(unplayable = state.unplayable + round, avoid = avoid))
        val prompts = state.rounds[round].map { if (it.id == current.id) replacement else it }
        val swapped = state.copy(
            rounds = state.rounds.mapIndexed { at, existing -> if (at == round) prompts else existing },
            alternates = state.alternates - replacement,
            avoid = avoid,
        )
        return next(swapped).restartedAtPrompt()
    }

    /**
     * [blocked] is what the couple's limits no longer permit (DECISIONS.md D-062). From round
     * [fromRound] on, every prompt it names is swapped for a released alternate that is still
     * allowed; a round that cannot be is passed over. A round under way starts again.
     */
    fun withBlocked(blocked: Set<String>, fromRound: Int = round): GameRun {
        if (blocked.isEmpty()) return this
        val affected = state.rounds.indices.filter { index ->
            index >= fromRound && index !in state.unplayable && state.rounds[index].any { it.id in blocked }
        }
        val allowed = state.alternates.filterNot { it.id in blocked }
        var run = next(state.copy(alternates = allowed, avoid = state.avoid + blocked))
        for (index in affected) {
            run = run.replacingBlocked(index, blocked)
            if (index == round) run = next(run.state.copy(position = 0, answers = emptyMap()))
        }
        return run
    }

    /** The seat this phone plays, or null when it is passed between the partners. */
    val phone: Int? get() = state.phone

    /**
     * The same game, now that it knows who is playing it (D-121).
     *
     * Names are asked for at the first hand-off, which is in the middle of a round that has
     * already started, so this carries every answer and result across rather than restarting.
     * Nothing about the game changes — only what the screen is able to call the two of them.
     */
    fun withPlayers(players: GamePlayers): GameRun = GameRun(state.copy(players = players))

    /**
     * The answer this phone has to send for the round it is on (§12.2): the last one it gave that
     * another phone could not know, or null when everything it did was its own to do.
     */
    fun answerToSend(): Answer? = reading.answerToSend()

    /**
     * Whether this round asks [seat] for anything at all (§12.2).
     *
     * The protocol's own question: a round is revealed when both phones have answered it, so a
     * game where one seat is never asked cannot be played apart — it would wait for an answer that
     * is never coming. `Game.playableApart` is the same rule read off the steps; this is it read
     * off a real round, which is what the content test holds the two to.
     */
    fun asksSeat(seat: Int): Boolean = reading.answeringStepFor(seat) != null

    /**
     * [answer], given by [seat] to step [step] on the other phone, as the server's reveal reported
     * it. The reveal is then read exactly as it is when one phone holds both answers.
     */
    fun withPartnerAnswer(step: Int, seat: Int?, answer: Answer): GameRun =
        next(state.copy(answers = state.answers + (AnswerKey(step, seat) to answer)))

    /**
     * What the server says [seat] answered this round, put where the round expects it — without
     * the phone needing to know the shape of the game.
     */
    fun receive(seat: Int, answer: Answer): GameRun {
        val take = reading.answeringStepFor(seat) ?: return this
        return withPartnerAnswer(take.step, take.seat ?: seat, answer)
    }

    /**
     * This phone's answer for the round is already with the server (§12.3): restarted mid-round,
     * it waits for the reveal rather than answering a second time. What it answered comes back
     * in the reveal, which carries both.
     */
    fun awaitingReveal(): GameRun {
        val wait = reading.slots().indexOfFirst { it is Slot.Wait }
        return if (wait < 0) this else next(state.copy(position = maxOf(state.position, wait)))
    }

    /** The released prompts still to come that are about [themeId]: what keeping it out removes. */
    fun promptsAbout(themeId: String): Set<String> =
        (state.rounds.drop(round).flatten() + state.alternates)
            .filter { themeId in it.excludedByBoundaries }
            .mapTo(HashSet()) { it.id }

    /** A released alternate for [prompt]: its kind, no more intense, the most intense such. */
    private fun replacementFor(prompt: GamePrompt, avoid: Set<String>): GamePrompt? = state.alternates
        .filter { it.id !in avoid && it.kind == prompt.kind && it.intensity.level <= prompt.intensity.level }
        .maxByOrNull { it.intensity.level }

    private fun replacingBlocked(index: Int, blocked: Set<String>): GameRun {
        var alternates = state.alternates
        val prompts = state.rounds[index].map { prompt ->
            if (prompt.id !in blocked) return@map prompt
            val replacement = next(state.copy(alternates = alternates)).replacementFor(prompt, state.avoid)
                ?: return next(state.copy(alternates = alternates, unplayable = state.unplayable + index))
            alternates = alternates - replacement
            replacement
        }
        val rounds = state.rounds.mapIndexed { at, existing -> if (at == index) prompts else existing }
        return next(state.copy(rounds = rounds, alternates = alternates))
    }

    /** The round taken up again from where its prompt first shows, and what followed forgotten. */
    private fun restartedAtPrompt(): GameRun {
        val slots = reading.slots()
        val at = slots
            .indexOfFirst { it is Slot.Take && reading.showsPrompt(game.steps[it.step]) }
            .coerceAtLeast(0)
        val position = if (at > 0 && slots[at - 1].let { it is Slot.HandOff || it == Slot.Gather }) at - 1 else at
        val fromStep = (slots[at] as? Slot.Take)?.step ?: 0
        return next(state.copy(position = position, answers = state.answers.filterKeys { it.step < fromStep }))
    }

    private fun next(state: RunState): GameRun = GameRun(state)

    override fun equals(other: Any?): Boolean = other is GameRun && other.state == state

    override fun hashCode(): Int = state.hashCode()

    override fun toString(): String = "GameRun(${game.id}, round=$round, moment=$moment)"

    companion object {
        /** A timer whose game gives it no length. */
        const val DEFAULT_SECONDS = 60
        const val TIE = "A tie"

        /**
         * A game ready to play from round [fromRound] — the first, or where a session already
         * stands after the phone was restarted. [firstSeat] goes first: 0 is whoever started it.
         * [phone] is the seat this phone plays when the couple has one each (§12); left out, the
         * phone is passed between them and plays both.
         */
        fun start(
            released: ReleasedGame,
            players: GamePlayers,
            firstSeat: Int = 0,
            fromRound: Int = 0,
            phone: Int? = null,
        ): GameRun =
            GameRun(
                RunState(
                    game = released.game,
                    players = players,
                    firstSeat = firstSeat.coerceIn(0, 1),
                    phone = phone?.coerceIn(0, 1),
                    rounds = released.rounds.map { it.prompts },
                    alternates = released.alternates,
                    unplayable = released.rounds.filterNot { it.playable }.mapTo(HashSet()) { it.index },
                    round = fromRound.coerceIn(0, released.rounds.size),
                    position = 0,
                    answers = emptyMap(),
                    results = emptyList(),
                    avoid = emptySet(),
                ),
            )
    }
}

/** A game's score from its reveals: a point per [Verdict.Point], and matches out of those compared. */
internal fun scoreOf(results: List<RoundResult>): Score {
    val points = IntArray(2)
    var matches = 0
    var compared = 0
    for (result in results) {
        when (val verdict = result.verdict) {
            is Verdict.Matched -> {
                compared += 1
                if (verdict.matched) matches += 1
            }
            is Verdict.Point -> verdict.seat?.let { points[it] += 1 }
            // A gap counts as a round compared, and as a match when they landed on the same
            // number. Nobody scores a point for wanting something more than their partner.
            is Verdict.Apart -> {
                compared += 1
                if (verdict.gap == 0) matches += 1
            }
            // Nothing to count: a round that only showed both answers is not a comparison.
            Verdict.Shown, Verdict.AwaitingJudge -> Unit
        }
    }
    return Score(points.toList(), matches, compared)
}

internal data class RunState(
    val game: Game,
    val players: GamePlayers,
    val firstSeat: Int,
    /** The seat this phone plays, on two phones; null when one phone is passed between them. */
    val phone: Int? = null,
    val rounds: List<List<GamePrompt>>,
    val alternates: List<GamePrompt>,
    val unplayable: Set<Int>,
    val round: Int,
    val position: Int,
    val answers: Map<AnswerKey, Answer>,
    val results: List<RoundResult>,
    val avoid: Set<String>,
) {
    fun activeSeat(index: Int): Int = (firstSeat + index) % 2
}

internal data class AnswerKey(val step: Int, val seat: Int?)

/** One place in a round: a hand-off, a gathering, a step taken by a seat (or both), the end. */
internal sealed interface Slot {
    data class HandOff(val seat: Int) : Slot

    /** The other partner's step, on their own phone: this one waits. */
    data class Wait(val seat: Int) : Slot

    data object Gather : Slot

    data class Take(val step: Int, val seat: Int?) : Slot

    data object End : Slot
}
