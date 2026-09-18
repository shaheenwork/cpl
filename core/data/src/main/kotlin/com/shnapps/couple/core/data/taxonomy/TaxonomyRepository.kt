package com.shnapps.couple.core.data.taxonomy

import com.shnapps.couple.core.data.content.ContentRepository
import com.shnapps.couple.core.model.Taxonomy
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The taxonomy of private curiosities (BUILD_PROMPT.md §9.5).
 *
 * A flow rather than a value because the taxonomy is remotely updatable: it travels inside
 * the content bundle, so a newer bundle brings a newer taxonomy without an app release.
 */
interface TaxonomyRepository {
    val taxonomy: Flow<Taxonomy>
}

/**
 * The taxonomy of the installed content bundle: the one shipped in the app until a newer
 * bundle has been downloaded. Works offline and on first launch.
 */
@Singleton
class ContentTaxonomyRepository @Inject constructor(
    contentRepository: ContentRepository,
) : TaxonomyRepository {
    override val taxonomy: Flow<Taxonomy> = contentRepository.taxonomy
}
