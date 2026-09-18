package com.shnapps.couple.core.model

/**
 * One partner's private boundary on one taxonomy theme — the document at
 * `users/{uid}/boundaries/{themeId}` (BUILD_PROMPT.md §6.1).
 *
 * Owner-only for life (§5.1). The server reads the [level] to build the couple's filters,
 * where either partner's NEVER or NOT TONIGHT removes the theme for both; neither the
 * partner nor any client ever sees the combined result (§5.4).
 *
 * A theme with no document has no boundary, and nothing about it is restricted.
 */
data class Boundary(
    val level: BoundaryLevel,
    /** A note to self. Read by no one else: not the partner, not the server's filters. */
    val note: String? = null,
) {
    init {
        require(note == null || note.length <= MAX_NOTE_LENGTH) { "A note is at most $MAX_NOTE_LENGTH characters" }
    }

    companion object {
        /** Mirrors the security rules' limit on `note`. */
        const val MAX_NOTE_LENGTH = 200
    }
}
