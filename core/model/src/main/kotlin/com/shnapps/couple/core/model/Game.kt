package com.shnapps.couple.core.model

/**
 * A game (BUILD_PROMPT.md §11): a declarative composition of primitives, authored in
 * content/games.json and shipped in the content bundle. A new game is new data, not new code —
 * the interpreter in :core:engine plays any game these types can describe, and :feature:games
 * renders primitives, never games.
 *
 * Public copy, like content items. Which prompts a couple meets is decided by the server
 * behind both partners' boundaries (§3.2).
 */
data class Game(
    val id: String,
    val title: String,
    val tagline: String,
    val description: String,
    val modes: Set<Mode>,
    val rounds: Int,
    val turns: TurnOrder,
    val scoring: Scoring,
    /** Each round offers one prompt of every kind, in this order (a truth and a dare). */
    val promptKinds: List<PromptKind>,
    val steps: List<GameStep>,
)

/** One step of every round: a primitive, who takes it, and what it shows or compares. */
data class GameStep(
    val primitive: GamePrimitive,
    val who: StepAudience,
    /** Only whoever takes it may see it: the phone is handed over first, and covered after. */
    val isPrivate: Boolean,
    val text: StepText?,
    val options: StepOptions?,
    val answer: AnswerKind?,
    /** Earlier steps this one refers to: what is predicted, compared or revealed. */
    val of: List<Int>,
    val outcome: RevealOutcome?,
    val seconds: StepSeconds?,
    /** The earlier CHOICE whose answer picks which of the round's prompts is played. */
    val kindFrom: Int?,
)

/** What a step shows: the round's prompt, or words of its own. */
sealed interface StepText {
    data object Prompt : StepText

    data class Literal(val text: String) : StepText
}

/** Where a step's options come from. */
sealed interface StepOptions {
    /** The round's prompt carries them (would you rather: two options). */
    data object FromPrompt : StepOptions

    /** The statements an earlier step wrote (two truths and a lie). */
    data object FromStatements : StepOptions

    /** A fixed list: [VoteOption] and [PromptKind] names. */
    data class Fixed(val ids: List<String>) : StepOptions
}

/** How long a timer runs: the round's prompt says, or the step does. */
sealed interface StepSeconds {
    data object FromPrompt : StepSeconds

    data class Fixed(val seconds: Int) : StepSeconds
}

/** The interaction units every game is built from (§11.1). */
enum class GamePrimitive {
    QUESTION,
    CHOICE,
    PREDICTION,
    VOTE,
    REVEAL,
    TIMER,
    CHALLENGE,
    REACTION,
}

/** Who takes a step: whose turn it is, the other partner, both in turn, or both at once. */
enum class StepAudience {
    ACTIVE,
    OTHER,
    EACH,
    TOGETHER,
}

/** How a question is answered: out loud, typed, or as three statements with one lie. */
enum class AnswerKind {
    SPOKEN,
    TEXT,
    STATEMENTS,
}

/** What a reveal compares. */
enum class RevealOutcome {
    /** Both chose the same. */
    MATCH,

    /** Both pointed at the same partner. */
    AGREEMENT,

    /** The other predicted the choice. */
    PREDICTED,

    /** The one whose turn it is judges whether the other came close. */
    JUDGED,

    /** The other found the lie. */
    LIE_FOUND,

    /** The couple named a winner, or called it a tie. */
    WINNER,
}

/** Whether every round belongs to both partners, or turns alternate between them. */
enum class TurnOrder {
    SHARED,
    ALTERNATE,
}

enum class Scoring {
    NONE,
    MATCHES,
    POINTS,
}

/** The partner a vote names, from the voter's side, or neither. */
enum class VoteOption {
    ME,
    YOU,
    TIE,
}

enum class PromptKind(val label: String) {
    TRUTH("Truth"),
    DARE("Dare"),
}

/**
 * One prompt of a game (§11.2): public copy, carrying every field the server's chokepoint
 * reads, so a prompt reaches a couple by exactly the rule an item does (§3.2).
 */
data class GamePrompt(
    val id: String,
    val version: Int,
    val game: String,
    val kind: PromptKind?,
    val text: String,
    val options: List<String>,
    val seconds: Int?,
    val category: String,
    val tags: Set<String>,
    val intensity: Intensity,
    val modes: Set<Mode>,
    val moods: Set<Mood>,
    val requiredMutualPreferences: Set<String>,
    val boostedByPreferences: Set<String>,
    /** Never empty: a prompt that arrives without its boundaries is dropped. */
    val excludedByBoundaries: Set<String>,
    val status: ContentStatus,
)

/** One round of a started game: its prompt, or one of each kind in the game's order. */
data class GameRound(
    val index: Int,
    val prompts: List<GamePrompt>,
) {
    val intensity: Intensity get() = prompts.maxBy { it.intensity.level }.intensity
}

/**
 * A game the server started for this couple, resolved against the content on this phone
 * (§11). Only ever made from what the server released — its rounds and a few alternates for
 * "Not this one" — never from prompts the phone chose.
 */
data class PlannedGame(
    val experienceId: String,
    val game: Game,
    val rounds: List<GameRound>,
    val alternates: List<GamePrompt>,
    /** Never above what was asked, nor either partner's level (§3.1). */
    val effectiveIntensity: Intensity,
    /** Rounds the couple's limits ruled out mid-game, with nothing released to replace them. */
    val unplayable: Set<Int> = emptySet(),
) {
    /** BOLD and WILD need both partners' "I'm in" before the first round (§3.1). */
    val needsBothConsents: Boolean get() = rounds.any { it.intensity.requiresBothPartyConsent }
}

/**
 * The two people passing the phone, by the names they chose on it. Kept on the phone only;
 * the first is whoever started the game.
 */
data class GamePlayers(val first: String, val second: String) {
    fun name(index: Int): String = if (index == 0) first else second
}
