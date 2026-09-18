package com.shnapps.couple.core.model

/**
 * One partner's private answer to one taxonomy item — the document at
 * `users/{uid}/preferences/{itemId}` (BUILD_PROMPT.md §6.1).
 *
 * Owner-only for life (§5.1). Nothing about it is ever shown to the partner: a match is
 * computed on the server, and even then only the fact that both were positive is
 * materialised — never either answer (§5.2).
 *
 * [secret] is "secretly curious" (§14.7): a curiosity the user wants to stay hidden unless
 * their partner turns out to share it. It only ever accompanies [PreferenceValue.CURIOUS];
 * the security rules reject any other combination, so the pair can never disagree.
 */
data class PreferenceAnswer(
    val value: PreferenceValue,
    val secret: Boolean = false,
) {
    init {
        require(!secret || value == PreferenceValue.CURIOUS) { "Only CURIOUS can be secret, not $value" }
    }

    companion object {
        val SECRETLY_CURIOUS = PreferenceAnswer(PreferenceValue.CURIOUS, secret = true)

        /** Every answer a user can give, in the order they are offered. */
        val OPTIONS: List<PreferenceAnswer> = listOf(
            PreferenceAnswer(PreferenceValue.YES),
            PreferenceAnswer(PreferenceValue.CURIOUS),
            SECRETLY_CURIOUS,
            PreferenceAnswer(PreferenceValue.MAYBE),
            PreferenceAnswer(PreferenceValue.NOT_FOR_ME),
            PreferenceAnswer(PreferenceValue.NEVER),
        )
    }
}
