package com.shnapps.couple.core.data

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.surprise.DefaultArcRepository
import com.shnapps.couple.core.firebase.surprise.ArcDoc
import com.shnapps.couple.core.firebase.surprise.ArcRemoteDataSource
import com.shnapps.couple.core.model.UserProfile
import com.shnapps.couple.core.testing.FakeAuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Listeners let go when nobody is listening (BUILD_PROMPT.md §18: "every listener is
 * lifecycle-scoped and verified released — write a leak test").
 *
 * The real listeners are Firestore's `addSnapshotListener`, so this counts them at the seam: a data
 * source whose `callbackFlow` records a registration on subscribe and drops it in `awaitClose`. A
 * repository that held the flow open — a `stateIn` on a scope of its own, a coroutine nobody
 * cancels, a cached collector — would leave the count above zero once its collector went away, and
 * this test fails.
 *
 * The arc repository stands for the pattern every observing repository here uses: the couple id
 * flows in, `flatMapLatest` swaps the listener, and the collector's lifetime is the listener's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListenerReleaseTest {

    private val remote = CountingArcRemote()

    private fun repository(coupleId: String?) = DefaultArcRepository(
        auth = FakeAuthRepository().apply {
            profile.value = coupleId?.let { UserProfile(uid = "u1", coupleId = it) }
        },
        remote = remote,
    )

    @Test
    fun `a listener lasts exactly as long as its collector`() = runTest {
        val arcs = repository("c1")

        val watching = launch { arcs.arc.collect {} }
        advanceUntilIdle()
        assertThat(remote.open).isEqualTo(1)
        assertThat(remote.registrations).isEqualTo(1)

        watching.cancel()
        advanceUntilIdle()

        assertThat(remote.open).isEqualTo(0)
    }

    @Test
    fun `two screens watching share nothing, and both let go`() = runTest {
        val arcs = repository("c1")

        val one = launch { arcs.arc.collect {} }
        val two = launch { arcs.arc.collect {} }
        advanceUntilIdle()
        assertThat(remote.open).isEqualTo(2)

        one.cancel()
        advanceUntilIdle()
        assertThat(remote.open).isEqualTo(1)

        two.cancel()
        advanceUntilIdle()
        assertThat(remote.open).isEqualTo(0)
    }

    @Test
    fun `nothing is listened to at all until something collects`() = runTest {
        repository("c1").arc
        advanceUntilIdle()

        assertThat(remote.registrations).isEqualTo(0)
    }

    @Test
    fun `signed out, there is nothing to listen to`() = runTest {
        val arcs = repository(null)

        val watching = launch { arcs.arc.collect {} }
        advanceUntilIdle()

        assertThat(remote.registrations).isEqualTo(0)
        watching.cancel()
    }

    /** Counts what it hands out: how many listeners are open now, and how many there have been. */
    private class CountingArcRemote : ArcRemoteDataSource {
        var open = 0
            private set
        var registrations = 0
            private set

        override fun observe(coupleId: String): Flow<List<ArcDoc>> = callbackFlow {
            open += 1
            registrations += 1
            trySend(emptyList())
            awaitClose { open -= 1 }
        }

        override suspend fun start(lengthDays: Int): Outcome<String> = Outcome.Success("arc")

        override suspend fun openDay(arcId: String, dayIndex: Int): Outcome<String> = Outcome.Success("night")

        override suspend fun close(arcId: String): Outcome<Unit> = Outcome.Success(Unit)
    }
}
