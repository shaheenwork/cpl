package com.shnapps.couple.core.data.billing

import android.app.Activity
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.billing.BillingRemoteDataSource
import com.shnapps.couple.core.firebase.billing.EntitlementDoc
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.UserProfile
import com.shnapps.couple.core.testing.FakeAppPreferencesStore
import com.shnapps.couple.core.testing.FakeAuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A pack is bought once for the couple (D-149).
 *
 * V2 sold packs to a phone, because a phone with no account has nowhere else to put a purchase. A
 * couple is two phones, though, and the one that did not pay was left at Flirty beside the one that
 * did. What matters here is both halves of the fix: that what a phone holds reaches the couple's
 * record, however late the couple forms, and that what the other phone holds reaches this one —
 * and never the other way round, so a phone cannot speak for its partner.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BillingRepositoryTest {

    private val auth = FakeAuthRepository()
    private val store = FakeAppPreferencesStore()
    private val remote = FakeRemote()

    private val repository = DefaultBillingRepository(
        auth = auth,
        remote = remote,
        client = object : BillingClient {
            override suspend fun offers() = Outcome.Success(emptyList<PackOffer>())
            override suspend fun purchase(activity: Activity, productId: String) = Outcome.Success("token")
            override suspend fun owned() = Outcome.Success(emptySet<String>())
        },
        purchases = store,
    )

    private fun pairedAs(uid: String = ME, couple: String = COUPLE) {
        auth.profile.value = UserProfile(uid = uid, coupleId = couple)
    }

    private fun TestScope.sharing() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.shareOwnership() }
        advanceUntilIdle()
    }

    @Test
    fun `what this phone has bought reaches the couple, as the furthest it reaches`() = runTest {
        pairedAs()
        store.unlock(setOf(Intensity.SPICY, Intensity.HOT))

        sharing()

        // One number per member: a pack opens everything below it, so the furthest is all there is.
        assertThat(remote.published).containsExactly(Triple(COUPLE, ME, 4))
    }

    @Test
    fun `buying a further pack says so again, and only once`() = runTest {
        pairedAs()
        sharing()
        assertThat(remote.published).isEmpty()

        store.unlock(setOf(Intensity.SPICY))
        advanceUntilIdle()
        store.unlock(setOf(Intensity.EXTREME))
        advanceUntilIdle()

        assertThat(remote.published).containsExactly(Triple(COUPLE, ME, 3), Triple(COUPLE, ME, 5)).inOrder()
    }

    @Test
    fun `a pack bought before there was a couple reaches the partner who joins later`() = runTest {
        store.unlock(setOf(Intensity.HOT))
        sharing()
        assertThat(remote.published).isEmpty()

        pairedAs()
        advanceUntilIdle()

        assertThat(remote.published).containsExactly(Triple(COUPLE, ME, 4))
    }

    @Test
    fun `nothing bought says nothing, and nobody paired has nobody to say it to`() = runTest {
        pairedAs()
        sharing()
        assertThat(remote.published).isEmpty()

        auth.profile.value = null
        store.unlock(setOf(Intensity.EXTREME))
        advanceUntilIdle()

        assertThat(remote.published).isEmpty()
    }

    @Test
    fun `the partner's packs are the ones that are not mine`() = runTest {
        pairedAs()
        remote.packs.value = mapOf(ME to 4, PARTNER to 5)

        // Mine is already on this phone: only what the other one holds is news.
        assertThat(repository.partnerPacks.first()).containsExactly(Intensity.EXTREME)
    }

    @Test
    fun `a partner who has bought nothing, or nobody paired, is nothing to read`() = runTest {
        remote.packs.value = mapOf(PARTNER to 5)
        assertThat(repository.partnerPacks.first()).isEmpty()

        pairedAs()
        remote.packs.value = mapOf(ME to 3)
        assertThat(repository.partnerPacks.first()).isEmpty()
    }

    @Test
    fun `a record that cannot be read is no packs, not an error`() = runTest {
        pairedAs()
        remote.fail = true

        // The one thing worse than a level that did not arrive is a screen complaining about it.
        assertThat(repository.partnerPacks.first()).isEmpty()
    }

    @Test
    fun `a level a pack cannot be is ignored rather than trusted`() = runTest {
        pairedAs()
        remote.packs.value = mapOf(PARTNER to 9)

        assertThat(repository.partnerPacks.first()).isEmpty()
    }

    private class FakeRemote : BillingRemoteDataSource {
        val packs = MutableStateFlow<Map<String, Int>>(emptyMap())
        val published = mutableListOf<Triple<String, String, Int>>()
        var fail = false

        override fun observe(coupleId: String): Flow<EntitlementDoc> = emptyFlow()

        override suspend fun verify(purchaseToken: String): Outcome<String> = Outcome.Success("FREE")

        override suspend fun current(): Outcome<EntitlementDoc> = Outcome.Success(EntitlementDoc("FREE", null, null))

        override fun observePacks(coupleId: String): Flow<Map<String, Int>> =
            if (fail) flow { error("cannot read the couple's record") } else packs

        override suspend fun publishPack(coupleId: String, uid: String, level: Int): Outcome<Unit> {
            published += Triple(coupleId, uid, level)
            return Outcome.Success(Unit)
        }
    }

    private companion object {
        const val ME = "uid_me"
        const val PARTNER = "uid_partner"
        const val COUPLE = "couple_1"
    }
}
