package com.shnapps.couple.core.firebase.config

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.shnapps.couple.core.firebase.FirebaseEnvironment
import com.shnapps.couple.core.firebase.R
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Reads Remote Config (BUILD_PROMPT.md §53) so that features can be switched off, the
 * content pointer moved and the engine weights retuned without shipping a release.
 *
 * Every key falls back to `res/xml/remote_config_defaults.xml`, so the app behaves
 * correctly on first launch before any fetch has completed.
 */
@Singleton
class RemoteConfigSource @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
    environment: FirebaseEnvironment,
) {
    private val minimumFetchInterval =
        if (environment.useEmulator) DEV_FETCH_INTERVAL else PROD_FETCH_INTERVAL

    /**
     * Applies defaults, then refreshes in the background. Callers do not wait on the
     * network: defaults are already in place, so a slow or failed fetch degrades to
     * "last known good" rather than to a blocked launch.
     */
    suspend fun initialize() {
        remoteConfig.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(minimumFetchInterval)
                .build(),
        ).await()
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults).await()
        runCatching { remoteConfig.fetchAndActivate().await() }
    }

    fun boolean(key: String): Boolean = remoteConfig.getBoolean(key)

    fun long(key: String): Long = remoteConfig.getLong(key)

    fun double(key: String): Double = remoteConfig.getDouble(key)

    fun string(key: String): String = remoteConfig.getString(key)

    /**
     * Engine ranking weights (§10.3), read as a map so the engine takes them as plain
     * data and stays a pure function of its inputs.
     */
    fun engineWeights(): Map<String, Double> = listOf(
        RemoteConfigKeys.WEIGHT_MUTUAL_PREFERENCE,
        RemoteConfigKeys.WEIGHT_AFFINITY,
        RemoteConfigKeys.WEIGHT_MOOD_MATCH,
        RemoteConfigKeys.WEIGHT_NOVELTY,
        RemoteConfigKeys.WEIGHT_INTENSITY_FIT,
        RemoteConfigKeys.WEIGHT_RECENCY_PENALTY,
        RemoteConfigKeys.WEIGHT_REPETITION_PENALTY,
    ).associateWith { double(it) }

    private companion object {
        /** Against the emulator, config should update as fast as it is edited. */
        val DEV_FETCH_INTERVAL = 0.minutes.inWholeSeconds

        val PROD_FETCH_INTERVAL = 12.hours.inWholeSeconds
    }
}
