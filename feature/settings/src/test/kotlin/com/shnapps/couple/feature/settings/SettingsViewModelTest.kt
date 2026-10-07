package com.shnapps.couple.feature.settings

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.model.AuthUser
import com.shnapps.couple.core.model.UserProfile
import com.shnapps.couple.core.security.DiscreetIcon
import com.shnapps.couple.core.testing.FakeAccountRepository
import com.shnapps.couple.core.testing.FakeAuthRepository
import com.shnapps.couple.core.testing.FakeBillingRepository
import com.shnapps.couple.core.testing.FakeCoupleRepository
import com.shnapps.couple.core.testing.FakeDiscreetIcons
import com.shnapps.couple.core.testing.FakeLevelAccessRepository
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
    private val icons = FakeDiscreetIcons()

    @Test
    fun `the launcher icon can be changed, and is read back from the platform`() = test {
        val screen = screen()
        assertThat(screen.uiState.value.icon).isEqualTo(DiscreetIcon.AFTERHOURS)

        act { screen.setIcon(DiscreetIcon.CALCULATOR) }

        assertThat(icons.shown).containsExactly(DiscreetIcon.CALCULATOR)
        // Read back rather than assumed: the platform is the record of which alias is enabled.
        assertThat(screen.uiState.value.icon).isEqualTo(DiscreetIcon.CALCULATOR)
    }

    private val levels = FakeLevelAccessRepository()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { body() }

    private fun TestScope.screen() = SettingsViewModel(account, couples, icons, auth, billing, levels).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private fun TestScope.act(action: () -> Unit) {
        action()
        advanceUntilIdle()
    }

    @Test
    fun `says who is signed in, and whether they are paired`() = test {
        auth.user.value = AuthUser(uid = "u1", email = "alex@example.com", isEmailVerified = true)
        auth.profile.value = UserProfile(uid = "u1", coupleId = "c1")

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
    fun `unpairing ends the couple and keeps the account`() = test {
        val vm = screen()

        act(vm::unpair)

        assertThat(couples.calls).contains("unpair")
        assertThat(vm.uiState.value.unpaired).isTrue()
        assertThat(account.deleted).isFalse()
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
