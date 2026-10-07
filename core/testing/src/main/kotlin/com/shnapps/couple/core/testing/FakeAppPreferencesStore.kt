package com.shnapps.couple.core.testing

import com.shnapps.couple.core.datastore.AppPreferences
import com.shnapps.couple.core.datastore.AppPreferencesStore
import com.shnapps.couple.core.datastore.PlaySettingsStore
import com.shnapps.couple.core.datastore.PurchaseStore
import com.shnapps.couple.core.model.AppLockMode
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.LimitAnswer
import com.shnapps.couple.core.model.LimitTag
import com.shnapps.couple.core.model.Mode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [AppPreferencesStore] that really keeps what is written to it. */
class FakeAppPreferencesStore(
    initial: AppPreferences = AppPreferences(),
) : AppPreferencesStore, PlaySettingsStore, PurchaseStore {

    val state = MutableStateFlow(initial)

    override val preferences = state

    override suspend fun setMode(mode: Mode) = state.update { it.copy(mode = mode) }

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

    override suspend fun setGamePlayers(players: GamePlayers) = state.update { it.copy(gamePlayers = players) }

    override suspend fun setShowNotificationContent(show: Boolean) =
        state.update { it.copy(showNotificationContent = show) }

    override suspend fun setTier(tier: Intensity) {
        state.value = state.value.copy(tier = tier)
    }

    override suspend fun refuse(cardId: String, themes: Set<String>) {
        state.value = state.value.copy(
            refusedCardIds = state.value.refusedCardIds + cardId,
            refusedThemes = state.value.refusedThemes + themes,
        )
    }

    override suspend fun markPlayed(cardId: String, deckSize: Int) {
        val played = state.value.playedCardIds + cardId
        // The real store's rule: seen all of it, and the deck comes round again (D-124).
        state.value = state.value.copy(
            playedCardIds = if (deckSize in 1..played.size) setOf(cardId) else played,
        )
    }

    override suspend fun unlock(levels: Set<Intensity>) {
        state.value = state.value.copy(ownedLevels = state.value.ownedLevels + levels)
    }

    override suspend fun setLimit(tag: LimitTag, answer: LimitAnswer?) {
        state.value = state.value.copy(
            limits = if (answer == null) state.value.limits - tag else state.value.limits + (tag to answer),
        )
    }

    override suspend fun setLane(pack: String?) {
        state.value = state.value.copy(lane = pack)
    }

    override suspend fun clear() {
        state.value = AppPreferences()
    }
}
