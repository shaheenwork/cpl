package com.shnapps.couple.feature.chat

import com.shnapps.couple.core.model.Message
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One row of the drawn conversation. */
internal sealed interface Entry {

    /** A day boundary: TODAY, YESTERDAY, or the date. */
    data class Day(val label: String) : Entry

    /** Everything below this was said since this phone last looked. */
    data object New : Entry

    /**
     * One message, and where it sits in its run.
     *
     * A run is consecutive messages from the same person close together in time. Only its [last]
     * carries a timestamp, and only its [first] gets the tail corner — otherwise five messages in
     * a row read as five separate interruptions rather than one person talking.
     */
    data class Bubble(val message: Message, val first: Boolean, val last: Boolean) : Entry

    /**
     * Media this phone is uploading. Not a message yet, and drawn at the end of the conversation.
     *
     * It is not part of [Timeline.of], because it is not part of what was said — it is what this
     * phone is in the middle of doing.
     */
    data class Sending(val outgoing: Outgoing) : Entry
}

/**
 * Turning a list of messages into the rows a conversation is actually drawn as.
 *
 * Pure, and deliberately so: day boundaries and grouping are the two things most easily got wrong
 * and least visible in a screenshot, and both are testable only if no composable is involved.
 */
internal object Timeline {

    /** Inside this, two messages from the same person are one run. */
    const val RUN_MS = 5 * 60 * 1000L

    fun of(
        messages: List<Message>,
        newFrom: String?,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): List<Entry> {
        if (messages.isEmpty()) return emptyList()
        val entries = mutableListOf<Entry>()
        var day: LocalDate? = null
        messages.forEachIndexed { index, message ->
            val on = message.dayIn(zone, today)
            if (on != day) {
                entries += Entry.Day(label(on, today))
                day = on
            }
            if (message.id == newFrom) entries += Entry.New
            entries += Entry.Bubble(
                message = message,
                first = !message.runsWith(messages.getOrNull(index - 1)),
                last = !message.runsWith(messages.getOrNull(index + 1)),
            )
        }
        return entries
    }

    /**
     * Which day a message belongs to.
     *
     * One still on its way has no clock on it at all — the server sets that — so it belongs to
     * today, which is where it was typed and where it will land.
     */
    private fun Message.dayIn(zone: ZoneId, today: LocalDate): LocalDate =
        if (pending) today else Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()

    /** Whether these two are the same person talking, close enough together to be one run. */
    private fun Message.runsWith(other: Message?): Boolean {
        if (other == null || other.mine != mine) return false
        if (pending || other.pending) return true
        return kotlin.math.abs(atMillis - other.atMillis) <= RUN_MS
    }

    private fun label(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "TODAY"
        today.minusDays(1) -> "YESTERDAY"
        else -> DAY.format(day).uppercase(Locale.getDefault())
    }

    /** "14 November" — no year, because a conversation nobody has scrolled that far back in. */
    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM")
}

/**
 * The maximum a bubble may be: wide enough to read, narrow enough that the side it is on is
 * obvious at a glance.
 *
 * A *maximum*, not a width. Every bubble being the same width is how the first draft looked, and
 * it made "ok" read as an announcement.
 */
internal const val BUBBLE_SHARE = 0.78f

/**
 * A photo's shape in the conversation, whatever shape the photo is.
 *
 * Uniform on purpose: a tall photo making a tall bubble and a wide one making a short one turns a
 * conversation into a ragged column. The photo is cropped to fit, and tapping it shows the whole
 * thing.
 */
internal const val PHOTO_RATIO = 4f / 3f
