package com.shnapps.couple.core.datastore

import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.Heat
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

    /** What a notification may say on this phone (§16.2), cached for when one arrives. */
    suspend fun setShowNotificationContent(show: Boolean)

    /** Called on sign-out and account deletion. Leaves no trace of the previous user. */
    suspend fun clear()
}

/**
 * What playing writes down on this phone (REDESIGN_PROMPT.md §5.3).
 *
 * Separate from [AppPreferencesStore] because it is a different kind of thing: the lock, the age
 * hint and the notification setting describe the app, while these two describe an evening. The same
 * data source implements both — the split is for the reader, and for the seam this will be replaced
 * at when a refusal starts going to the person's real limits instead (R5).
 */
interface PlaySettingsStore {
    /** How far tonight goes. Changed with one thumb, mid-card. */
    suspend fun setHeat(heat: Heat)

    /** Never show this card again, and nothing else of its kind either. Nobody is told. */
    suspend fun refuse(cardId: String, themes: Set<String>)
}
