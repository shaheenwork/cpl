package com.shnapps.couple

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.data.content.ContentBundleParser
import com.shnapps.couple.core.data.content.ContentRepository
import com.shnapps.couple.core.data.taxonomy.TaxonomyRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Phase 8 exit criterion, on a device: the whole catalogue is there on first launch with no
 * network at all (BUILD_PROMPT.md §9.2, §22). Nothing here touches a server, so run it with
 * the device in airplane mode to prove the point; it needs no emulators either.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ContentOnDeviceTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var content: ContentRepository

    @Inject
    lateinit var taxonomy: TaxonomyRepository

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        // As on first launch: whatever an earlier run installed, synced or disabled is gone.
        context.deleteDatabase(CONTENT_DATABASE)
        hiltRule.inject()
    }

    @Test
    fun the_shipped_catalogue_installs_and_reads_without_a_network() = runBlocking {
        val shipped = ContentBundleParser.parse(
            context.assets.open("content/bundle.json").bufferedReader().use { it.readText() },
        )

        val items = withTimeout(TIMEOUT_MS) { content.items.first { it.isNotEmpty() } }

        assertThat(items.map { it.id }).containsExactlyElementsIn(shipped.items.map { it.id })
        assertThat(items.size).isAtLeast(MINIMUM_CATALOGUE)
        assertThat(content.packs.first().map { it.id })
            .containsExactlyElementsIn(ContentBundleParser.parsePacks(shipped.packsJson).map { it.id })
        assertThat(taxonomy.taxonomy.first().items).isNotEmpty()
    }

    private companion object {
        /** ContentDatabase.NAME; :app does not depend on :core:database directly. */
        const val CONTENT_DATABASE = "content.db"
        const val MINIMUM_CATALOGUE = 600
        const val TIMEOUT_MS = 30_000L
    }
}
