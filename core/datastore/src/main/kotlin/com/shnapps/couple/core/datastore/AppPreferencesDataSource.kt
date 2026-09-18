package com.shnapps.couple.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shnapps.couple.core.model.AppLockMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "afterhours_prefs")

@Singleton
class AppPreferencesDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppPreferencesStore {
    override val preferences: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            lockMode = prefs[Keys.LOCK_MODE]
                ?.let { runCatching { AppLockMode.valueOf(it) }.getOrNull() }
                ?: AppLockMode.OFF,
            pinHash = prefs[Keys.PIN_HASH],
            pinSalt = prefs[Keys.PIN_SALT],
            lockTimeoutMillis = prefs[Keys.LOCK_TIMEOUT]
                ?: AppPreferences.DEFAULT_LOCK_TIMEOUT_MILLIS,
            ageConfirmedHint = prefs[Keys.AGE_CONFIRMED_HINT] ?: false,
            onboardingCompleteHint = prefs[Keys.ONBOARDING_COMPLETE_HINT] ?: false,
        )
    }

    override suspend fun setLockOff() {
        context.dataStore.edit { prefs ->
            prefs[Keys.LOCK_MODE] = AppLockMode.OFF.name
            prefs.remove(Keys.PIN_HASH)
            prefs.remove(Keys.PIN_SALT)
        }
    }

    override suspend fun setBiometricLock() {
        context.dataStore.edit { prefs ->
            prefs[Keys.LOCK_MODE] = AppLockMode.BIOMETRIC.name
            // A biometric lock still keeps its PIN, because biometrics can fail or be
            // temporarily locked out and the user must not be shut out of their own app.
        }
    }

    override suspend fun setPinLock(hash: String, salt: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LOCK_MODE] = AppLockMode.PIN.name
            prefs[Keys.PIN_HASH] = hash
            prefs[Keys.PIN_SALT] = salt
        }
    }

    override suspend fun setLockTimeout(millis: Long) {
        context.dataStore.edit { it[Keys.LOCK_TIMEOUT] = millis }
    }

    override suspend fun setAgeConfirmedHint(confirmed: Boolean) {
        context.dataStore.edit { it[Keys.AGE_CONFIRMED_HINT] = confirmed }
    }

    override suspend fun setOnboardingCompleteHint(complete: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE_HINT] = complete }
    }

    override suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private object Keys {
        val LOCK_MODE = stringPreferencesKey("lock_mode")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val LOCK_TIMEOUT = longPreferencesKey("lock_timeout_millis")
        val AGE_CONFIRMED_HINT = booleanPreferencesKey("age_confirmed_hint")
        val ONBOARDING_COMPLETE_HINT = booleanPreferencesKey("onboarding_complete_hint")
    }
}
