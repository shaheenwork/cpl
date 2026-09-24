package com.shnapps.couple.core.model

/** How something left for later is opened (BUILD_PROMPT.md §13). */
enum class SurpriseType {
    /** At a time its author chose. */
    SCHEDULED,

    /** When the partner feels a particular way: "open when you can't sleep". */
    OPEN_WHEN,

    /** Far off: tomorrow, next week, an anniversary. */
    CAPSULE,
}

/** Where something left for later stands (§6.1). */
enum class SurpriseState { SCHEDULED, DELIVERED, OPENED, EXPIRED }

/** The moments an "Open when…" can be kept for (§13). */
enum class OpenWhenTrigger(val label: String) {
    MISS_ME("You miss me"),
    NEED_A_LAUGH("You need a laugh"),
    CANT_SLEEP("You can't sleep"),
    WANT_A_SURPRISE("You want a surprise"),
    THINKING_OF_ME("You're thinking about me"),
    NEED_ATTENTION("You need attention"),
    REMEMBER_US("You want to remember us"),
}

/**
 * Something one partner left for the other to find later (§5.7, §13), as a phone sees it: that it
 * exists, what it is called, and where it stands. Never what is in it — that is a separate
 * document, and the rules keep it from the partner until the server delivers it.
 */
data class Surprise(
    val id: String,
    val type: SurpriseType,
    val title: String,
    /** True when this phone's user left it. */
    val mine: Boolean,
    val state: SurpriseState,
    val scheduledForMillis: Long?,
    val trigger: OpenWhenTrigger?,
    val hasMedia: Boolean,
) {
    /** Delivered, and waiting to be opened by the partner it was left for. */
    val waitingForMe: Boolean get() = !mine && state == SurpriseState.DELIVERED
}

/** What was inside, once it may be read. */
data class SurpriseContent(val text: String?, val mediaId: String?)

/** A countdown to something they are both waiting for (§13). */
data class Countdown(val id: String, val title: String, val targetAtMillis: Long)
