package com.shnapps.couple.core.analytics

/**
 * The complete analytics allowlist (BUILD_PROMPT.md §17.2).
 *
 * This is a closed hierarchy on purpose. There is deliberately no `log(name, bundle)`
 * escape hatch anywhere in the app: if an event is not modelled here, it cannot be
 * logged. Parameters are limited to enums, counts, durations and buckets.
 *
 * NEVER add a parameter that carries a private preference value, a private answer, a
 * boundary setting, user-authored text, or any media path or content body (§17.3).
 */
sealed interface AnalyticsEvent {
    val name: String
    val params: Map<String, Any> get() = emptyMap()

    data object AppOpen : AnalyticsEvent {
        override val name = "app_open"
    }

    data object OnboardingComplete : AnalyticsEvent {
        override val name = "onboarding_complete"
    }

    data object CoupleCreated : AnalyticsEvent {
        override val name = "couple_created"
    }

    data object PartnerJoined : AnalyticsEvent {
        override val name = "partner_joined"
    }

    data object PreferencesStarted : AnalyticsEvent {
        override val name = "preferences_started"
    }

    data class PreferencesCompleted(val answeredCount: Int) : AnalyticsEvent {
        override val name = "preferences_completed"
        override val params = mapOf("answered_count" to answeredCount)
    }

    /** Count only. The matched items themselves must never be sent. */
    data class MutualInterestFound(val count: Int) : AnalyticsEvent {
        override val name = "mutual_interest_found"
        override val params = mapOf("count" to count)
    }

    data class ExperienceStarted(val mode: String, val durationMin: Int, val intensity: Int) : AnalyticsEvent {
        override val name = "experience_started"
        override val params = mapOf("mode" to mode, "duration_min" to durationMin, "intensity" to intensity)
    }

    data class ExperienceCompleted(val mode: String, val durationMin: Int, val chapterCount: Int) : AnalyticsEvent {
        override val name = "experience_completed"
        override val params = mapOf("mode" to mode, "duration_min" to durationMin, "chapter_count" to chapterCount)
    }

    data class ExperienceSkipped(val chapterIndex: Int) : AnalyticsEvent {
        override val name = "experience_skipped"
        override val params = mapOf("chapter_index" to chapterIndex)
    }

    data class GameStarted(val gameId: String) : AnalyticsEvent {
        override val name = "game_started"
        override val params = mapOf("game_id" to gameId)
    }

    data class GameCompleted(val gameId: String) : AnalyticsEvent {
        override val name = "game_completed"
        override val params = mapOf("game_id" to gameId)
    }

    data object SurpriseCreated : AnalyticsEvent {
        override val name = "surprise_created"
    }

    data object SurpriseOpened : AnalyticsEvent {
        override val name = "surprise_opened"
    }

    data object ApartSessionStarted : AnalyticsEvent {
        override val name = "apart_session_started"
    }

    data object ApartSessionCompleted : AnalyticsEvent {
        override val name = "apart_session_completed"
    }

    data object MemoryCreated : AnalyticsEvent {
        override val name = "memory_created"
    }

    data object PaywallViewed : AnalyticsEvent {
        override val name = "paywall_viewed"
    }

    data object SubscriptionStarted : AnalyticsEvent {
        override val name = "subscription_started"
    }
}
