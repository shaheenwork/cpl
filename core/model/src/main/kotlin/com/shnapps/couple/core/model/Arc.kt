package com.shnapps.couple.core.model

/** Where a day of an arc stands (BUILD_PROMPT.md §13). */
enum class ArcDayState { LOCKED, READY, DONE }

/** One day of an arc: a beat, when it opens, and the night it was given. */
data class ArcDay(
    val index: Int,
    val title: String,
    val state: ArcDayState,
    val unlockAtMillis: Long,
    /** Null until the day is opened: a night is built when it is reached, not before. */
    val experienceId: String?,
)

/**
 * A few days of this (§13): one, three, seven or fourteen, a night each. Days unlock on the
 * clock, and only once the day before has been played.
 */
data class Arc(
    val id: String,
    val title: String,
    val lengthDays: Int,
    val active: Boolean,
    val days: List<ArcDay>,
) {
    /**
     * The day to open now, if there is one: due, and not yet played.
     *
     * The first day is due the moment the arc starts — it is what starting one means — so it does
     * not wait for the phone's clock to agree with the server's about when that moment was.
     */
    fun dayDue(nowMillis: Long): ArcDay? = days.firstOrNull {
        it.state != ArcDayState.DONE && (it.index == 0 || it.unlockAtMillis <= nowMillis)
    }

    companion object {
        /** How long an arc may run. The server refuses any other length. */
        val LENGTHS = listOf(1, 3, 7, 14)
    }
}
