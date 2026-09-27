package com.shnapps.couple.core.model

/**
 * Why someone was asked to make an account (REDESIGN_PROMPT.md §6).
 *
 * Nothing on the way in asks for one. A row on home does, at the moment somebody reaches for the
 * half of the app that needs a partner or a place to keep things — and the screen that asks says
 * which of those it was, because "create an account" with no reason is a wall.
 *
 * The words live with the screen. This is only which of them to say.
 */
enum class AccountOffer {
    /** Sending, leaving something for later, counting down to a date. */
    APART,

    /** Keeping an evening, and finding it again on a new phone. */
    MEMORIES,

    /** An evening the server builds: a surprise, a mystery, fifteen minutes, a plan. */
    EVENING,
}
