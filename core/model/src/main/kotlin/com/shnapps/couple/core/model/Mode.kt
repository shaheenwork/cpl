package com.shnapps.couple.core.model

/**
 * The two halves of the product. Apart is not a reskin of Together: it has its own
 * information architecture, interaction primitives and pacing (BUILD_PROMPT.md §2.6).
 */
enum class Mode(val label: String, val describesPlay: String) {
    TOGETHER("Together", "One phone, passed between you."),
    APART("Apart", "A phone each. Both of you see every round."),
}
