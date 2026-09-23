package com.shnapps.couple.core.datastore

import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.Mode
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

    suspend fun setGamePlayers(players: GamePlayers)

    /** TOGETHER or APART: the home this phone opens on (§14.2). */
    suspend fun setMode(mode: Mode)

    /** Called on sign-out and account deletion. Leaves no trace of the previous user. */
    suspend fun clear()
}
