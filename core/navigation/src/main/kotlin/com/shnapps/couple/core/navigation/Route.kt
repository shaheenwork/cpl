package com.shnapps.couple.core.navigation

import kotlinx.serialization.Serializable

/**
 * Every destination in the app, as type-safe Navigation Compose routes.
 *
 * These live in :core:navigation rather than in the features so that no feature module
 * ever has to depend on another one (BUILD_PROMPT.md §4.2). The :architecture module
 * fails the build if that rule is broken.
 *
 * Screens are added by the phase that builds them; the first-run chain below is the
 * MVP screen map from §14.1.
 */
object Route {

    // --- First run (§14.1) ---

    @Serializable
    data object Splash

    @Serializable
    data object AgeGate

    /**
     * The whole of first launch (REDESIGN_PROMPT.md §5.1): 18+ and how far tonight goes, on one
     * screen, with no account behind it. [AgeGate] stays for the signed-in path, where the
     * attestation is written to the person's own record.
     */
    @Serializable
    data object Start

    /** Playing with nothing set up: the card, and the two taps that move it on. */
    @Serializable
    data object InstantPlay

    /** A guided evening: warm-up, build, peak, aftercare (M5, M6). */
    @Serializable
    data object GameNight

    /**
     * The only setup in the app, and only for playing apart (V2 rule 2).
     *
     * There is nothing before it and nothing after it: no account to make, no profile to fill in.
     * A pair, once made, lasts until one of the two phones unpairs.
     */
    @Serializable
    data object Pairing

    // --- Main ---

    @Serializable
    data object Home

    /** The user's private boundaries and content level (§3.2, §14.9). */
    @Serializable
    data object Boundaries

    /** Everything about this account and this couple, in one place (§14.9). */
    @Serializable
    data object Settings

    /** The end of an account, and of the couple with it (§14.9, D-093). */
    @Serializable
    data object DeleteAccount

    /** What this phone may say out loud (§16.2). */
    @Serializable
    data object NotificationSettings

    /**
     * The packs, and what they cost (V2 rule 5).
     *
     * [level] is the level they tapped to get here, by `Intensity` name, so the store can put that
     * pack in front. Never anything about what they were playing.
     */
    @Serializable
    data class Store(val level: String? = null)

    /**
     * A game dealt on this phone: no session, no server, nobody waiting. What a phone with no
     * account plays, and the one player screen draws it the same way it draws a session's.
     */
    @Serializable
    data class DealtGame(val gameId: String)

    /**
     * A game being played — or waiting to be — on either partner's phone (§11). [twoPhones] is
     * how the couple chose to play it: a phone each, rather than one passed between them (§12).
     */
    @Serializable
    data class Game(val experienceId: String, val twoPhones: Boolean = false)

    /**
     * The app lock (§3.4). Not part of the first-run chain — it can appear over anything,
     * including a cold start, because the app always starts locked.
     */
    @Serializable
    data object AppLock

    /** Turning the app lock on. Reached from Settings once Phase 20 builds it. */
    @Serializable
    data object AppLockSetup

    // --- Development ---

    /**
     * The design-system gallery (BUILD_PROMPT.md §15.3). Reachable from debug builds only;
     * it is a review surface for the component inventory, not a product screen.
     */
    @Serializable
    data object DesignGallery

    /**
     * Every item in the content cache (§9). Debug builds only: in a product build content is
     * only ever shown through the engine, behind the couple's boundaries (§3.2).
     */
    @Serializable
    data object ContentInspector
}
