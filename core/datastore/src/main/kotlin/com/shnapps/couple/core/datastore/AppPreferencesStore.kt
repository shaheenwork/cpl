package com.shnapps.couple.core.datastore

import kotlinx.coroutines.flow.Flow

/**
 * Device-local settings, as an interface so callers depend on behaviour rather than on
 * DataStore — and so tests can use a real in-memory fake that actually keeps what is
 * written. A relaxed mock silently drops writes, which made "biometric requires a PIN"
 * untestable against one.
 */
interface AppPreferencesStore {
    val preferences: Flow<AppPreferences>

    suspend fun setLockOff()

    suspend fun setBiometricLock()

    suspend fun setPinLock(hash: String, salt: String)

    suspend fun setLockTimeout(millis: Long)

    suspend fun setAgeConfirmedHint(confirmed: Boolean)

    suspend fun setOnboardingCompleteHint(complete: Boolean)

    /** Called on sign-out and account deletion. Leaves no trace of the previous user. */
    suspend fun clear()
}
