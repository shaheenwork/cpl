package com.shnapps.couple.core.data.taxonomy

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The taxonomy really ships: the build copies `content/taxonomy.json` into the assets, and
 * the repository finds it there. A wrong path on either side would otherwise surface only
 * as a crash on a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BundledTaxonomyRepositoryTest {

    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
    }

    @Test
    fun `loads the taxonomy bundled into the assets`() = runTest {
        val repository = BundledTaxonomyRepository(RuntimeEnvironment.getApplication(), dispatchers)

        val bundled = repository.taxonomy.first()
        val source = TaxonomyParser.parse(File("../../content/taxonomy.json").readText())

        assertThat(bundled).isEqualTo(source)
        assertThat(bundled.items).isNotEmpty()
    }
}
