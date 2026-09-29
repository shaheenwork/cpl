package com.shnapps.couple.core.data.entitlement

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.FakeClock
import com.shnapps.couple.core.model.Entitlement
import com.shnapps.couple.core.model.Tier
import com.shnapps.couple.core.testing.FakeAppPreferencesStore
import com.shnapps.couple.core.testing.FakeBillingRepository
import com.shnapps.couple.core.testing.FakeLaunchOffer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * What the free tier allows (M9).
 *
 * The assertions that matter are about what it never touches. No tier is gated, no game is gated,
 * and a run that has started is never stopped — what is limited is volume, and the two limits are
 * counted per day and per week from the phone's own record so they work with no account at all.
 */
class AllowanceTest {

    private val preferences = FakeAppPreferencesStore()
    private val billing = FakeBillingRepository()
    private val clock = FakeClock(NOW)

    private fun allowances(free: Boolean = false) =
        DefaultAllowances(preferences, preferences, billing, FakeLaunchOffer(everythingFree = free), clock)

    @Test
    fun `a fresh day has the whole day's cards`() = runTest {
        val allowance = allowances().allowance.first()

        assertThat(allowance.cardsLeft).isEqualTo(Allowance.CARDS_A_DAY)
        assertThat(allowance.canDealCard).isTrue()
        assertThat(allowance.gameNightsLeft).isEqualTo(Allowance.NIGHTS_A_WEEK)
    }

    @Test
    fun `cards count down, and run out`() = runTest {
        val allowances = allowances()
        repeat(Allowance.CARDS_A_DAY) { allowances.cardDealt() }

        val allowance = allowances.allowance.first()
        assertThat(allowance.cardsLeft).isEqualTo(0)
        assertThat(allowance.canDealCard).isFalse()
    }

    @Test
    fun `tomorrow is a fresh day, with no job to reset it`() = runTest {
        val allowances = allowances()
        repeat(Allowance.CARDS_A_DAY) { allowances.cardDealt() }
        assertThat(allowances.allowance.first().canDealCard).isFalse()

        clock.now = NOW + DAY_MS

        // The stored count carries the day it belongs to, so a new day reads as untouched rather
        // than needing something to have run overnight.
        assertThat(allowances.allowance.first().cardsLeft).isEqualTo(Allowance.CARDS_A_DAY)
    }

    @Test
    fun `premium is not counted at all`() = runTest {
        billing.current.value = Entitlement(tier = Tier.PREMIUM)
        val allowances = allowances()
        repeat(Allowance.CARDS_A_DAY * 2) { allowances.cardDealt() }

        val allowance = allowances.allowance.first()
        assertThat(allowance.unlimited).isTrue()
        assertThat(allowance.canDealCard).isTrue()
        assertThat(allowance.canPlanNight).isTrue()
    }

    @Test
    fun `nothing is counted during the launch period`() = runTest {
        val allowances = allowances(free = true)
        repeat(Allowance.CARDS_A_DAY * 2) { allowances.cardDealt() }

        assertThat(allowances.allowance.first().unlimited).isTrue()
        assertThat(allowances.allowance.first().canDealCard).isTrue()
        // And nothing is written, so switching the flag off does not reveal a backlog of counts.
        assertThat(preferences.preferences.first().cardsDealt).isEqualTo(0)
    }

    @Test
    fun `one guided evening a week, and next week is another`() = runTest {
        val allowances = allowances()
        allowances.nightPlanned()

        assertThat(allowances.allowance.first().canPlanNight).isFalse()

        clock.now = NOW + WEEK_MS

        assertThat(allowances.allowance.first().canPlanNight).isTrue()
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val WEEK_MS = 7 * DAY_MS

        /** A fixed moment, well inside a day, so nothing here depends on a boundary. */
        const val NOW = 1_790_000_000_000L
    }
}
