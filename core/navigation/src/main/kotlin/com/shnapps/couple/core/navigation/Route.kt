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
}
