package com.shnapps.couple.core.analytics

/**
 * The only way to record analytics. The Firebase-backed implementation arrives in
 * Phase 1 and lives in :core:firebase; direct use of FirebaseAnalytics anywhere else is
 * a build failure (BUILD_PROMPT.md §17.1, enforced by :architecture).
 */
fun interface AnalyticsLogger {
    fun log(event: AnalyticsEvent)
}

/** Used in tests, in previews, and whenever the user has opted out. */
object NoOpAnalyticsLogger : AnalyticsLogger {
    override fun log(event: AnalyticsEvent) = Unit
}
