package com.shnapps.couple.core.data.entitlement

import com.shnapps.couple.core.common.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The free period: everything open until a date, then the packs (D-151).
 *
 * One value, in Firebase: Remote Config `free_until`. The owner sets the date in the console and
 * publishes, and nobody has to ship a release to move it, bring it forward, or end it.
 *
 * - **A date**, as `2026-11-06`: open through the end of that day on the phone's own clock, which
 *   is what "until the sixth" means to the person holding it.
 * - **A moment**, as `2026-11-06T18:00:00Z` or `2026-11-06T18:00:00+01:00`: exactly then.
 * - **Blank**: there is no free period. Clearing it is how the offer ends today.
 * - **Anything else**: ignored, and the date shipped in the app applies. A typo in a console must
 *   not be able to switch everybody's levels off.
 *
 * Until this, free was "a month from install, or while a switch was on" — and the switch defaulted
 * to on, so in practice it never ended. A date is the thing somebody can plan around, say on a
 * banner, and change.
 *
 * It is a gate now and not only a caption: after the date, Spicy, Hot and Extreme are packs. What it
 * is *not* is a cut-off in the middle of an evening — the ceiling is applied when a card is chosen,
 * so a couple at Hot at midnight finish the card they are on (V2 rule 5).
 */
interface LaunchOffer {
    /** When everything stops being free, or null when there is no free period at all. */
    val freeUntilMillis: Long?

    /** Whether everything is open right now: there is a date, and the clock has not reached it. */
    val isFree: Boolean

    /** Whole days until it ends, or null when it is not on. */
    val daysLeft: Int?

    /**
     * Whether to say something about it now.
     *
     * At fourteen days and again at three, which is what the brief asks for. A banner that is always
     * up is wallpaper; one that appears twice is a reminder.
     */
    val shouldRemind: Boolean
}

@Singleton
class RemoteLaunchOffer @Inject constructor(
    private val config: LaunchConfig,
    private val clock: Clock,
) : LaunchOffer {

    override val freeUntilMillis: Long? get() = FreeUntil.parse(config.freeUntil())

    override val isFree: Boolean get() = freeUntilMillis?.let { clock.nowMillis() <= it } == true

    override val daysLeft: Int?
        get() = freeUntilMillis?.takeIf { clock.nowMillis() <= it }?.let { until ->
            ((until - clock.nowMillis()) / DAY_MS).toInt()
        }

    override val shouldRemind: Boolean get() = daysLeft in REMINDERS

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000

        /** Fourteen days out and three days out. The brief's two. */
        val REMINDERS = setOf(3, 14)
    }
}

/** Reading `free_until`. Pure, so every spelling the console might be given is a test. */
object FreeUntil {

    /**
     * The date the app ships with, for a phone that has not fetched anything and for a value that
     * cannot be read. The same string as the Remote Config default in `:core:firebase`, which
     * cannot be imported from here.
     */
    const val DEFAULT = "2026-11-06"

    /** Epoch milliseconds the free period ends at, or null when there is none. */
    fun parse(raw: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        return read(text, zone) ?: read(DEFAULT, zone)
    }

    private fun read(text: String, zone: ZoneId): Long? = when {
        // A number is a moment, in seconds or in milliseconds: both are what somebody pastes.
        text.all(Char::isDigit) -> text.toLongOrNull()?.let { if (it < SECONDS_BELOW) it * MILLIS else it }
        else -> endOfDay(text, zone) ?: instant(text) ?: local(text, zone)
    }

    /** `2026-11-06` is the whole of the sixth, so it ends a millisecond before the seventh begins. */
    private fun endOfDay(text: String, zone: ZoneId): Long? = runCatching {
        LocalDate.parse(text).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    }.getOrNull()

    private fun instant(text: String): Long? = runCatching {
        OffsetDateTime.parse(text).toInstant().toEpochMilli()
    }.getOrNull()

    private fun local(text: String, zone: ZoneId): Long? = runCatching {
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    /** Below this a number can only be seconds: in milliseconds it would be a day in January 1973. */
    private const val SECONDS_BELOW = 100_000_000_000L
    private const val MILLIS = 1_000L
}

/**
 * The one value the free period needs, behind a seam.
 *
 * Remote Config lives in `:core:firebase`, which `:core:data` may not import (BUILD_PROMPT.md §4).
 * This is the narrow interface the app binds to the real source, and that a test binds to a string.
 */
interface LaunchConfig {
    /** The raw `free_until`, as the console holds it. */
    fun freeUntil(): String
}
