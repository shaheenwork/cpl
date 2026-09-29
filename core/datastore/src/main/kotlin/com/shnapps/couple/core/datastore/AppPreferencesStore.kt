package com.shnapps.couple.core.datastore

import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.LimitAnswer
import com.shnapps.couple.core.model.LimitTag
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
 * hint and the notification setting describe the app, while these describe an evening. The same
 * data source implements both — the split is for the reader, and for the seam this will be replaced
 * at when a refusal starts going to the person's real limits instead (R5).
 */
interface PlaySettingsStore {
    /** How far tonight goes. Changed with one thumb, mid-card. */
    suspend fun setTier(tier: Intensity)

    /** Never show this card again, and nothing else of its kind either. Nobody is told. */
    suspend fun refuse(cardId: String, themes: Set<String>)

    /**
     * This card has been seen, so the deck stops offering it (D-124).
     *
     * [deckSize] is how many cards there are to see at all: once the record covers them, it is
     * reset to this one card and the deck comes round again.
     */
    suspend fun markPlayed(cardId: String, deckSize: Int)

    /** Deal from one pack, or from all of them when null (D-125). */
    suspend fun setLane(pack: String?)

    /**
     * Counts one card against today's free-tier allowance (M9).
     *
     * [day] is which day it belongs to: a stamp from another day resets the count rather than
     * adding to it, which is how the day rolls over with no scheduled job.
     */
    suspend fun countCard(day: Long)

    /** Counts one guided evening against this week's allowance. */
    suspend fun countNight(week: Long)

    /**
     * One answer in the limits profile, or null to unset it (M3).
     *
     * On the phone rather than in the account, because the profile is offered after the first
     * session and there may not be an account. `LimitsRepository` is what writes it to both.
     */
    suspend fun setLimit(tag: LimitTag, answer: LimitAnswer?)
}
