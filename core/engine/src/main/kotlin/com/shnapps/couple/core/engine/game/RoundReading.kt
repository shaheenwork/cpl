package com.shnapps.couple.core.engine.game

import com.shnapps.couple.core.model.AnswerKind
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.GamePrimitive
import com.shnapps.couple.core.model.GamePrompt
import com.shnapps.couple.core.model.GameStep
import com.shnapps.couple.core.model.PromptKind
import com.shnapps.couple.core.model.StepAudience
import com.shnapps.couple.core.model.StepOptions
import com.shnapps.couple.core.model.StepSeconds
import com.shnapps.couple.core.model.StepText
import com.shnapps.couple.core.model.VoteOption

/**
 * The round under way, read from a run's state: its places in turn, the primitive at each,
 * and what the answers given so far say. [GameRun] makes the moves; this only reads.
 */
internal class RoundReading(private val state: RunState) {

    private val game: Game get() = state.game
    val active: Int = state.activeSeat(state.round)
    val other: Int = 1 - active

    fun moment(): GameMoment = when (val slot = slots().getOrElse(state.position) { Slot.End }) {
        is Slot.HandOff -> GameMoment.HandOff(state.round, slot.seat)
        is Slot.Wait -> GameMoment.Waiting(state.round, slot.seat)
        Slot.Gather -> GameMoment.Gather(state.round)
        is Slot.Take -> {
            val prompted = showsPrompt(game.steps[slot.step])
            GameMoment.Turn(state.round, primitive(slot), canSkip = prompted)
        }
        Slot.End -> GameMoment.RoundOver(state.round, promptInPlay()?.id)
    }

    /** Every step of the round, and the seat it belongs to: the round before any phone reads it. */
    private val takes: List<Slot.Take>
        get() = game.steps.flatMapIndexed { index, step ->
            when (step.who) {
                StepAudience.ACTIVE -> listOf(Slot.Take(index, active))
                StepAudience.OTHER -> listOf(Slot.Take(index, other))
                StepAudience.EACH -> listOf(Slot.Take(index, active), Slot.Take(index, other))
                StepAudience.TOGETHER -> listOf(Slot.Take(index, null))
            }
        }

    /**
     * The round as the phone moves through it: each step taken by whoever it belongs to, a
     * hand-off before anything private to one partner, and a gathering when private answers
     * give way to something both see.
     */
    fun slots(): List<Slot> {
        val takes = takes
        val phone = state.phone
        if (phone != null) return ownSlots(takes, phone)
        val slots = mutableListOf<Slot>()
        var holder: Int? = null
        for (take in takes) {
            val seat = take.seat
            if (game.steps[take.step].isPrivate && seat != null) {
                if (holder != seat) slots += Slot.HandOff(seat)
                holder = seat
            } else {
                if (holder != null) slots += Slot.Gather
                holder = null
            }
            slots += take
        }
        slots += Slot.End
        return slots
    }

    /**
     * A phone each (§12): this phone takes its own steps, and the other partner's are only ever
     * "they are answering". What they said arrives as the server's reveal, which comes only once
     * both have answered — so this phone always answers first, and waits after.
     */
    private fun ownSlots(takes: List<Slot.Take>, phone: Int): List<Slot> {
        val partner = 1 - phone
        val slots = mutableListOf<Slot>()
        var owed = false
        for (take in takes) {
            when (take.seat) {
                phone -> slots += take
                null -> {
                    // A step they both see needs both their answers first.
                    if (owed) slots += Slot.Wait(partner)
                    owed = false
                    slots += take
                }
                else -> owed = true
            }
        }
        if (owed) slots += Slot.Wait(partner)
        return slots + Slot.End
    }

    /** The prompt the round is played with: the chosen kind's, once chosen, else the first. */
    fun promptInPlay(): GamePrompt? {
        val prompts = state.rounds.getOrNull(state.round)?.takeIf { it.isNotEmpty() } ?: return null
        val chooser = game.steps.firstNotNullOfOrNull { it.kindFrom }
        val picked = state.answers.entries.firstOrNull { it.key.step == chooser }?.value as? Answer.Picked
        val kind = PromptKind.entries.firstOrNull { it.name == picked?.optionId }
        return prompts.firstOrNull { kind != null && it.kind == kind } ?: prompts.first()
    }

    fun showsPrompt(step: GameStep): Boolean = step.text == StepText.Prompt || step.options == StepOptions.FromPrompt

    fun primitive(take: Slot.Take): RoundPrimitive {
        val step = game.steps[take.step]
        val text = textOf(step)
        val seat = take.seat
        return when (step.primitive) {
            GamePrimitive.QUESTION -> RoundPrimitive.Question(take.step, seat, text, step.answer ?: AnswerKind.SPOKEN)
            GamePrimitive.CHOICE -> RoundPrimitive.Choice(take.step, seat, text, options(step, seat))
            GamePrimitive.PREDICTION -> {
                val options = if (step.answer == AnswerKind.TEXT) null else options(step, seat)
                RoundPrimitive.Prediction(take.step, seat, text, options)
            }
            GamePrimitive.VOTE -> RoundPrimitive.Vote(take.step, seat, text, options(step, seat))
            GamePrimitive.REVEAL -> reveal(take, step)
            GamePrimitive.TIMER -> {
                val caption = promptInPlay()?.text.orEmpty()
                RoundPrimitive.Timer(take.step, seat, caption, secondsOf(step))
            }
            GamePrimitive.CHALLENGE -> RoundPrimitive.Challenge(take.step, seat, text, promptInPlay()?.kind)
            GamePrimitive.REACTION -> RoundPrimitive.Reaction(take.step, seat, text)
        }
    }

    private fun textOf(step: GameStep): String = when (val words = step.text) {
        StepText.Prompt -> promptInPlay()?.text.orEmpty()
        is StepText.Literal -> words.text
        null -> ""
    }

    private fun secondsOf(step: GameStep): Int = when (val seconds = step.seconds) {
        StepSeconds.FromPrompt -> promptInPlay()?.seconds ?: GameRun.DEFAULT_SECONDS
        is StepSeconds.Fixed -> seconds.seconds
        null -> GameRun.DEFAULT_SECONDS
    }

    fun options(step: GameStep, seat: Int?): List<Option> = when (val source = step.options) {
        StepOptions.FromPrompt -> promptInPlay()?.options.orEmpty()
            .mapIndexed { index, label -> Option("$index", label) }
        StepOptions.FromStatements -> statementsOf(step.of.firstOrNull())
            ?.statements.orEmpty()
            .mapIndexed { index, statement -> Option("$index", statement) }
        is StepOptions.Fixed -> source.ids.map { id -> Option(id, fixedLabel(id, seat)) }
        null -> emptyList()
    }

    /** "Me" and "you" said by [seat] — or, said by both at once, the first and second seat. */
    private fun fixedLabel(id: String, seat: Int?): String = when (id) {
        VoteOption.ME.name -> state.players.name(seat ?: 0)
        VoteOption.YOU.name -> state.players.name(if (seat == null) 1 else 1 - seat)
        VoteOption.TIE.name -> GameRun.TIE
        else -> PromptKind.entries.firstOrNull { it.name == id }?.label ?: id
    }

    /** The seat a vote by [voter] names, or null for a tie. */
    fun named(answer: Answer?, voter: Int?): Int? = when ((answer as? Answer.Picked)?.optionId) {
        VoteOption.ME.name -> voter ?: 0
        VoteOption.YOU.name -> if (voter == null) 1 else 1 - voter
        else -> null
    }

    fun statementsOf(step: Int?): Answer.Statements? =
        state.answers.entries.firstOrNull { it.key.step == step }?.value as? Answer.Statements

    /** What [answer] to step [index], taken by [seat], says. */
    fun labelOf(index: Int, seat: Int?, answer: Answer?): String = when (answer) {
        is Answer.Picked -> options(game.steps[index], seat).firstOrNull { it.id == answer.optionId }?.label
            ?: answer.optionId
        is Answer.Text -> answer.text
        else -> ""
    }

    /** Who took step [index] alone: null for a step both took at once, or each in turn. */
    fun takerOf(index: Int): Int? = when (game.steps[index].who) {
        StepAudience.ACTIVE -> active
        StepAudience.OTHER -> other
        StepAudience.EACH, StepAudience.TOGETHER -> null
    }

    fun answerOf(step: Int, seat: Int?): Answer? = state.answers[AnswerKey(step, seat)]

    /**
     * The step [seat] answers in this round: the last one that asks them for something only they
     * can give. It is where the reveal puts what the server says they answered.
     */
    fun answeringStepFor(seat: Int): Slot.Take? =
        takes.lastOrNull { take -> (take.seat == seat || take.seat == null) && asks(game.steps[take.step]) }

    private fun asks(step: GameStep): Boolean = when (step.primitive) {
        GamePrimitive.CHOICE, GamePrimitive.PREDICTION, GamePrimitive.VOTE -> true
        GamePrimitive.QUESTION -> step.answer != AnswerKind.SPOKEN
        else -> false
    }

    /**
     * What this phone must send for the round (§12.2): the last answer it gave that the other
     * phone cannot know — a choice, a guess, words, statements — or null when it only pressed on.
     */
    fun answerToSend(): Answer? {
        val phone = state.phone ?: return null
        val mine = slots()
            .filterIsInstance<Slot.Take>()
            .filter { it.seat == phone || it.seat == null }
            .mapNotNull { take -> answerOf(take.step, take.seat) }
        return mine.lastOrNull { it !is Answer.Done && it !is Answer.Judged }
    }

    fun name(seat: Int): String = state.players.name(seat)
}
