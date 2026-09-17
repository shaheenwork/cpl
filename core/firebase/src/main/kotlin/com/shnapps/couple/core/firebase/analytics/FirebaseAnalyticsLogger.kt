package com.shnapps.couple.core.firebase.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.shnapps.couple.core.analytics.AnalyticsEvent
import com.shnapps.couple.core.analytics.AnalyticsLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only bridge between the app and Firebase Analytics.
 *
 * It accepts [AnalyticsEvent] and nothing else. Because that type is a closed hierarchy
 * with enum/count/duration parameters only, there is no code path by which a private
 * preference value, a private answer, a boundary setting, user-authored text or a media
 * path can reach Analytics (BUILD_PROMPT.md §17.1, §17.3).
 *
 * :architecture fails the build if FirebaseAnalytics is imported anywhere else.
 */
@Singleton
class FirebaseAnalyticsLogger @Inject constructor(
    private val analytics: FirebaseAnalytics,
) : AnalyticsLogger {

    override fun log(event: AnalyticsEvent) {
        val bundle = Bundle().apply {
            event.params.forEach { (key, value) ->
                when (value) {
                    is Int -> putLong(key, value.toLong())
                    is Long -> putLong(key, value)
                    is Double -> putDouble(key, value)
                    is Boolean -> putLong(key, if (value) 1L else 0L)
                    is String -> putString(key, value)
                    else -> putString(key, value.toString())
                }
            }
        }
        analytics.logEvent(event.name, bundle)
    }
}
