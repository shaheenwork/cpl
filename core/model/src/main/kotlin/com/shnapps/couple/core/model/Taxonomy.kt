package com.shnapps.couple.core.model

/**
 * The map of curiosities each partner answers privately (BUILD_PROMPT.md §9.5).
 *
 * Three levels: category → theme → item. Users answer **items**. A **theme** is also the
 * unit of boundaries (Phase 6): every item maps to exactly one boundary theme — the one it
 * is filed under — so a boundary on "Blindfolds" covers everything in that theme and no
 * item can slip out from under its boundary by being filed elsewhere.
 *
 * Versioned, because answers are stored with the version they were given against: if an
 * item's meaning is ever reworded materially, the old answers can be recognised as stale
 * rather than silently reinterpreted.
 */
data class Taxonomy(
    val version: Int,
    val categories: List<TaxonomyCategory>,
) {
    /** Every item, in authored order: category by category, theme by theme. */
    val items: List<PreferenceItem> = categories.flatMap { category -> category.themes.flatMap { it.items } }

    private val itemsById: Map<String, PreferenceItem> = items.associateBy { it.id }
    private val categoriesById: Map<String, TaxonomyCategory> = categories.associateBy { it.id }

    fun item(id: String): PreferenceItem? = itemsById[id]

    fun category(id: String): TaxonomyCategory? = categoriesById[id]
}

data class TaxonomyCategory(
    val id: String,
    val title: String,
    /** One line of framing shown above the category's cards. */
    val subtitle: String,
    val themes: List<TaxonomyTheme>,
)

/** A group of related items, and the boundary that covers all of them. */
data class TaxonomyTheme(
    val id: String,
    val title: String,
    val items: List<PreferenceItem>,
)

/**
 * One thing a partner can be curious about.
 *
 * Deliberately broad and non-graphic: the taxonomy is a map of curiosities, not a
 * catalogue of acts (§9.5). The specifics live in content, which is filtered by these
 * answers and by boundaries.
 */
data class PreferenceItem(
    val id: String,
    val prompt: String,
    val description: String,
    /**
     * The lowest content level at which this item is asked about at all. A user whose own
     * content level is below it never sees the card.
     */
    val intensityFloor: Intensity,
    /** Which halves of the product this can shape. Never empty. */
    val modes: Set<Mode>,
    val categoryId: String,
    /** The boundary theme this item maps to (§9.5). Always the theme it is filed under. */
    val themeId: String,
) {
    /** Whether a user at [contentLevel] is asked about this item. */
    fun isAvailableAt(contentLevel: Intensity): Boolean = intensityFloor.level <= contentLevel.level
}
