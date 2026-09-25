package com.shnapps.couple.core.ui

/**
 * Where the privacy policy and the terms are published.
 *
 * Blank until a human publishes them (HUMAN_SETUP.md §4.1) — the drafts are in `legal/`. A screen
 * that offers one of these **must** hide the row while the URL is blank: a dead link in Settings,
 * on an app that asks people to trust it with this, is worse than no link at all.
 *
 * Two constants rather than string resources because everything user-facing in this app is written
 * in Kotlin beside the screen that says it, and these are not translated text — they are addresses.
 */
object LegalUrls {
    const val PRIVACY_POLICY = ""
    const val TERMS = ""

    /** Whether there is anything to link to yet. */
    val published: Boolean get() = PRIVACY_POLICY.isNotBlank() || TERMS.isNotBlank()
}
