package com.shnapps.couple.core.data.content

import android.content.pm.ApplicationInfo
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

    /**
     * A build you can debug reloads its content every launch (D-132).
     *
     * Robolectric runs the app as debuggable, which is the case under test: the fingerprint has to
     * differ from one launch to the next, or an author who edits the bundle and reinstalls is told
     * their old cards are the current ones.
     */
    @Test
    fun `a debuggable build never looks like the build that installed the cache`() = runTest {
        debuggable(true)

        assertThat(shipped.fingerprint()).isNotEqualTo(shipped.fingerprint())
    }

    @Test
    fun `a release build looks the same until it is replaced`() = runTest {
        debuggable(false)

        // The cache is right here: the assets cannot change without the app being replaced, and
        // reinstalling the whole bundle on every cold start is a cost a user should not pay.
        assertThat(shipped.fingerprint()).isEqualTo(shipped.fingerprint())
    }

    private fun debuggable(on: Boolean) {
        val info = RuntimeEnvironment.getApplication().applicationInfo
        info.flags = if (on) {
            info.flags or ApplicationInfo.FLAG_DEBUGGABLE
        } else {
            info.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        }
    }

    @Test
    fun `reads the version from the header without parsing the whole bundle`() = runTest {
        assertThat(shipped.version()).isEqualTo(ContentBundleParser.parse(shipped.read()).contentVersion)
        assertThat(shipped.version()).isAtLeast(1)
    }
}
