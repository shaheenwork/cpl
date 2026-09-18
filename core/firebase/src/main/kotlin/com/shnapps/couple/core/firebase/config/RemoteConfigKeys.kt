package com.shnapps.couple.core.firebase.config

/**
 * Remote Config surface (BUILD_PROMPT.md §53) plus the experience-engine weights from
 * §10.3, so ranking can be tuned without shipping a release.
 *
 * Defaults live in `res/xml/remote_config_defaults.xml` and must stay in sync with these
 * keys; the app has to behave correctly on first launch, before any fetch completes.
 */
object RemoteConfigKeys {
    const val MINIMUM_APP_VERSION = "minimum_app_version"
    const val MAINTENANCE_MODE = "maintenance_mode"

    /**
     * The content bundle clients should be on (§9.2). Moved only by tools/publish-content.
     * The taxonomy travels inside the bundle, so it has no pointer of its own.
     */
    const val CONTENT_VERSION = "content_version"

    // Feature flags
    const val FEATURE_APART_MODE = "feature_apart_mode"
    const val FEATURE_MULTI_DAY_ARCS = "feature_multi_day_arcs"
    const val FEATURE_VOICE = "feature_voice"
    const val FEATURE_PHOTO_CHALLENGES = "feature_photo_challenges"
    const val FEATURE_SUBSCRIPTION = "feature_subscription"

    // Reveal timing (§5.3) is not here on purpose: the server alone decides when a match is
    // released, from its own Remote Config template (DECISIONS.md D-045).

    // Engine ranking weights (§10.3).
    const val WEIGHT_MUTUAL_PREFERENCE = "weight_mutual_preference"
    const val WEIGHT_AFFINITY = "weight_affinity"
    const val WEIGHT_MOOD_MATCH = "weight_mood_match"
    const val WEIGHT_NOVELTY = "weight_novelty"
    const val WEIGHT_INTENSITY_FIT = "weight_intensity_fit"
    const val WEIGHT_RECENCY_PENALTY = "weight_recency_penalty"
    const val WEIGHT_REPETITION_PENALTY = "weight_repetition_penalty"
}
