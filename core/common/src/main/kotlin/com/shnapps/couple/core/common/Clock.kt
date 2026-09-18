package com.shnapps.couple.core.common

/**
 * Injected rather than called statically, so time-dependent behaviour is testable.
 *
 * This matters more than it looks: the app-lock timeout, the reveal jitter window
 * (BUILD_PROMPT.md §5.3), scheduled surprises and multi-day arcs are all clock-driven,
 * and none of them can be tested honestly against a real `System.currentTimeMillis()`.
 */
fun interface Clock {
    fun nowMillis(): Long
}

object SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/** Test double. Time only moves when a test moves it. */
class FakeClock(var now: Long = 0L) : Clock {
    override fun nowMillis(): Long = now

    fun advanceBy(millis: Long) {
        now += millis
    }
}
