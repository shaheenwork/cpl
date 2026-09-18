package com.shnapps.couple.core.database.content

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContentDaoTest {

    private lateinit var database: ContentDatabase
    private lateinit var dao: ContentDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ContentDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.contentDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun header(version: Int, cursor: Long = 0L) = ContentBundleEntity(
        format = 1,
        contentVersion = version,
        taxonomyVersion = 1,
        taxonomyJson = "{}",
        packsJson = "[]",
        lastSyncMillis = null,
        deltaCursorMillis = cursor,
    )

    private fun row(id: String, pack: String = "FLIRT") =
        ContentItemEntity(id, 1, pack, "published", "{\"id\":\"$id\"}")

    @Test
    fun `installing a bundle replaces every item and the header at once`() = runTest {
        dao.installBundle(header(1), listOf(row("flirt_a"), row("flirt_b")))
        dao.installBundle(header(2), listOf(row("tease_c", pack = "TEASE")))

        assertThat(dao.observeItems().first().map { it.id }).containsExactly("tease_c")
        assertThat(dao.bundle()?.contentVersion).isEqualTo(2)
    }

    @Test
    fun `deltas survive a bundle install and upsert by id`() = runTest {
        dao.upsertDeltas(listOf(ContentDeltaEntity("flirt_a", "disabled", 1, null, 10L)))
        dao.installBundle(header(1), listOf(row("flirt_a")))
        dao.upsertDeltas(listOf(ContentDeltaEntity("flirt_a", "published", 1, null, 20L)))

        val deltas = dao.observeDeltas().first()
        assertThat(deltas).hasSize(1)
        assertThat(deltas.single().status).isEqualTo("published")
    }

    @Test
    fun `marking a sync records the time and the delta cursor`() = runTest {
        dao.installBundle(header(1), emptyList())

        dao.markSynced(syncedAtMillis = 500L, deltaCursorMillis = 400L)

        val bundle = dao.observeBundle().first()
        assertThat(bundle?.lastSyncMillis).isEqualTo(500L)
        assertThat(bundle?.deltaCursorMillis).isEqualTo(400L)
    }

    @Test
    fun `an empty cache has no header`() = runTest {
        assertThat(dao.bundle()).isNull()
        assertThat(dao.observeItems().first()).isEmpty()
    }
}
