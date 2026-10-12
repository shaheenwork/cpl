package com.shnapps.couple.core.data.entitlement

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.FakeClock
import com.shnapps.couple.core.datastore.AppPreferences
import com.shnapps.couple.core.model.Entitlement
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.SubscriptionState
import com.shnapps.couple.core.model.Tier
import com.shnapps.couple.core.testing.FakeAppPreferencesStore
import com.shnapps.couple.core.testing.FakeBillingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * How far a phone may go, and why (V2 rule 5).
 *
 * V1 limited volume — thirty cards a day — and left every level open. V2 does the opposite, so what
 * is worth asserting here is the opposite too: never that something ran out, always that the
 * ceiling is where it should be and that the two free levels are untouchable.
 *
 * The rule that earns the most tests is the ladder. A pack opens everything below it, because every
 * other part of this app treats a level as a ceiling: a couple who owned Extreme and not Spicy
 * would have a deck with a hole in the middle of it and a climb that skipped two rungs.
 */
class LevelAccessTest {

    private val preferences = FakeAppPreferencesStore()
    private val billing = FakeBillingRepository()
    private val clock = FakeClock(NOW)

    private var installedAt = NOW

    private val repository = DefaultLevelAccessRepository(
        preferences = preferences,
        billing = billing,
        installed = object : InstallDate {
            override suspend fun firstInstalledAtMillis(): Long = installedAt
        },
        launch = offer(everythingFree = false),
        clock = clock,
    )

    private suspend fun access(): LevelAccess = repository.access.first()

    @Test
    fun `a phone just installed has everything, and knows when that ends`() = runTest {
        val access = access()

        assertThat(access.ceiling).isEqualTo(Intensity.EXTREME)
        assertThat(access.freeForNow).isTrue()
        assertThat(access.freeUntilMillis).isEqualTo(NOW + LevelAccess.OPENING_MONTH_MS)
        assertThat(access.locked).isEmpty()
        // Open because the month is on, not because anything was bought: the store still has
        // three things to sell on the day the month ends.
        assertThat(access.isOwned(Intensity.SPICY)).isFalse()
    }

    @Test
    fun `when the month is over, the free pair is what is left`() = runTest {
        installedAt = NOW - LevelAccess.OPENING_MONTH_MS - 1

        val access = access()

        assertThat(access.ceiling).isEqualTo(Intensity.FLIRTY)
        assertThat(access.freeForNow).isFalse()
        assertThat(access.allows(Intensity.MILD)).isTrue()
        assertThat(access.allows(Intensity.FLIRTY)).isTrue()
        assertThat(access.locked)
            .containsExactly(Intensity.SPICY, Intensity.HOT, Intensity.EXTREME)
            .inOrder()
    }

    @Test
    fun `a pack opens the levels below it as well`() = runTest {
        installedAt = NOW - LevelAccess.OPENING_MONTH_MS - 1
        preferences.unlock(setOf(Intensity.HOT))

        val access = access()

        assertThat(access.ceiling).isEqualTo(Intensity.HOT)
        assertThat(access.allows(Intensity.SPICY)).isTrue()
        assertThat(access.isOwned(Intensity.SPICY)).isTrue()
        assertThat(access.locked).containsExactly(Intensity.EXTREME)
    }

    @Test
    fun `a pack the partner bought opens the levels for this phone too`() = runTest {
        installedAt = NOW - LevelAccess.OPENING_MONTH_MS - 1
        billing.partner.value = setOf(Intensity.HOT)

        val access = access()

        // One of them paying is enough (D-149): this phone bought nothing and goes as far as Hot.
        assertThat(access.ceiling).isEqualTo(Intensity.HOT)
        assertThat(access.allows(Intensity.SPICY)).isTrue()
        assertThat(access.isOwned(Intensity.HOT)).isTrue()
        assertThat(access.locked).containsExactly(Intensity.EXTREME)
    }

    @Test
    fun `the two phones' packs add up to the further of them, never less than either`() = runTest {
        installedAt = NOW - LevelAccess.OPENING_MONTH_MS - 1
        preferences.unlock(setOf(Intensity.SPICY))
        billing.partner.value = setOf(Intensity.EXTREME)

        assertThat(access().ceiling).isEqualTo(Intensity.EXTREME)

        // And the other way round: a partner who bought less takes nothing from what was bought here.
        billing.partner.value = setOf(Intensity.SPICY)
        preferences.unlock(setOf(Intensity.HOT))
        assertThat(access().ceiling).isEqualTo(Intensity.HOT)
    }

    @Test
    fun `a partner who unpairs takes their packs with them, and nobody else's`() = runTest {
        installedAt = NOW - LevelAccess.OPENING_MONTH_MS - 1
        preferences.unlock(setOf(Intensity.SPICY))
        billing.partner.value = setOf(Intensity.EXTREME)
        assertThat(access().ceiling).isEqualTo(Intensity.EXTREME)

        billing.partner.value = emptySet()

        assertThat(access().ceiling).isEqualTo(Intensity.SPICY)
    }

    @Test
    fun `the top pack opens everything, which is what the bundle is`() = runTest {
        installedAt = NOW - LevelAccess.OPENING_MONTH_MS - 1
        preferences.unlock(setOf(Intensity.EXTREME))

        val access = access()

        assertThat(access.ceiling).isEqualTo(Intensity.EXTREME)
        assertThat(access.anythingLocked).isFalse()
    }

    @Test
    fun `a subscription from before this change still opens everything`() = runTest {
        installedAt = NOW - LevelAccess.OPENING_MONTH_MS - 1
        billing.current.value = Entitlement(tier = Tier.PREMIUM, state = SubscriptionState.ACTIVE)

        val access = access()

        assertThat(access.ceiling).isEqualTo(Intensity.EXTREME)
    }

    @Test
    fun `the launch switch opens everything with no date set, and no network`() = runTest {
        installedAt = NOW - LevelAccess.OPENING_MONTH_MS - 1
        val switched = DefaultLevelAccessRepository(
            preferences = preferences,
            billing = billing,
            installed = object : InstallDate {
                override suspend fun firstInstalledAtMillis(): Long = installedAt
            },
            launch = offer(everythingFree = true, until = 0),
            clock = clock,
        )

        val access = switched.access.first()

        assertThat(access.ceiling).isEqualTo(Intensity.EXTREME)
        assertThat(access.freeForNow).isTrue()
        // Nothing to say about when it ends, so the banner says nothing rather than guessing.
        assertThat(access.freeUntilMillis).isNull()
    }

    @Test
    fun `a package manager that will not say is treated as a phone that just got the app`() = runTest {
        installedAt = 0

        val access = access()

        assertThat(access.ceiling).isEqualTo(Intensity.EXTREME)
        assertThat(access.freeForNow).isTrue()
    }

    @Test
    fun `nothing can take the free pair away`() = runTest {
        installedAt = NOW - LevelAccess.OPENING_MONTH_MS - 1
        preferences.state.value = AppPreferences(ownedLevels = emptySet())

        val access = access()

        // Not "they get Mild if they behave": the gentlest two are the ones a couple reaches for in
        // order to slow down, and those are never behind a price.
        assertThat(LevelAccess.ALWAYS_FREE.all(access::allows)).isTrue()
    }

    private fun offer(everythingFree: Boolean, until: Long = 0) = object : LaunchOffer {
        override val everythingFree: Boolean = everythingFree
        override val freeUntilMillis: Long? = until.takeIf { it > 0 }
        override val daysLeft: Int? = null
        override val shouldRemind: Boolean = false
    }

    private companion object {
        /** 11 October 2026, midday UTC. A fixed clock, so nothing depends on the day it runs. */
        const val NOW = 1_791_720_000_000L
    }
}
