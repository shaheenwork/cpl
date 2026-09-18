package com.shnapps.couple.core.datastore

import com.shnapps.couple.core.model.AppLockMode

/**
 * Device-local settings. Deliberately small.
 *
 * Nothing private lives here. Preferences, boundaries and answers are owner-only documents
 * in Firestore (BUILD_PROMPT.md §5.1); this file holds only what has to survive a cold
 * start before any network call completes.
 */
data class AppPreferences(
    val lockMode: AppLockMode = AppLockMode.OFF,
    /** Salted PBKDF2 hash of the PIN. Never the PIN itself. */
    val pinHash: String? = null,
    val pinSalt: String? = null,
    /** How long the app may sit in the background before it re-locks. */
    val lockTimeoutMillis: Long = DEFAULT_LOCK_TIMEOUT_MILLIS,

    /**
     * Routing hint only, so the splash screen can pick a destination without waiting on
     * Firestore. The server record at `users/{uid}.ageAttestation` remains the truth, and
     * a missing or false record there re-prompts regardless of what this says (§3.1).
     */
    val ageConfirmedHint: Boolean = false,
    val onboardingCompleteHint: Boolean = false,
) {
    companion object {
        const val DEFAULT_LOCK_TIMEOUT_MILLIS: Long = 60_000L
    }
}
