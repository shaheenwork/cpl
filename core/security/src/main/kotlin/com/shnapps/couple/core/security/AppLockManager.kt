package com.shnapps.couple.core.security

import com.shnapps.couple.core.common.Clock
import com.shnapps.couple.core.datastore.AppPreferencesStore
import com.shnapps.couple.core.model.AppLockMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the app is currently sealed behind the lock screen. */
enum class LockState { Unlocked, Locked }

/**
 * Owns whether the app is locked (BUILD_PROMPT.md §3.4, §57).
 *
 * The rule is simple and deliberately strict: **the app starts locked.** Anything else
 * would mean process death, a cold start or a crash quietly bypasses the lock, which is
 * exactly the situation it exists for.
 *
 * Re-locking is time-based rather than instant on background, because the app is
 * constantly backgrounded in normal use — answering a message mid-session, checking the
 * time — and demanding a fingerprint every time would train people to switch the lock off.
 */
@Singleton
class AppLockManager @Inject constructor(
    private val preferences: AppPreferencesStore,
    private val pinHasher: PinHasher,
    private val clock: Clock,
) {
    private val unlocked = MutableStateFlow(false)
    private var backgroundedAtMillis: Long? = null

    val lockMode: Flow<AppLockMode> = preferences.preferences.map { it.lockMode }

    /**
     * Locked unless the lock is switched off *and* stays off. Combining rather than
     * caching means switching the lock on in settings takes effect immediately.
     */
    val lockState: Flow<LockState> = combine(lockMode, unlocked) { mode, isUnlocked ->
        if (!mode.isEnabled || isUnlocked) LockState.Unlocked else LockState.Locked
    }

    fun markUnlocked() {
        unlocked.value = true
        backgroundedAtMillis = null
    }

    fun lockNow() {
        unlocked.value = false
        backgroundedAtMillis = null
    }

    /** Called when the app goes to the background. Starts the re-lock clock. */
    fun onBackgrounded() {
        backgroundedAtMillis = clock.nowMillis()
    }

    /**
     * Called when the app returns to the foreground. Re-locks if it was away longer than
     * the configured timeout.
     */
    suspend fun onForegrounded() {
        val since = backgroundedAtMillis ?: return
        val timeout = preferences.preferences.first().lockTimeoutMillis
        if (clock.nowMillis() - since >= timeout) {
            lockNow()
        }
        backgroundedAtMillis = null
    }

    suspend fun verifyPin(pin: String): Boolean {
        val prefs = preferences.preferences.first()
        val hash = prefs.pinHash ?: return false
        val salt = prefs.pinSalt ?: return false
        val matches = pinHasher.verify(pin, salt, hash)
        if (matches) markUnlocked()
        return matches
    }

    /**
     * Sets the PIN and turns the lock on.
     *
     * Also marks the session unlocked: the user has just proved they know the PIN, and
     * without this, switching the lock on would immediately lock them out of the very screen
     * they used to set it.
     */
    suspend fun setPin(pin: String) {
        require(PinRules.isValid(pin)) { "PIN must be ${PinRules.MIN_LENGTH}-${PinRules.MAX_LENGTH} digits" }
        val salt = pinHasher.newSalt()
        preferences.setPinLock(hash = pinHasher.hash(pin, salt), salt = salt)
        markUnlocked()
    }

    /**
     * Switches to biometric unlock. Requires a PIN to already exist, because biometrics can
     * fail or lock out and the user must never be stranded outside their own app (§3.4).
     */
    suspend fun enableBiometric() {
        check(hasPinFallback()) { "Set a PIN before enabling biometric unlock" }
        preferences.setBiometricLock()
        markUnlocked()
    }

    suspend fun disableLock() {
        preferences.setLockOff()
        markUnlocked()
    }

    /** True when a PIN exists to fall back to if biometrics fail or are locked out. */
    suspend fun hasPinFallback(): Boolean = preferences.preferences.first().pinHash != null
}
