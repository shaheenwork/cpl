package com.shnapps.couple.core.data.taxonomy

import android.content.Context
import com.shnapps.couple.core.common.DispatcherProvider
import com.shnapps.couple.core.model.Taxonomy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The taxonomy of private curiosities (BUILD_PROMPT.md §9.5).
 *
 * A flow rather than a value because the taxonomy is remotely updatable: Phase 8's content
 * pipeline can publish a newer version through the same stream without an app release.
 */
interface TaxonomyRepository {
    val taxonomy: Flow<Taxonomy>
}

/**
 * The taxonomy shipped inside the app: `content/taxonomy.json`, copied into the assets at
 * build time. Works offline and on first launch, before any network call has succeeded.
 */
@Singleton
class BundledTaxonomyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : TaxonomyRepository {

    // Parsed once per process. It is small, immutable, and read on every discovery screen.
    private val bundled: Taxonomy by lazy {
        context.assets.open(ASSET_PATH).bufferedReader().use { TaxonomyParser.parse(it.readText()) }
    }

    override val taxonomy: Flow<Taxonomy> = flow { emit(bundled) }.flowOn(dispatchers.io)

    private companion object {
        const val ASSET_PATH = "content/taxonomy.json"
    }
}
