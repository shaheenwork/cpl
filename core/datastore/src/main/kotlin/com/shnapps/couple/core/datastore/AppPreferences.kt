package com.shnapps.couple.core.datastore

import com.shnapps.couple.core.model.AppLockMode
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.Heat
import com.shnapps.couple.core.model.Mode

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

    /**
     * The names the partners gave themselves for a game passed between them on this phone
     * (§11). Only what they typed; kept here so the next game remembers them.
     */
    val gamePlayers: GamePlayers? = null,

    /**
     * Which home the couple last had open (§14.2): the same room, or apart. Device-local,
     * because it is about where this phone is, not about the couple.
     */
    val mode: Mode = Mode.TOGETHER,

    /**
     * Whether a notification may name which kind of thing happened (§16.2). The answer lives in
     * the user's profile, because the server reads it too; this is the copy the phone has when a
     * message arrives before anything has loaded. Hidden until told otherwise.
     */
    val showNotificationContent: Boolean = false,

    /**
     * How far tonight goes, as this phone last set it (REDESIGN_PROMPT.md §2).
     *
     * Device-local on purpose: before a couple has signed in there is nowhere else to keep it, and
     * afterwards the couple's real ceiling is still the server's — this only ever narrows what that
     * allows, never widens it.
     */
    val heat: Heat = Heat.DEFAULT,

    /**
     * Cards this phone has been told never to show again, and the kinds of thing they belong to
     * (REDESIGN_PROMPT.md §5.3).
     *
     * Captured by one tap during play rather than by a wizard beforehand. They are kept here while
     * there is no account, and written to the person's real limits the moment there is one — until
     * then they are the only record, so a refusal is honoured from the next card onwards.
     */
    val refusedCardIds: Set<String> = emptySet(),
    val refusedThemes: Set<String> = emptySet(),

    /**
     * Cards this phone has already played, so the deck does not deal them again (D-124).
     *
     * Not a refusal and not a judgement: a card they did and a card they skipped both land here,
     * because both have been seen. Cleared the moment it covers the whole deck, which is how an
     * evening gets a second pass at content rather than an empty screen.
     */
    val playedCardIds: Set<String> = emptySet(),

    /**
     * The pack this phone is dealing from, or null for all of them (D-125).
     *
     * Kept rather than asked each time: picking "After dark" is a statement about the couple, and
     * making them say it again every evening is the setup form the rebuild removed.
     */
    val lane: String? = null,
) {
    companion object {
        const val DEFAULT_LOCK_TIMEOUT_MILLIS: Long = 60_000L
    }
}
