package com.shnapps.couple.core.data.content

import android.content.Context
import android.content.pm.ApplicationInfo
import com.shnapps.couple.core.common.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The content bundle built into the app (BUILD_PROMPT.md §9.2): what first launch and a
 * device that has never been online use. The build copies `content/dist/bundle.json` into
 * the assets, so what ships is exactly what the validator passed.
 */
interface ShippedContent {
    /** The shipped bundle's version, without reading the whole bundle. */
    suspend fun version(): Int

    /**
     * Which build's assets these are: the version, and when this build was installed.
     *
     * The cache installs the shipped bundle when its version is *higher* than what it holds, which
     * is right for a phone that has been syncing — but it means an app update whose content changed
     * without the version changing never reaches the screen. A phone that had the old Heat Dice kept
     * playing it after the update (D-131). This is what tells the two apart.
     */
    suspend fun fingerprint(): String

    suspend fun read(): String
}

@Singleton
class AssetShippedContent @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : ShippedContent {

    override suspend fun version(): Int = withContext(dispatchers.io) {
        // The bundle is serialized with its header first (tools/src/bundle.ts), so the
        // version is in the first few lines. Falls back to a full parse if that ever changes.
        val fromHeader = context.assets.open(PATH).bufferedReader().use { reader ->
            reader.lineSequence().take(HEADER_LINES).firstNotNullOfOrNull { line ->
                VERSION_FIELD.find(line)?.groupValues?.get(1)?.toIntOrNull()
            }
        }
        fromHeader ?: ContentBundleParser.parse(read()).contentVersion
    }

    /**
     * The version and this build's install time. Not a hash of the bundle: the assets can only
     * change when the app is replaced, so the cheap fact is also the true one — and hashing a
     * megabyte on every cold start to learn it would be worse.
     */
    override suspend fun fingerprint(): String = withContext(dispatchers.io) {
        // A debuggable build has no fingerprint at all, so the shipped bundle installs on
        // every launch. Authoring content means running the app against a bundle that changed
        // while the app did not, and a cache that is right for a release is exactly wrong for
        // that: "why is it still showing the old cards" should not have an answer on a
        // developer's phone (D-132). A release keeps the cache — reinstalling eight hundred
        // items on every cold start is not a cost a user should pay.
        if (debuggable) {
            return@withContext "debug@${asked.incrementAndGet()}"
        }
        val installed = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        }.getOrDefault(0L)
        "${version()}@$installed"
    }

    /** Whether this build was built to be debugged: the one that reloads its content. */
    private val debuggable: Boolean
        get() = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /**
     * How many times a debuggable build has been asked. A counter rather than a clock: the
     * contract is only that the answer differs from the last one, and a clock that does not
     * advance — Robolectric's, for one — would quietly make it the same answer twice.
     */
    private val asked = AtomicLong()

    override suspend fun read(): String = withContext(dispatchers.io) {
        context.assets.open(PATH).bufferedReader().use { it.readText() }
    }

    companion object {
        const val PATH = "content/bundle.json"
        private const val HEADER_LINES = 5
        private val VERSION_FIELD = Regex("\"contentVersion\"\\s*:\\s*(\\d+)")
    }
}
