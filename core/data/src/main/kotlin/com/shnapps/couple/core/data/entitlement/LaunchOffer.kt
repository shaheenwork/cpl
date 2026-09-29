package com.shnapps.couple.core.data.entitlement

import com.shnapps.couple.core.common.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The launch period: everything free until a date, then premium (M9).
 *
 * Two Remote Config values and nothing else. `everything_free` is the switch, and
 * `everything_free_until` is the date the app shows. Turning it off is a console change rather than
 * a release, which is exactly what the brief asks for — and it is the switch rather than the date
 * that decides, so a date that slips does not accidentally start charging anybody.
 *
 * The date is only ever used to *say* something: the banner, and the two reminders. Nothing is
 * gated on it. A clock that is wrong, or a fetch that has not landed, must never be the reason a
 * couple loses something they had.
 */
interface LaunchOffer {
    /** Whether nothing at all is counted right now. */
    val everythingFree: Boolean

    /** When that ends, for the banner. Null when the date is not set or has passed. */
    val freeUntilMillis: Long?

    /** Days until it ends, or null when there is nothing to count down to. */
    val daysLeft: Int?

    /**
     * Whether to say something about it now.
     *
     * At the start, and again at fourteen days and three days, which is what the brief asks for. A
     * banner that is always up is wallpaper; one that appears twice is a reminder.
     */
    val shouldRemind: Boolean
}

@Singleton
class RemoteLaunchOffer @Inject constructor(
    private val config: LaunchConfig,
    private val clock: Clock,
) : LaunchOffer {

    override val everythingFree: Boolean get() = config.everythingFree()

    override val freeUntilMillis: Long?
        get() = config.freeUntilMillis().takeIf { it > 0 && it > clock.nowMillis() }

    override val daysLeft: Int?
        get() = freeUntilMillis?.let { until ->
            ((until - clock.nowMillis()) / DAY_MS).toInt().coerceAtLeast(0)
        }

    override val shouldRemind: Boolean
        get() = everythingFree && daysLeft?.let { it in REMINDERS } == true

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000

        /** Fourteen days out and three days out. The brief's two. */
        val REMINDERS = setOf(3, 14)
    }
}

/**
 * The two values the launch period needs, behind a seam.
 *
 * Remote Config lives in `:core:firebase`, which `:core:data` may not import (BUILD_PROMPT.md §4).
 * This is the narrow interface the app binds to the real source, and that a test binds to two
 * numbers.
 */
interface LaunchConfig {
    fun everythingFree(): Boolean

    /** Epoch milliseconds, or 0 when unset. */
    fun freeUntilMillis(): Long
}
