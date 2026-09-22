package com.shnapps.couple.core.model

/**
 * What a couple asks for tonight (BUILD_PROMPT.md §14.5, "WHAT ARE WE IN THE MOOD FOR?").
 *
 * The engine turns these into a night on the server, behind both partners' boundaries (§10).
 * Nothing here is private: it is what the couple chose together.
 */
data class NightSettings(
    val mode: Mode = Mode.TOGETHER,
    val durationMin: Int = DEFAULT_DURATION_MIN,
    val moods: Set<Mood> = emptySet(),
    val intensity: Intensity = Intensity.FLIRTY,
    val leadership: Leadership = Leadership.RANDOM,
    val novelty: Novelty = Novelty.MIXED,
    /** A revealed mutual match to build the night around (§14.7), as a taxonomy item id. */
    val focus: String? = null,
) {
    companion object {
        const val DEFAULT_DURATION_MIN = 30
    }
}

/** One chapter of a night, with the content that plays it. */
data class NightChapter(
    val index: Int,
    val kind: ChapterKind,
    val item: ContentItem,
) {
    val intensity: Intensity get() = item.intensity
}

/**
 * A night the server built for this couple, resolved against the content on this phone and
 * ready to play (§14.6). Only ever made from what the server released — its chapters and a
 * bounded pool of alternates (§5.5) — never from a list the phone chose.
 */
data class PlannedNight(
    val experienceId: String,
    val settings: NightSettings,
    val chapters: List<NightChapter>,
    /** The night's ceiling: never above what was asked, nor either partner's level (§3.1). */
    val effectiveIntensity: Intensity,
    /**
     * Released items this phone's user asked to be asked about first (§3.2). Only ever the
     * user's own: a partner's ask-first themes are left out of a night played on this phone
     * (DECISIONS.md D-060).
     */
    val askFirstItems: Set<String>,
    /** Whether a night asked to be built around a match holds something about it. */
    val focusIncluded: Boolean,
    val seed: Long,
    /**
     * Chapters the couple's limits ruled out during the night, with nothing released to take
     * their place (DECISIONS.md D-062). The player passes over them without showing them.
     */
    val unplayable: Set<Int> = emptySet(),
) {
    val totalMinutes: Int get() = chapters.sumOf { it.item.durationMin }

    /** BOLD and WILD need both partners' "I'm in" before the first chapter (§3.1). */
    val needsBothConsents: Boolean get() = chapters.any { it.intensity.requiresBothPartyConsent }
}

/** Where a night or a game being played stands (§12.1). The server's state is always the truth. */
enum class SessionState {
    WAITING_FOR_PARTNER,
    READY,
    CHAPTER_ACTIVE,

    /** One partner has answered on their own phone; the chapter waits for the other (§12.1). */
    AWAITING_SUBMISSIONS,
    BOTH_SUBMITTED,

    /** Both answers are in, and the reveal both may see is written. */
    REVEAL,

    /** Both have seen the reveal: the chapter is over, and either phone moves it on. */
    CHAPTER_COMPLETE,
    PAUSED,
    COMPLETED,
    ABANDONED,
    ;

    val isOver: Boolean get() = this == COMPLETED || this == ABANDONED

    /** The states a chapter is under way in. */
    val inChapter: Boolean
        get() = this == CHAPTER_ACTIVE || this == AWAITING_SUBMISSIONS || this == BOTH_SUBMITTED ||
            this == REVEAL || this == CHAPTER_COMPLETE
}

/** Whether a session is played on one phone, passed between the partners, or on both (§12). */
enum class SessionKind {
    ONE_DEVICE,
    TWO_DEVICE,
}

/** Why a night ended. Never who ended it: STOP is nobody's failure (§3.1, D-059). */
enum class SessionEndReason {
    COMPLETED,
    STOPPED,
    DECLINED,
    EXPIRED,
}

/** A night being played, as either phone sees it (§12). */
data class NightSession(
    val id: String,
    val experienceId: String,
    val state: SessionState,
    /** The state a paused night returns to. */
    val pausedFrom: SessionState?,
    /** -1 before the first chapter. */
    val currentChapter: Int,
    val chapterCount: Int,
    val consentRequired: Boolean,
    /** Whether this phone's user has said "I'm in". */
    val consented: Boolean,
    /** Whether the other partner has. Consent is not private: it is how a night starts. */
    val partnerConsented: Boolean,
    /** Whether this is the phone the night plays on (D-059). */
    val isHost: Boolean,
    val endReason: SessionEndReason?,
    /** What is being played: a night, or a game through the same session (§11). */
    val experienceType: ExperienceType = ExperienceType.NIGHT,
    /** One phone passed between the partners, or a phone each (§12). */
    val kind: SessionKind = SessionKind.ONE_DEVICE,
    /** Whether this phone's user has answered the chapter they are on. */
    val submitted: Boolean = false,
    val partnerSubmitted: Boolean = false,
    /** Whether they have seen its reveal. */
    val seen: Boolean = false,
    val partnerSeen: Boolean = false,
    /** When the partner's phone was last in the session, or null if it never has been. */
    val partnerLastSeenMillis: Long? = null,
) {
    /** On two phones, either partner moves a finished chapter on; on one, only its host (D-074). */
    val canAdvance: Boolean get() = kind == SessionKind.TWO_DEVICE || isHost
}

/** What a session plays. Either phone opens the player that fits. */
enum class ExperienceType {
    NIGHT,
    GAME,
}

/**
 * "How are we doing?" — the check-in at the close of a night or a game (§3.1). Aftercare, not a
 * form: nothing it says is stored (DECISIONS.md D-065); it only decides what to say next.
 */
enum class CloseCheckIn(val label: String, val emoji: String, val followUp: String) {
    GLOWING("Glowing", "✨", "Hold onto that."),
    GOOD("Good", "❤️", "Good. That's what tonight was for."),
    A_BIT_MUCH(
        "A bit much",
        "🌙",
        "Thank you for saying so. Check in with each other — and you can turn things down any time, in your limits.",
    ),
    LETS_TALK(
        "Let's talk",
        "💬",
        "Take your time. Some easy ways in: what felt best? Anything you'd change? What would you like more of?",
    ),
}

/** How a chapter landed, for the partner who says so. Private to them (D-063). */
enum class ChapterReaction(val label: String, val emoji: String) {
    LOVED("Loved it", "🔥"),
    FINE("It was fine", "🙂"),
    NOT_FOR_US("Not for us", "🙅"),
}
