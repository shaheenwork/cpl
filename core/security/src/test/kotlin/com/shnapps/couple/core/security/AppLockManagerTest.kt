package com.shnapps.couple.core.security

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.FakeClock
import com.shnapps.couple.core.datastore.AppPreferences
import com.shnapps.couple.core.model.AppLockMode
import com.shnapps.couple.core.testing.FakeAppPreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * The lock's behaviour, which is where its promises actually live (BUILD_PROMPT.md §3.4).
 *
 * The single most important assertion here is the first one: **the app starts locked.**
 * Anything else would mean a cold start, a crash or process death quietly walks straight
 * past the lock — exactly the situation it exists for.
 */
class AppLockManagerTest {

    // A real in-memory store rather than a mock: writes are kept, so behaviour that depends
    // on what was saved (a PIN existing before biometrics) is actually exercised.
    private val preferences = FakeAppPreferencesStore()
    private val preferencesFlow = preferences.state
    private val clock = FakeClock(now = 1_000L)
    private val hasher = PinHasher()

    private fun manager() = AppLockManager(preferences, hasher, clock)

    @Test
    fun `starts locked when a lock is configured`() = runTest {
        preferencesFlow.value = AppPreferences(lockMode = AppLockMode.PIN)
        assertThat(manager().lockState.first()).isEqualTo(LockState.Locked)
    }

    @Test
    fun `is unlocked when no lock is configured`() = runTest {
        preferencesFlow.value = AppPreferences(lockMode = AppLockMode.OFF)
        assertThat(manager().lockState.first()).isEqualTo(LockState.Unlocked)
    }

    @Test
    fun `unlocks after markUnlocked`() = runTest {
        preferencesFlow.value = AppPreferences(lockMode = AppLockMode.PIN)
        val manager = manager()
        manager.markUnlocked()
        assertThat(manager.lockState.first()).isEqualTo(LockState.Unlocked)
    }

    @Test
    fun `switching the lock on in settings takes effect immediately`() = runTest {
        // Starts off and unlocked, then the user enables the lock. It must not stay
        // unlocked just because nothing re-evaluated.
        preferencesFlow.value = AppPreferences(lockMode = AppLockMode.OFF)
        val manager = manager()
        assertThat(manager.lockState.first()).isEqualTo(LockState.Unlocked)

        preferencesFlow.value = AppPreferences(lockMode = AppLockMode.PIN)
        assertThat(manager.lockState.first()).isEqualTo(LockState.Locked)
    }

    @Test
    fun `a brief trip to the background does not re-lock`() = runTest {
        preferencesFlow.value = AppPreferences(lockMode = AppLockMode.PIN, lockTimeoutMillis = 60_000L)
        val manager = manager()
        manager.markUnlocked()

        manager.onBackgrounded()
        clock.advanceBy(5_000L)
        manager.onForegrounded()

        // Glancing at a notification mid-session must not demand a fingerprint, or people
        // switch the lock off entirely.
        assertThat(manager.lockState.first()).isEqualTo(LockState.Unlocked)
    }

    @Test
    fun `a long time in the background re-locks`() = runTest {
        preferencesFlow.value = AppPreferences(lockMode = AppLockMode.PIN, lockTimeoutMillis = 60_000L)
        val manager = manager()
        manager.markUnlocked()

        manager.onBackgrounded()
        clock.advanceBy(120_000L)
        manager.onForegrounded()

        assertThat(manager.lockState.first()).isEqualTo(LockState.Locked)
    }

    @Test
    fun `re-locks exactly at the timeout boundary`() = runTest {
        preferencesFlow.value = AppPreferences(lockMode = AppLockMode.PIN, lockTimeoutMillis = 60_000L)
        val manager = manager()
        manager.markUnlocked()

        manager.onBackgrounded()
        clock.advanceBy(60_000L)
        manager.onForegrounded()

        assertThat(manager.lockState.first()).isEqualTo(LockState.Locked)
    }

    @Test
    fun `foregrounding without a prior background does nothing`() = runTest {
        preferencesFlow.value = AppPreferences(lockMode = AppLockMode.PIN)
        val manager = manager()
        manager.markUnlocked()

        manager.onForegrounded()

        assertThat(manager.lockState.first()).isEqualTo(LockState.Unlocked)
    }

    @Test
    fun `verifyPin unlocks on the correct pin`() = runTest {
        val salt = hasher.newSalt()
        preferencesFlow.value = AppPreferences(
            lockMode = AppLockMode.PIN,
            pinHash = hasher.hash("2468", salt),
            pinSalt = salt,
        )
        val manager = manager()

        assertThat(manager.verifyPin("2468")).isTrue()
        assertThat(manager.lockState.first()).isEqualTo(LockState.Unlocked)
    }

    @Test
    fun `verifyPin stays locked on a wrong pin`() = runTest {
        val salt = hasher.newSalt()
        preferencesFlow.value = AppPreferences(
            lockMode = AppLockMode.PIN,
            pinHash = hasher.hash("2468", salt),
            pinSalt = salt,
        )
        val manager = manager()

        assertThat(manager.verifyPin("1111")).isFalse()
        assertThat(manager.lockState.first()).isEqualTo(LockState.Locked)
    }

    @Test
    fun `verifyPin fails closed when no pin is stored`() = runTest {
        preferencesFlow.value = AppPreferences(lockMode = AppLockMode.PIN)
        assertThat(manager().verifyPin("2468")).isFalse()
    }

    @Test
    fun `setPin rejects a pin outside the allowed length`() = runTest {
        // No stubbing needed: an invalid PIN is rejected before the preferences are touched.
        // (A `coEvery` here once hung this test for an hour — mockk records suspend stubs
        // through an internal runBlocking, which blocks runTest's thread so its own
        // timeout can never fire. See DECISIONS.md D-013.)
        val manager = manager()

        assertFailsWith<IllegalArgumentException> { manager.setPin("12") }
        assertFailsWith<IllegalArgumentException> { manager.setPin("123456789") }
    }

    @Test
    fun `disableLock unlocks immediately`() = runTest {
        preferencesFlow.value = AppPreferences(lockMode = AppLockMode.PIN)
        val manager = manager()

        manager.disableLock()
        preferencesFlow.value = AppPreferences(lockMode = AppLockMode.OFF)

        assertThat(manager.lockState.first()).isEqualTo(LockState.Unlocked)
    }

    @Test
    fun `setting a pin turns the lock on without locking the user out`() = runTest {
        val manager = manager()
        manager.setPin("2468")

        assertThat(preferences.state.value.lockMode).isEqualTo(AppLockMode.PIN)
        // They just proved they know the PIN; locking them out of the settings screen they
        // used to set it would be absurd.
        assertThat(manager.lockState.first()).isEqualTo(LockState.Unlocked)
    }

    @Test
    fun `biometric unlock cannot be enabled without a pin to fall back on`() = runTest {
        val manager = manager()
        assertFailsWith<IllegalStateException> { manager.enableBiometric() }
        assertThat(preferences.state.value.lockMode).isEqualTo(AppLockMode.OFF)
    }

    @Test
    fun `biometric unlock can be enabled once a pin exists`() = runTest {
        val manager = manager()
        manager.setPin("2468")
        manager.enableBiometric()

        assertThat(preferences.state.value.lockMode).isEqualTo(AppLockMode.BIOMETRIC)
        assertThat(manager.hasPinFallback()).isTrue()
    }
}
