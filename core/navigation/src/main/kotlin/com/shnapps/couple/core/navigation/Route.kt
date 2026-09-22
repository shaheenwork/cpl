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

    @Serializable
    data object Auth

    @Serializable
    data object ForgotPassword

    @Serializable
    data object Welcome

    @Serializable
    data object CoupleSetup

    @Serializable
    data object PreferenceDiscovery

    @Serializable
    data object MutualDiscovery

    // --- Main ---

    @Serializable
    data object Home

    /** The user's private boundaries and content level (§3.2, §14.9). */
    @Serializable
    data object Boundaries

    /**
     * Build Our Night (§14.5). [focus] is a revealed mutual match to build it around, from
     * "Build a night around this" (§14.7), or null.
     */
    @Serializable
    data class BuildNight(val focus: String? = null)

    /** A night being played — or waiting to be — on either partner's phone (§14.6). */
    @Serializable
    data class Night(val experienceId: String)

    /** The games, and setting one up (§11). */
    @Serializable
    data object Games

    /** A game being played — or waiting to be — on either partner's phone (§11). */
    @Serializable
    data class Game(val experienceId: String)

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
