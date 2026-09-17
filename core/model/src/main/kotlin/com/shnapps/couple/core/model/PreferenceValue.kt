package com.shnapps.couple.core.model

/**
 * A single partner's private answer to one taxonomy item (BUILD_PROMPT.md §9.5).
 *
 * These values are owner-only for life. They are never written to a couple-scoped
 * document, never sent to Analytics and never rendered with partner attribution (§5.1).
 */
enum class PreferenceValue {
    YES,
    CURIOUS,
    MAYBE,
    NOT_FOR_ME,
    NEVER,
    ;

    /** Only positives can ever produce a mutual match; a non-match materialises nothing (§5.2). */
    val isPositive: Boolean get() = this == YES || this == CURIOUS || this == MAYBE
}
