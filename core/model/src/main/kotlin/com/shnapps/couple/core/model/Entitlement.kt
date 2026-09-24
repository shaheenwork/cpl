package com.shnapps.couple.core.model

/** What a couple may do (BUILD_PROMPT.md §19). One subscription covers both partners. */
enum class Tier { FREE, PREMIUM }

/**
 * Where a subscription stands, in the store's own terms. A grace period still entitles — the
 * payment failed, the subscription did not — and a cancelled one runs to the end of its term.
 */
enum class SubscriptionState { ACTIVE, GRACE, ON_HOLD, PAUSED, CANCELED, EXPIRED, REVOKED }

/**
 * What the server says this couple is entitled to. Never decided on the phone: the app reads it,
 * shows it, and asks the server again when it needs to be sure (§19, §7.2).
 */
data class Entitlement(
    val tier: Tier = Tier.FREE,
    val state: SubscriptionState? = null,
    val expiresAtMillis: Long? = null,
) {
    val premium: Boolean get() = tier == Tier.PREMIUM

    /** A subscription that will not renew, but has not run out yet. */
    val endingSoon: Boolean get() = premium && (state == SubscriptionState.CANCELED || state == SubscriptionState.GRACE)
}

/**
 * The limits a free couple runs into (§19), and what each one is about. The server refuses with
 * the same names, so a phone shows the paywall for a reason rather than for a shrug.
 */
enum class Gate(val headline: String, val detail: String) {
    NIGHTS_THIS_WEEK(
        "That's this week's nights.",
        "Free includes three built nights a week. Premium is as many as you want — and everything " +
            "you've already got stays yours.",
    ),
    MYSTERY_NIGHT(
        "Mystery Nights are premium.",
        "You find out as you go: not how long, not how many, just the next thing.",
    ),
    MULTI_DAY_ARC(
        "A few days of this is premium.",
        "One, three, seven or fourteen days, a night each, picking up where the last left off.",
    ),
    SURPRISES_WAITING(
        "That's three things waiting.",
        "Free lets you keep three surprises waiting at once. Opening them is never limited.",
    ),
    ;

    companion object {
        /**
         * The server refuses with `premium-required:GATE`; a route carries the bare name. Both
         * read the same way here, and anything else is not about money at all.
         */
        fun of(code: String?): Gate? {
            if (code == null) return null
            val name = code.substringAfter("premium-required:", code)
            return entries.firstOrNull { it.name == name }
        }
    }
}
