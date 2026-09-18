package com.shnapps.couple.core.data.content

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.DispatcherProvider
import com.shnapps.couple.core.common.FakeClock
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.database.content.ContentDatabase
import com.shnapps.couple.core.firebase.content.ContentDelta
import com.shnapps.couple.core.model.ContentItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The content cache against a real (in-memory) Room database: first-run install from the
 * shipped bundle, bundle updates, and the admin deltas laid over them (BUILD_PROMPT.md §9.2,
 * §9.6). The server side is a fake; everything on the device is real.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultContentRepositoryTest {

    private lateinit var database: ContentDatabase
    private val shipped = FakeShippedContent(ContentFixtures.bundle(1, listOf(item("flirt_a"), item("flirt_b"))))
    private val remote = FakeContentRemote()
    private val clock = FakeClock(now = 1_000_000L)

    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ContentDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun TestScope.repository() =
        DefaultContentRepository(database.contentDao(), shipped, remote, clock, dispatchers, backgroundScope)

    /**
     * The items once the cache has caught up with [predicate], or as they are after a short
     * wait. Room notifies observers on its own thread, so right after a write the shared flow
     * may still replay the previous list; waiting in real time keeps the tests honest without
     * sleeping, and a wrong expectation still fails with a readable diff.
     */
    private suspend fun DefaultContentRepository.settled(predicate: (List<ContentItem>) -> Boolean): List<ContentItem> =
        withContext(Dispatchers.Default) { withTimeoutOrNull(SETTLE_MS) { items.first(predicate) } } ?: items.first()

    private suspend fun DefaultContentRepository.assertIds(vararg expected: String) {
        val want = expected.sorted()
        assertThat(settled { ids(it) == want }.let(::ids)).isEqualTo(want)
    }

    private suspend fun DefaultContentRepository.titleOf(id: String, expected: String): String? =
        settled { list -> list.any { it.id == id && it.title == expected } }.firstOrNull { it.id == id }?.title

    private fun ids(items: List<ContentItem>): List<String> = items.map(ContentItem::id).sorted()

    private fun item(id: String, version: Int = 1, title: String = "Title of $id", status: String = "published") =
        ContentFixtures.item(id, version = version, title = title, status = status)

    private fun delta(id: String, status: String, version: Int = 1, itemJson: String? = null, at: Long = 2_000L) =
        ContentDelta(id, status, version, itemJson, updatedAtMillis = at)

    // ---- install ---------------------------------------------------------------------------

    @Test
    fun `installs the shipped bundle on first use, with no network at all`() = runTest {
        val repository = repository()

        repository.assertIds("flirt_a", "flirt_b")
        assertThat(repository.taxonomy.first().item("teasing_verbal")).isNotNull()
        assertThat(repository.packs.first().single().id).isEqualTo("FLIRT")
        assertThat(repository.state.first().contentVersion).isEqualTo(1)
        assertThat(remote.downloads).isEmpty()
    }

    @Test
    fun `keeps a newer downloaded bundle over an older shipped one`() = runTest {
        remote.latest = Outcome.Success(2)
        remote.bundles[2] = ContentFixtures.bundle(2, listOf(item("flirt_c")))
        repository().sync()

        val afterRestart = repository()

        afterRestart.assertIds("flirt_c")
        assertThat(shipped.reads).isEqualTo(1)
    }

    @Test
    fun `installs a newer shipped bundle after an app update`() = runTest {
        repository().ensureInstalled()
        shipped.text = ContentFixtures.bundle(2, listOf(item("flirt_a"), item("flirt_z")))

        repository().assertIds("flirt_a", "flirt_z")
    }

    @Test
    fun `the taxonomy updates with the bundle`() = runTest {
        remote.latest = Outcome.Success(2)
        remote.bundles[2] = ContentFixtures.bundle(2, listOf(item("flirt_a")), taxonomyPrompt = "Teasing words")
        val repository = repository()

        repository.sync()

        assertThat(repository.taxonomy.first().item("teasing_verbal")?.prompt).isEqualTo("Teasing words")
    }

    // ---- bundle sync -----------------------------------------------------------------------

    @Test
    fun `sync installs a newer bundle when the pointer moves`() = runTest {
        remote.latest = Outcome.Success(2)
        remote.bundles[2] =
            ContentFixtures.bundle(2, listOf(item("flirt_a", version = 2, title = "Edited"), item("flirt_c")))
        val repository = repository()

        val result = repository.sync()

        assertThat(result).isEqualTo(Outcome.Success(ContentSyncResult(installedVersion = 2, deltasReceived = 0)))
        repository.assertIds("flirt_a", "flirt_c")
        assertThat(repository.titleOf("flirt_a", "Edited")).isEqualTo("Edited")
        assertThat(repository.state.first().contentVersion).isEqualTo(2)
    }

    @Test
    fun `sync downloads nothing while the pointer has not moved`() = runTest {
        val repository = repository()

        val result = repository.sync()

        assertThat(result).isEqualTo(Outcome.Success(ContentSyncResult(installedVersion = null, deltasReceived = 0)))
        assertThat(remote.downloads).isEmpty()
    }

    @Test
    fun `refuses a bundle that is not the version it was fetched as`() = runTest {
        remote.latest = Outcome.Success(3)
        remote.bundles[3] = ContentFixtures.bundle(2, listOf(item("flirt_c")))
        val repository = repository()

        val result = repository.sync()

        assertThat((result as Outcome.Failure).error).isInstanceOf(AppError.Validation::class.java)
        repository.assertIds("flirt_a", "flirt_b")
    }

    @Test
    fun `refuses a bundle it cannot read, keeping what it has`() = runTest {
        remote.latest = Outcome.Success(2)
        remote.bundles[2] = "{ truncated"
        val repository = repository()

        val result = repository.sync()

        assertThat((result as Outcome.Failure).error).isInstanceOf(AppError.Validation::class.java)
        repository.assertIds("flirt_a", "flirt_b")
    }

    // ---- deltas ----------------------------------------------------------------------------

    @Test
    fun `a delta disables an item without a release, and enabling brings it back`() = runTest {
        val repository = repository()
        remote.deltas += delta("flirt_a", "disabled", at = 2_000L)

        repository.sync()
        repository.assertIds("flirt_b")

        remote.deltas.clear()
        remote.deltas += delta("flirt_a", "published", at = 3_000L)
        repository.sync()
        repository.assertIds("flirt_a", "flirt_b")
    }

    @Test
    fun `an edited item in a delta replaces the bundled copy`() = runTest {
        val repository = repository()
        val fixed = item("flirt_a", version = 2, title = "Fixed")
        remote.deltas += delta("flirt_a", "published", version = 2, itemJson = fixed)

        repository.sync()

        assertThat(repository.titleOf("flirt_a", "Fixed")).isEqualTo("Fixed")
    }

    @Test
    fun `a later bundle that raises the item version supersedes an older delta`() = runTest {
        val repository = repository()
        remote.deltas += delta("flirt_a", "disabled", version = 1)
        repository.sync()
        repository.assertIds("flirt_b")

        remote.latest = Outcome.Success(2)
        remote.bundles[2] = ContentFixtures.bundle(2, listOf(item("flirt_a", version = 2), item("flirt_b")))
        repository.sync()

        repository.assertIds("flirt_a", "flirt_b")
    }

    @Test
    fun `an item added by a delta appears before any bundle carries it`() = runTest {
        val repository = repository()
        remote.deltas += delta("flirt_new", "published", itemJson = item("flirt_new"))

        repository.sync()

        repository.assertIds("flirt_a", "flirt_b", "flirt_new")
    }

    @Test
    fun `a delta this version cannot read hides its item - fail closed`() = runTest {
        val repository = repository()
        remote.deltas += delta("flirt_a", "quarantined")
        remote.deltas += delta("flirt_b", "published", version = 2, itemJson = "{ not an item }")

        repository.sync()

        repository.assertIds()
    }

    @Test
    fun `deltas still land when the bundle download fails, and the failure is reported`() = runTest {
        remote.latest = Outcome.Success(2)
        val repository = repository()
        remote.deltas += delta("flirt_a", "disabled")

        val result = repository.sync()

        assertThat((result as Outcome.Failure).error).isInstanceOf(AppError.NotFound::class.java)
        repository.assertIds("flirt_b")
    }

    @Test
    fun `the delta cursor moves forward and the sync time is recorded`() = runTest {
        val repository = repository()
        remote.deltas += delta("flirt_a", "disabled", at = 5_000L)
        remote.deltas += delta("flirt_b", "disabled", at = 7_000L)

        repository.sync()
        repository.sync()

        assertThat(remote.cursors).containsExactly(0L, 7_000L).inOrder()
        assertThat(repository.state.first().lastSyncMillis).isEqualTo(clock.now)
        assertThat(repository.state.first().deltaCount).isEqualTo(2)
    }

    private companion object {
        const val SETTLE_MS = 2_000L
    }
}
