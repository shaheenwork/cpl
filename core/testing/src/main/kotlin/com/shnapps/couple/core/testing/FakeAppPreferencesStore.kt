package com.shnapps.couple.core.testing

import com.shnapps.couple.core.datastore.AppPreferences
import com.shnapps.couple.core.datastore.AppPreferencesStore
import com.shnapps.couple.core.model.AppLockMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [AppPreferencesStore] that really keeps what is written to it. */
class FakeAppPreferencesStore(initial: AppPreferences = AppPreferences()) : AppPreferencesStore {

    val state = MutableStateFlow(initial)

    override val preferences = state

    override suspend fun setLockOff() =
        state.update { it.copy(lockMode = AppLockMode.OFF, pinHash = null, pinSalt = null) }

    override suspend fun setBiometricLock() = state.update { it.copy(lockMode = AppLockMode.BIOMETRIC) }

    override suspend fun setPinLock(hash: String, salt: String) =
        state.update { it.copy(lockMode = AppLockMode.PIN, pinHash = hash, pinSalt = salt) }

    override suspend fun setLockTimeout(millis: Long) = state.update { it.copy(lockTimeoutMillis = millis) }

    override suspend fun setAgeConfirmedHint(confirmed: Boolean) =
        state.update { it.copy(ageConfirmedHint = confirmed) }

    override suspend fun setOnboardingCompleteHint(complete: Boolean) =
        state.update { it.copy(onboardingCompleteHint = complete) }

    override suspend fun clear() {
        state.value = AppPreferences()
    }
}
