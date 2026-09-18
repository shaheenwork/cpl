package com.shnapps.couple.core.model

/**
 * One authored piece of content (BUILD_PROMPT.md §9.1): a prompt, challenge, choice or
 * scene the experience engine can place in a night.
 *
 * Items are public copy. What is private is which ones a couple is shown, which is decided
 * by the engine from mutual preferences and boundaries (§3.2, §10) — so nothing outside the
 * engine's chokepoint may put an item in front of a user.
 */
data class ContentItem(
    val id: String,
    /** Raised whenever the copy or the metadata changes, so edits can supersede deltas. */
    val version: Int,
    val pack: String,
    /** A taxonomy category id: the affinity dimension this item scores against (§10.3). */
    val category: String,
    val title: String,
    val subtitle: String,
    val body: String,
    val tags: Set<String>,
    val intensity: Intensity,
    /** Never empty: an item no mode can use is dropped when the bundle is read. */
    val modes: Set<Mode>,
    val interactionType: InteractionType,
    val durationMin: Int,
    val moods: Set<Mood>,
    /** Taxonomy item ids both partners must be positive about for this item to appear. */
    val requiredMutualPreferences: Set<String>,
    /** Taxonomy item ids that make this item more likely when both partners like them. */
    val boostedByPreferences: Set<String>,
    /** Boundary theme ids: a NEVER from either partner on any of them removes the item. */
    val excludedByBoundaries: Set<String>,
    val chapterKinds: Set<ChapterKind>,
    /** 0..1: how much the item leans on surprise when novelty is asked for (§10.3). */
    val noveltyWeight: Double,
    val repeatCooldownDays: Int,
    val requiresMedia: MediaKind?,
    val status: ContentStatus,
)

/** How an item is played. A night mixes these, never running one type thrice (§10.5). */
enum class InteractionType {
    CONVERSATION,
    CONFESSION,
    CHALLENGE,
    CHOICE,
    PREDICTION,
    REVEAL,
    SURPRISE,
    CONTROL,
    MEMORY,
    ROLEPLAY,
    SENSORY,
    MESSAGE,
}

/** The chapters a night is assembled from (§10.4). */
enum class ChapterKind {
    WARM_UP,
    CURIOSITY,
    TEASE,
    CHALLENGE,
    CONTROL,
    CONFESSION,
    ROLEPLAY,
    WILDCARD,
    YOUR_CHOICE,
    FINALE,
}

/** Media an item cannot be played without. */
enum class MediaKind {
    VOICE,
    PHOTO,
}

/** Content lifecycle (§9.6). Only [PUBLISHED] items ever reach the engine. */
enum class ContentStatus(val id: String) {
    DRAFT("draft"),
    PUBLISHED("published"),
    DISABLED("disabled"),
    ARCHIVED("archived"),
    ;

    companion object {
        fun fromId(id: String): ContentStatus? = entries.firstOrNull { it.id == id }
    }
}

/** A themed collection of items (§9.3). */
data class ContentPack(
    val id: String,
    val title: String,
    val description: String,
    val intensityRange: IntRange,
)

/** Which content the device holds, and where it came from. */
data class ContentState(
    /** 0 until the shipped bundle has been loaded for the first time. */
    val contentVersion: Int,
    val itemCount: Int,
    /** Admin overrides in force on top of the bundle (§9.2). */
    val deltaCount: Int,
    /** When content was last checked against the server; null if never. */
    val lastSyncMillis: Long?,
)
