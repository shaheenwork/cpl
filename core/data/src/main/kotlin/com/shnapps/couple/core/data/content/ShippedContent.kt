package com.shnapps.couple.core.data.content

import android.content.Context
import com.shnapps.couple.core.common.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
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

    override suspend fun read(): String = withContext(dispatchers.io) {
        context.assets.open(PATH).bufferedReader().use { it.readText() }
    }

    companion object {
        const val PATH = "content/bundle.json"
        private const val HEADER_LINES = 5
        private val VERSION_FIELD = Regex("\"contentVersion\"\\s*:\\s*(\\d+)")
    }
}
