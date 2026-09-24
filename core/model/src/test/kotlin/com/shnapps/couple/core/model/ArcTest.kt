package com.shnapps.couple.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Which day of an arc is due (BUILD_PROMPT.md §13). */
class ArcTest {

    private fun day(index: Int, state: ArcDayState, unlockAt: Long) =
        ArcDay(index = index, title = "Day $index", state = state, unlockAtMillis = unlockAt, experienceId = null)

    private fun arc(vararg days: ArcDay) =
        Arc(id = "a", title = "3 days", lengthDays = days.size, active = true, days = days.toList())

    @Test
    fun `the first day is due the moment the arc starts`() {
        // Started a second ago by the server's clock, which this phone's clock has not reached.
        val arc = arc(
            day(0, ArcDayState.READY, NOW + 1_000),
            day(1, ArcDayState.LOCKED, NOW + DAY_MS),
        )

        assertThat(arc.dayDue(NOW)?.index).isEqualTo(0)
    }

    @Test
    fun `a later day waits for its own day`() {
        val arc = arc(
            day(0, ArcDayState.DONE, NOW - DAY_MS),
            day(1, ArcDayState.LOCKED, NOW + DAY_MS),
        )

        assertThat(arc.dayDue(NOW)).isNull()
        assertThat(arc.dayDue(NOW + DAY_MS)?.index).isEqualTo(1)
    }

    @Test
    fun `a played day is behind them`() {
        val arc = arc(
            day(0, ArcDayState.DONE, NOW - DAY_MS),
            day(1, ArcDayState.READY, NOW - 1),
            day(2, ArcDayState.LOCKED, NOW + DAY_MS),
        )

        assertThat(arc.dayDue(NOW)?.index).isEqualTo(1)
    }

    private companion object {
        const val NOW = 1_790_000_000_000L
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
