package com.shnapps.couple.core.data.entitlement

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.datastore.AppPreferences
import com.shnapps.couple.core.model.Entitlement
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.SubscriptionState
import com.shnapps.couple.core.model.Tier
import com.shnapps.couple.core.testing.FakeAppPreferencesStore
import com.shnapps.couple.core.testing.FakeBillingRepository
import com.shnapps.couple.core.testing.FakeLaunchOffer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * How far a phone may go, and why (V2 rule 5, D-151).
 *
 * V1 limited volume — thirty cards a day — and left every level open. V2 does the opposite, so what
 * is worth asserting here is the opposite too: never that something ran out, always that the
 * ceiling is where it should be and that the two free levels are untouchable.
 *
 * Free is a date now, set in Firebase. The tests that matter are the edges of it: the moment before
 * and the moment after, and what a date nobody could read does.
 *
 * The rule that earns the most tests is the ladder. A pack opens everything below it, because every
 * other part of this app treats a level as a ceiling: a couple who owned Extreme and not Spicy
 * would have a deck with a hole in the middle of it and a climb that skipped two rungs.
 */
class LevelAccessTest {

    private val preferences = FakeAppPreferencesStore()
    private val billing = FakeBillingRepository()

    /** Free, until [NOVEMBER_6]: what the console says, unless a test says otherwise. */
    private var offer = FakeLaunchOffer(isFree = true, freeUntilMillis = NOVEMBER_6)

    private fun repository() = DefaultLevelAccessRepository(
        preferences = preferences,
        billing = billing,
        launch = offer,
    )

    private suspend fun access(): LevelAccess = repository().access.first()

    /** The day after the free period: nothing open, and nothing to announce. */
    private fun over() {
        offer = FakeLaunchOffer(isFree = false, freeUntilMillis = null)
    }

    @Test
    fun `while the free period is on, everything is open, and it says when that ends`() = runTest {
        val access = access()

        assertThat(access.ceiling).isEqualTo(Intensity.EXTREME)
        assertThat(access.freeForNow).isTrue()
        assertThat(access.freeUntilMillis).isEqualTo(NOVEMBER_6)
        assertThat(access.locked).isEmpty()
        // Open because the date has not come, not because anything was bought: the store still has
        // three things to sell on the day it does.
        assertThat(access.isOwned(Intensity.SPICY)).isFalse()
    }

    @Test
    fun `when it is over, the free pair is what is left`() = runTest {
        over()

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
    fun `a date that has passed is not announced`() = runTest {
        // The offer knows its date and that it is over: the access must not carry it to a banner.
        offer = FakeLaunchOffer(isFree = false, freeUntilMillis = NOVEMBER_6)

        assertThat(access().freeUntilMillis).isNull()
    }

    @Test
    fun `no free period at all is the same as one that is over`() = runTest {
        offer = FakeLaunchOffer(isFree = false, freeUntilMillis = null)

        assertThat(access().ceiling).isEqualTo(Intensity.FLIRTY)
    }

    @Test
    fun `a pack opens the levels below it as well`() = runTest {
        over()
        preferences.unlock(setOf(Intensity.HOT))

        val access = access()

        assertThat(access.ceiling).isEqualTo(Intensity.HOT)
        assertThat(access.allows(Intensity.SPICY)).isTrue()
        assertThat(access.isOwned(Intensity.SPICY)).isTrue()
        assertThat(access.locked).containsExactly(Intensity.EXTREME)
    }

    @Test
    fun `a pack the partner bought opens the levels for this phone too`() = runTest {
        over()
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
        over()
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
        over()
        preferences.unlock(setOf(Intensity.SPICY))
        billing.partner.value = setOf(Intensity.EXTREME)
        assertThat(access().ceiling).isEqualTo(Intensity.EXTREME)

        billing.partner.value = emptySet()

        assertThat(access().ceiling).isEqualTo(Intensity.SPICY)
    }

    @Test
    fun `the top pack opens everything, which is what the bundle is`() = runTest {
        over()
        preferences.unlock(setOf(Intensity.EXTREME))

        val access = access()

        assertThat(access.ceiling).isEqualTo(Intensity.EXTREME)
        assertThat(access.anythingLocked).isFalse()
    }

    @Test
    fun `a pack bought during the free period is still there when it ends`() = runTest {
        preferences.unlock(setOf(Intensity.HOT))
        assertThat(access().isOwned(Intensity.HOT)).isTrue()

        over()

        // Nothing about the end of the free period takes away something paid for.
        assertThat(access().ceiling).isEqualTo(Intensity.HOT)
    }

    @Test
    fun `a subscription from before this change still opens everything`() = runTest {
        over()
        billing.current.value = Entitlement(tier = Tier.PREMIUM, state = SubscriptionState.ACTIVE)

        val access = access()

        assertThat(access.ceiling).isEqualTo(Intensity.EXTREME)
    }

    @Test
    fun `nothing can take the free pair away`() = runTest {
        over()
        preferences.state.value = AppPreferences(ownedLevels = emptySet())

        val access = access()

        // Not "they get Mild if they behave": the gentlest two are the ones a couple reaches for in
        // order to slow down, and those are never behind a price.
        assertThat(LevelAccess.ALWAYS_FREE.all(access::allows)).isTrue()
    }

    private companion object {
        /** 6 November 2026, the last millisecond of it in UTC. A fixed date, so nothing depends on the day it runs. */
        const val NOVEMBER_6 = 1_794_009_599_999L
    }
}
