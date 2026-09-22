package com.shnapps.couple.core.engine

import com.shnapps.couple.core.model.ContentItem
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Mode

/** A night as the server returned it (BUILD_PROMPT.md §5.5): ids only. */
data class ServerNight(
    val request: NightRequest,
    val chapters: List<Chapter>,
    /** The rest of the sampled pool, best first. Never the whole permitted set. */
    val alternates: List<String>,
    val effectiveIntensity: Int,
    val engineVersion: Int,
)

/**
 * The only content a client may assemble or remix with: exactly what the server released for
 * one night — its chapters and its alternates (BUILD_PROMPT.md §3.2, §5.5).
 *
 * The constructor is internal, so app code cannot hand the engine a list of its own choosing;
 * the one way in is [fromServer]. It drops, as defence in depth, anything that could not have
 * been permitted — another mode, above the night's ceiling, not published — even though the
 * server's chokepoint should never have released it.
 */
class PermittedPool internal constructor(
    val items: List<ContentItem>,
    val effectiveIntensity: Int,
    val mode: Mode,
) {
    val ids: Set<String> = items.mapTo(LinkedHashSet()) { it.id }

    /**
     * This pool without [removed] — items the couple's limits no longer permit, mid-night
     * (DECISIONS.md D-062), or a theme just kept out. A pool can only ever shrink: nothing
     * outside what the server released can be added back.
     */
    fun without(removed: Set<String>): PermittedPool = if (removed.none { it in ids }) {
        this
    } else {
        PermittedPool(items.filterNot { it.id in removed }, effectiveIntensity, mode)
    }

    companion object {
        /** The pool for [night], resolving ids through [resolve]; unknown ids are left out. */
        fun fromServer(night: ServerNight, resolve: (String) -> ContentItem?): PermittedPool {
            val ids = (night.chapters.map { it.itemId } + night.alternates).distinct()
            val items = ids.mapNotNull(resolve).filter { item ->
                item.status == ContentStatus.PUBLISHED &&
                    night.request.mode in item.modes &&
                    item.intensity.level <= night.effectiveIntensity
            }
            return PermittedPool(items, night.effectiveIntensity, night.request.mode)
        }
    }
}
