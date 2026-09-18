package com.shnapps.couple.core.data.content

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The bundle really ships: the build copies `content/dist/bundle.json` into the assets and
 * the app finds it there. A wrong path on either side would otherwise surface only as an
 * empty app on a device with no network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AssetShippedContentTest {

    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private val shipped = AssetShippedContent(RuntimeEnvironment.getApplication(), dispatchers)

    @Test
    fun `the assets hold exactly the committed bundle`() = runTest {
        val committed = File("../../content/dist/bundle.json").readText()

        assertThat(shipped.read().replace("\r\n", "\n")).isEqualTo(committed.replace("\r\n", "\n"))
    }

    @Test
    fun `reads the version from the header without parsing the whole bundle`() = runTest {
        assertThat(shipped.version()).isEqualTo(ContentBundleParser.parse(shipped.read()).contentVersion)
        assertThat(shipped.version()).isAtLeast(1)
    }
}
