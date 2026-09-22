package com.shnapps.couple.core.engine.game

import com.shnapps.couple.core.model.AnswerKind
import com.shnapps.couple.core.model.ChapterReaction
import com.shnapps.couple.core.model.PromptKind
import com.shnapps.couple.core.model.RevealOutcome

/**
 * One interaction unit of a round (BUILD_PROMPT.md §11.1), resolved for the round being
 * played: what to show, who takes it, and — through [accepts] — the one answer contract it
 * has. Every game is these, in the order its definition lists; :feature:games has one
 * renderer for each, and none for any game.
 *
 * [seat] is who takes it: 0 is whoever started the game, 1 their partner, null both at once.
 */
sealed interface RoundPrimitive {
    /** The step of the game's definition this comes from. */
    val step: Int
    val seat: Int?

    /** Whether [answer] completes this primitive. */
    fun accepts(answer: Answer): Boolean

    /** Asked and answered: out loud, typed, or as three statements with one lie. */
    data class Question(
        override val step: Int,
        override val seat: Int?,
        val text: String,
        val answer: AnswerKind,
    ) : RoundPrimitive {
        override fun accepts(answer: Answer): Boolean = when (this.answer) {
            AnswerKind.SPOKEN -> answer == Answer.Done
            AnswerKind.TEXT -> answer is Answer.Text && answer.text.isNotBlank()
            AnswerKind.STATEMENTS -> answer is Answer.Statements &&
                answer.statements.size == STATEMENTS &&
                answer.statements.all { it.isNotBlank() } &&
                answer.lie in answer.statements.indices
        }
    }

    /** One of a few options. */
    data class Choice(
        override val step: Int,
        override val seat: Int?,
        val text: String,
        val options: List<Option>,
    ) : RoundPrimitive {
        override fun accepts(answer: Answer): Boolean = answer.picks(options)
    }

    /** A guess at another answer: from its options, or typed when it was typed. */
    data class Prediction(
        override val step: Int,
        override val seat: Int?,
        val text: String,
        /** Null when the guess is typed. */
        val options: List<Option>?,
    ) : RoundPrimitive {
        override fun accepts(answer: Answer): Boolean =
            if (options == null) answer is Answer.Text && answer.text.isNotBlank() else answer.picks(options)
    }

    /** Naming one of the two partners, or a tie. */
    data class Vote(
        override val step: Int,
        override val seat: Int?,
        val text: String,
        val options: List<Option>,
    ) : RoundPrimitive {
        override fun accepts(answer: Answer): Boolean = answer.picks(options)
    }

    /** Earlier answers, shown side by side, and what they add up to. */
    data class Reveal(
        override val step: Int,
        override val seat: Int?,
        val outcome: RevealOutcome,
        val shown: List<Shown>,
        val verdict: Verdict,
    ) : RoundPrimitive {
        override fun accepts(answer: Answer): Boolean =
            if (verdict == Verdict.AwaitingJudge) answer is Answer.Judged else answer == Answer.Done
    }

    /** A countdown for whatever is being done. */
    data class Timer(
        override val step: Int,
        override val seat: Int?,
        /** What the time is for: the round's prompt. */
        val caption: String,
        val seconds: Int,
    ) : RoundPrimitive {
        override fun accepts(answer: Answer): Boolean = answer == Answer.Done
    }

    /** Something to do: a dare, a battle, a truth to tell. */
    data class Challenge(
        override val step: Int,
        override val seat: Int?,
        val text: String,
        val kind: PromptKind?,
    ) : RoundPrimitive {
        override fun accepts(answer: Answer): Boolean = answer == Answer.Done
    }

    /** How it landed. Stays on the phone. */
    data class Reaction(
        override val step: Int,
        override val seat: Int?,
        val text: String,
    ) : RoundPrimitive {
        override fun accepts(answer: Answer): Boolean = answer is Answer.Reacted
    }

    companion object {
        const val STATEMENTS = 3
    }
}

/** An option to pick: its id in the round, and the words shown for it. */
data class Option(val id: String, val label: String)

/** An answer as the phone took it. Never leaves the phone (DECISIONS.md D-069). */
sealed interface Answer {
    data class Picked(val optionId: String) : Answer

    data class Text(val text: String) : Answer

    data class Statements(val statements: List<String>, val lie: Int) : Answer

    /** Said out loud, done, or seen. */
    data object Done : Answer

    /** Whether a guess came close enough, as the one it was about judges. */
    data class Judged(val close: Boolean) : Answer

    data class Reacted(val reaction: ChapterReaction) : Answer
}

private fun Answer.picks(options: List<Option>): Boolean = this is Answer.Picked && options.any { it.id == optionId }

/** One earlier answer as a reveal shows it: whose it was, and what it said. */
data class Shown(
    val seat: Int?,
    val label: String,
    /** Marks the answer the reveal turns on: the lie among the statements. */
    val highlighted: Boolean = false,
)

/** What a reveal adds up to. */
sealed interface Verdict {
    /** Both chose the same, or pointed at the same partner — or did not. */
    data class Matched(val matched: Boolean) : Verdict

    /** A point to [seat]; null when nobody scores (a tie). */
    data class Point(val seat: Int?) : Verdict

    /** The one the guess was about decides whether it came close. */
    data object AwaitingJudge : Verdict
}
