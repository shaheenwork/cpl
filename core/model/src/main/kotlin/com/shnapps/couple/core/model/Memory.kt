package com.shnapps.couple.core.model

/** What kind of evening it was, as the library shows it (§14.8). */
enum class MemoryKind { NIGHT, MYSTERY }

/**
 * A night they kept (BUILD_PROMPT.md §14.8).
 *
 * Their own words, and the shape of the evening — moods, how long, how many chapters. Never a
 * content id: a memory both may read must not become a way to read a night one of them planned
 * for the other (D-081, D-090).
 */
data class Memory(
    val id: String,
    val experienceId: String,
    val title: String,
    val note: String?,
    val favoriteMoment: String?,
    val rating: Int?,
    val mood: List<Mood>,
    val durationMin: Int,
    val kind: MemoryKind,
    val chapterCount: Int,
    val playedThrough: Int,
    /** Voice notes and photos they attached, by id. The bytes stay where the media rules are. */
    val mediaIds: List<String>,
    val favorite: Boolean,
    val dateMillis: Long,
) {
    /** They played it to the end. */
    val finished: Boolean get() = chapterCount > 0 && playedThrough >= chapterCount
}
