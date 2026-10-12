package com.shnapps.couple.feature.settings

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.FakeClock
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.model.AppLockMode
import com.shnapps.couple.core.model.AuthUser
import com.shnapps.couple.core.model.UserProfile
import com.shnapps.couple.core.security.AppLockManager
import com.shnapps.couple.core.security.PinHasher
import com.shnapps.couple.core.testing.FakeAccountRepository
import com.shnapps.couple.core.testing.FakeAppPreferencesStore
import com.shnapps.couple.core.testing.FakeAuthRepository
import com.shnapps.couple.core.testing.FakeBillingRepository
import com.shnapps.couple.core.testing.FakeCoupleRepository
import com.shnapps.couple.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Settings (BUILD_PROMPT.md §14.9): an export that lands as a file, unpairing, and the deletion
 * that takes the couple with it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val account = FakeAccountRepository()
    private val couples = FakeCoupleRepository()
    private val auth = FakeAuthRepository()
    private val billing = FakeBillingRepository()
    private val directory = File("exports")
    private val store = FakeAppPreferencesStore()
    private val appLock = AppLockManager(store, PinHasher(), FakeClock())

    private fun test(body: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { body() }

    private fun TestScope.screen() = SettingsViewModel(account, couples, appLock, auth, billing).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private fun TestScope.act(action: () -> Unit) {
        action()
        advanceUntilIdle()
    }

    private fun pairedWith(coupleId: String) {
        auth.user.value = AuthUser(uid = "u1", email = "alex@example.com", isEmailVerified = true)
        auth.profile.value = UserProfile(uid = "u1", coupleId = coupleId)
    }

    @Test
    fun `says who is signed in, and whether they are paired`() = test {
        pairedWith("c1")

        val vm = screen()

        assertThat(vm.uiState.value.email).isEqualTo("alex@example.com")
        assertThat(vm.uiState.value.paired).isTrue()
    }

    @Test
    fun `an export comes back as a file on this phone`() = test {
        val vm = screen()

        act { vm.export(directory) }

        assertThat(account.exports).isEqualTo(1)
        assertThat(vm.uiState.value.export?.fileName).isEqualTo("afterhours-export.json")
        assertThat(vm.uiState.value.export?.bytes).isEqualTo(42L)
        assertThat(vm.uiState.value.exporting).isFalse()
        act(vm::dismissExport)
        assertThat(vm.uiState.value.export).isNull()
    }

    @Test
    fun `an export that did not happen says so, and leaves no file`() = test {
        val vm = screen()
        account.failNext = AppError.Network()

        act { vm.export(directory) }

        assertThat(vm.uiState.value.error).isEqualTo(SettingsError.OFFLINE)
        assertThat(vm.uiState.value.export).isNull()
        act(vm::dismissError)
        assertThat(vm.uiState.value.error).isNull()
    }

    @Test
    fun `the app lock row knows whether the lock is on`() = test {
        val vm = screen()
        assertThat(vm.uiState.value.lock).isEqualTo(AppLockMode.OFF)

        act { store.state.value = store.state.value.copy(lockMode = AppLockMode.PIN) }

        assertThat(vm.uiState.value.lock).isEqualTo(AppLockMode.PIN)
    }

    @Test
    fun `asking to unpair is a question, and touches nothing`() = test {
        pairedWith("c1")
        val vm = screen()

        act(vm::askUnpair)

        assertThat(vm.uiState.value.confirmingUnpair).isTrue()
        // The row only asks. Clearing the pair on both phones is one answer later.
        assertThat(couples.calls).doesNotContain("unpair")
        assertThat(vm.uiState.value.paired).isTrue()
    }

    @Test
    fun `keeping the pair closes the question and changes nothing`() = test {
        pairedWith("c1")
        val vm = screen()
        act(vm::askUnpair)

        act(vm::keepPair)

        assertThat(vm.uiState.value.confirmingUnpair).isFalse()
        assertThat(couples.calls).doesNotContain("unpair")
        assertThat(vm.uiState.value.unpaired).isFalse()
    }

    @Test
    fun `unpairing ends the couple, says so, and keeps the account`() = test {
        pairedWith("c1")
        val vm = screen()
        act(vm::askUnpair)

        act(vm::unpair)

        assertThat(couples.calls).contains("unpair")
        assertThat(vm.uiState.value.confirmingUnpair).isFalse()
        assertThat(vm.uiState.value.busy).isFalse()
        assertThat(vm.uiState.value.unpaired).isTrue()
        // The server clears the profile a moment later; until then the screen already agrees with
        // what it was just told, instead of showing a pair that is gone.
        assertThat(vm.uiState.value.paired).isFalse()
        assertThat(account.deleted).isFalse()
    }

    @Test
    fun `an unpairing that did not happen says so, and the pair is still there`() = test {
        pairedWith("c1")
        val vm = screen()
        couples.unpairResult = Outcome.Failure(AppError.Network())
        act(vm::askUnpair)

        act(vm::unpair)

        assertThat(vm.uiState.value.error).isEqualTo(SettingsError.OFFLINE)
        assertThat(vm.uiState.value.unpaired).isFalse()
        assertThat(vm.uiState.value.paired).isTrue()
        assertThat(vm.uiState.value.busy).isFalse()
    }

    @Test
    fun `what unpairing said can be put away`() = test {
        pairedWith("c1")
        val vm = screen()
        act(vm::unpair)

        act(vm::dismissUnpaired)

        assertThat(vm.uiState.value.unpaired).isFalse()
    }

    @Test
    fun `deleting the account goes through the server, and only then`() = test {
        val vm = screen()

        act(vm::deleteAccount)

        assertThat(account.deleted).isTrue()
        assertThat(vm.uiState.value.deleted).isTrue()
    }

    @Test
    fun `a deletion that failed leaves the account alone`() = test {
        val vm = screen()
        account.failNext = AppError.Unknown()

        act(vm::deleteAccount)

        assertThat(vm.uiState.value.deleted).isFalse()
        assertThat(vm.uiState.value.error).isEqualTo(SettingsError.OTHER)
    }
}
