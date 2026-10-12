package com.shnapps.couple

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.testing.FakeNotificationsRepository
import com.shnapps.couple.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * The permission to notify is asked for after pairing, once (D-151).
 *
 * It used to be asked the first time the app opened, to somebody with nobody to send them anything.
 * What is worth holding is that the moment is the repository's — paired and not asked — and that
 * putting the question marks it as put, so it is never put twice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotifyAskViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val notifications = FakeNotificationsRepository()

    @Test
    fun `nothing is asked of a phone that is not paired`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = NotifyAskViewModel(notifications)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.shouldAsk.collect {} }
        advanceUntilIdle()

        assertThat(viewModel.shouldAsk.value).isFalse()
    }

    @Test
    fun `once paired, it is time to ask`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = NotifyAskViewModel(notifications)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.shouldAsk.collect {} }

        notifications.shouldAsk.value = true
        advanceUntilIdle()

        assertThat(viewModel.shouldAsk.value).isTrue()
    }

    @Test
    fun `having asked, it is never time again`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = NotifyAskViewModel(notifications)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.shouldAsk.collect {} }
        notifications.shouldAsk.value = true
        advanceUntilIdle()

        viewModel.asked()
        advanceUntilIdle()

        assertThat(notifications.asked).isEqualTo(1)
        assertThat(viewModel.shouldAsk.value).isFalse()
    }
}
