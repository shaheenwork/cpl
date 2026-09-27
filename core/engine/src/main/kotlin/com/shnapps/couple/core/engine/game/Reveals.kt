package com.shnapps.couple.core.engine.game

import com.shnapps.couple.core.model.GameStep
import com.shnapps.couple.core.model.RevealOutcome

// What each kind of reveal compares, and what it comes to (BUILD_PROMPT.md §11.1). One
// function per outcome; the renderer only ever shows what these say.

/** The reveal at [take]: the earlier answers it names, side by side, and its verdict. */
internal fun RoundReading.reveal(take: Slot.Take, step: GameStep): RoundPrimitive.Reveal {
    val outcome = step.outcome ?: RevealOutcome.MATCH
    val first = step.of.getOrElse(0) { 0 }
    val second = step.of.getOrElse(1) { first }
    val (shown, verdict) = when (outcome) {
        RevealOutcome.MATCH -> matched(first)
        RevealOutcome.AGREEMENT -> agreed(first)
        RevealOutcome.PREDICTED -> predicted(first, second)
        RevealOutcome.JUDGED -> judged(first, second, answerOf(take.step, take.seat) as? Answer.Judged)
        RevealOutcome.LIE_FOUND -> lieFound(first, second)
        RevealOutcome.WINNER -> won(first)
        RevealOutcome.DISTANCE -> apart(first)
        RevealOutcome.SHARED -> shown(first)
    }
    return RoundPrimitive.Reveal(take.step, take.seat, outcome, shown, verdict)
}

/** Both answers, side by side, and nothing else: no winner, no match, no point. */
private fun RoundReading.shown(step: Int): Pair<List<Shown>, Verdict> {
    val mine = answerOf(step, active)
    val theirs = answerOf(step, other)
    val shown = listOf(Shown(active, labelOf(step, active, mine)), Shown(other, labelOf(step, other, theirs)))
    return shown to Verdict.Shown
}

/**
 * Both left a dial somewhere: how far apart, and nothing else.
 *
 * Neither number is attributed and neither is called higher. A couple learns whether they want the
 * same thing to the same degree, which is the useful half, without learning who wanted it more —
 * which is the half that makes people lie to the dial.
 */
private fun RoundReading.apart(step: Int): Pair<List<Shown>, Verdict> {
    val mine = (answerOf(step, active) as? Answer.Dialed)?.value
    val theirs = (answerOf(step, other) as? Answer.Dialed)?.value
    val gap = if (mine == null || theirs == null) NO_GAP else kotlin.math.abs(mine - theirs)
    val both = if (mine == null || theirs == null) "" else "${kotlin.math.min(mine, theirs)}+"
    return listOf(Shown(null, both)) to Verdict.Apart(gap)
}

/** No dial, no gap to report. */
private const val NO_GAP = -1

/** Both chose, each in turn: the same, or not. */
private fun RoundReading.matched(step: Int): Pair<List<Shown>, Verdict> {
    val mine = answerOf(step, active)
    val theirs = answerOf(step, other)
    val shown = listOf(Shown(active, labelOf(step, active, mine)), Shown(other, labelOf(step, other, theirs)))
    return shown to Verdict.Matched(mine != null && mine == theirs)
}

/** Both named a partner, each from their own side: the same one, or not. */
private fun RoundReading.agreed(step: Int): Pair<List<Shown>, Verdict> {
    val mine = named(answerOf(step, active), active)
    val theirs = named(answerOf(step, other), other)
    val shown = listOf(
        Shown(active, mine?.let(::name) ?: GameRun.TIE),
        Shown(other, theirs?.let(::name) ?: GameRun.TIE),
    )
    return shown to Verdict.Matched(mine != null && mine == theirs)
}

/** One chose, the other predicted it: a point to the predictor when right. */
private fun RoundReading.predicted(choice: Int, prediction: Int): Pair<List<Shown>, Verdict> {
    val chooser = takerOf(choice)
    val predictor = takerOf(prediction)
    val chosen = answerOf(choice, chooser)
    val guess = answerOf(prediction, predictor)
    val shown = listOf(
        Shown(chooser, labelOf(choice, chooser, chosen)),
        Shown(predictor, labelOf(prediction, predictor, guess)),
    )
    return shown to Verdict.Point(predictor.takeIf { chosen != null && chosen == guess })
}

/** One answered, the other guessed: the one it was about says whether it came close. */
private fun RoundReading.judged(answer: Int, guess: Int, judgement: Answer.Judged?): Pair<List<Shown>, Verdict> {
    val answerer = takerOf(answer)
    val guesser = takerOf(guess)
    val shown = listOf(
        Shown(answerer, labelOf(answer, answerer, answerOf(answer, answerer))),
        Shown(guesser, labelOf(guess, guesser, answerOf(guess, guesser))),
    )
    return shown to if (judgement == null) Verdict.AwaitingJudge else Verdict.Point(guesser.takeIf { judgement.close })
}

/** Three statements, one a lie: a point to whoever found it, or to the liar who hid it. */
private fun RoundReading.lieFound(statementsStep: Int, guessStep: Int): Pair<List<Shown>, Verdict> {
    val liar = takerOf(statementsStep)
    val guesser = takerOf(guessStep)
    val statements = statementsOf(statementsStep)
    val guess = (answerOf(guessStep, guesser) as? Answer.Picked)?.optionId?.toIntOrNull()
    val found = statements != null && guess == statements.lie
    val shown = statements?.statements.orEmpty().mapIndexed { index, statement ->
        Shown(if (index == guess) guesser else null, statement, highlighted = index == statements?.lie)
    }
    return shown to Verdict.Point(if (found) guesser else liar)
}

/** The couple named a winner, or called it a tie. */
private fun RoundReading.won(vote: Int): Pair<List<Shown>, Verdict> {
    val voter = takerOf(vote)
    val winner = named(answerOf(vote, voter), voter)
    return listOf(Shown(winner, winner?.let(::name) ?: GameRun.TIE)) to Verdict.Point(winner)
}
