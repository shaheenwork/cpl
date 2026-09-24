package com.shnapps.couple.feature.settings

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.model.NotificationSettings
import com.shnapps.couple.core.model.QuietHours
import com.shnapps.couple.core.testing.FakeNotificationsRepository
import com.shnapps.couple.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** The three notification choices of BUILD_PROMPT.md §16.2, and the hours nobody wants to hear from anyone. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val notifications = FakeNotificationsRepository()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { body() }

    private fun TestScope.screen() = NotificationSettingsViewModel(notifications).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private fun TestScope.act(action: () -> Unit) {
        action()
        advanceUntilIdle()
    }

    @Test
    fun `previews are hidden until the user asks for them`() = test {
        val vm = screen()

        assertThat(vm.uiState.value.settings).isEqualTo(NotificationSettings())
        assertThat(vm.uiState.value.settings.showContent).isFalse()
        assertThat(vm.uiState.value.settings.enabled).isTrue()

        act { vm.setShowContent(true) }

        assertThat(notifications.current.value.showContent).isTrue()
        assertThat(vm.uiState.value.settings.showContent).isTrue()
    }

    @Test
    fun `turning them off is kept where the server can read it`() = test {
        val vm = screen()

        act { vm.setEnabled(false) }

        // Not a phone-side filter: the server is what decides whether to send at all (§16).
        assertThat(notifications.current.value.enabled).isFalse()
    }

    @Test
    fun `quiet hours are a window, or nothing`() = test {
        val vm = screen()

        act { vm.setQuietHours(true) }
        assertThat(notifications.current.value.quietHours).isEqualTo(QuietHours.NIGHT)
        assertThat(vm.uiState.value.quietHoursOn).isTrue()

        act { vm.setQuietHours(false) }
        assertThat(notifications.current.value.quietHours).isNull()
    }

    @Test
    fun `a setting that did not save says so, and leaves the old one showing`() = test {
        val vm = screen()
        notifications.failNext = AppError.Network()

        act { vm.setShowContent(true) }

        assertThat(vm.uiState.value.failed).isTrue()
        assertThat(vm.uiState.value.settings.showContent).isFalse()
        act(vm::dismissError)
        assertThat(vm.uiState.value.failed).isFalse()
    }
}
