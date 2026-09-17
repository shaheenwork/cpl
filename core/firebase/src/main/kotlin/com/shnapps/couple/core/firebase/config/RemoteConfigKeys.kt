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
    const val CONTENT_VERSION = "content_version"
    const val TAXONOMY_VERSION = "taxonomy_version"

    // Feature flags
    const val FEATURE_APART_MODE = "feature_apart_mode"
    const val FEATURE_MULTI_DAY_ARCS = "feature_multi_day_arcs"
    const val FEATURE_VOICE = "feature_voice"
    const val FEATURE_PHOTO_CHALLENGES = "feature_photo_challenges"
    const val FEATURE_SUBSCRIPTION = "feature_subscription"

    // Privacy timing (§5.3): mutual matches are released in jittered batches so that
    // reveal timing cannot be used to infer what a partner just answered.
    const val REVEAL_JITTER_MIN_MINUTES = "reveal_jitter_min_minutes"
    const val REVEAL_JITTER_MAX_MINUTES = "reveal_jitter_max_minutes"

    // Engine ranking weights (§10.3).
    const val WEIGHT_MUTUAL_PREFERENCE = "weight_mutual_preference"
    const val WEIGHT_AFFINITY = "weight_affinity"
    const val WEIGHT_MOOD_MATCH = "weight_mood_match"
    const val WEIGHT_NOVELTY = "weight_novelty"
    const val WEIGHT_INTENSITY_FIT = "weight_intensity_fit"
    const val WEIGHT_RECENCY_PENALTY = "weight_recency_penalty"
    const val WEIGHT_REPETITION_PENALTY = "weight_repetition_penalty"
}
