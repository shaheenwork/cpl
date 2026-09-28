package com.shnapps.couple.core.engine.game

import com.shnapps.couple.core.engine.Rng
import com.shnapps.couple.core.model.AnswerKind
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.GamePrimitive
import com.shnapps.couple.core.model.GamePrompt
import com.shnapps.couple.core.model.GameStep
import com.shnapps.couple.core.model.MediaKind
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

    /** What the draw seeds itself from: which game this is, and which round of it. */
    val gameId: String get() = game.id
    val roundIndex: Int get() = state.round
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

    /**
     * The primitive a step comes to, in two halves: the ones that ask for an answer, and the ones
     * that only show something. Split because the dispatch is the one place every primitive has to
     * be named, and it grows by one branch each time the vocabulary does.
     */
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
            GamePrimitive.DIAL -> RoundPrimitive.Dial(take.step, seat, text, DIAL_LOW, DIAL_HIGH)
            GamePrimitive.MEDIA -> RoundPrimitive.Record(take.step, seat, text, step.media ?: MediaKind.VOICE)
            else -> shown(take, step, seat, text)
        }
    }

    private fun textOf(step: GameStep): String = when (val words = step.text) {
        StepText.Prompt -> promptInPlay()?.text.orEmpty()
        is StepText.Literal -> words.text
        null -> ""
    }

    fun secondsOf(step: GameStep): Int = when (val seconds = step.seconds) {
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

    /**
     * "Me" and "you" said by [seat] — or, said by both at once, the first and second seat.
     *
     * A name is only substituted when the couple actually gave one. Without names the option
     * stays relative, because only the person whose turn it is can see it, and a relative word
     * printed as a fixed name reads correctly for exactly one of the two of them.
     */
    private fun fixedLabel(id: String, seat: Int?): String = when (id) {
        VoteOption.ME.name -> if (state.players.named) state.players.name(seat ?: 0) else "Me"
        VoteOption.YOU.name -> if (state.players.named) {
            state.players.name(if (seat == null) 1 else 1 - seat)
        } else {
            "You"
        }
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

/** What a dial's ends say. The words are the same in every game, so no game has to supply them. */
private const val DIAL_LOW = "Not at all"
private const val DIAL_HIGH = "Very much"

/**
 * A seed from what the round already is, so a draw needs no stored randomness.
 *
 * Kotlin's `String.hashCode` is specified rather than platform-dependent, so this gives the same
 * number on every device, every run, for the same round (engine/SPEC.md §2).
 */
private fun seedOf(game: String, round: Int, step: Int, prompt: String): Long {
    val text = "$game|$round|$step|$prompt"
    return text.hashCode().toLong() and THIRTY_TWO_BITS
}

/** mulberry32 is a 32-bit generator, so the seed is taken unsigned in that width (SPEC §2). */
private const val THIRTY_TWO_BITS = 0xFFFFFFFFL

/**
 * Where chance lands, decided here rather than on screen.
 *
 * The seed is the round's own: the game, the round number, the step and the prompt being played. So
 * the same round always lands the same way — a test can assert it, both phones of a two-phone game
 * agree without sending anything, and a reopened phone lands where it did before (D-047, D-075).
 * The screen spins towards a decision that was already made.
 */
internal fun RoundReading.draw(take: Slot.Take, step: GameStep, seat: Int?, text: String): RoundPrimitive.Draw {
    val pool = options(step, seat)
    val seed = seedOf(gameId, roundIndex, take.step, promptInPlay()?.id.orEmpty())
    val landed = if (pool.isEmpty()) Option("", "") else pool[Rng(seed).nextInt(pool.size)]
    return RoundPrimitive.Draw(take.step, seat, text, pool, landed)
}

/**
 * The primitives that show something and move on: a clock, a dare, a reaction, a draw.
 *
 * A file-level extension, like the reveal and the draw, so the reading stays a reading rather than
 * growing a method for every primitive the vocabulary gains.
 */
internal fun RoundReading.shown(take: Slot.Take, step: GameStep, seat: Int?, text: String): RoundPrimitive =
    when (step.primitive) {
        GamePrimitive.TIMER -> RoundPrimitive.Timer(take.step, seat, promptInPlay()?.text.orEmpty(), secondsOf(step))
        GamePrimitive.CHALLENGE -> RoundPrimitive.Challenge(take.step, seat, text, promptInPlay()?.kind)
        GamePrimitive.REACTION -> RoundPrimitive.Reaction(take.step, seat, text)
        GamePrimitive.DRAW -> draw(take, step, seat, text)
        else -> error("${step.primitive} is answered, not shown")
    }
