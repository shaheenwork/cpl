package com.shnapps.couple.feature.pairing

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.analytics.AnalyticsEvent
import com.shnapps.couple.core.analytics.AnalyticsLogger
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.couple.PendingInvite
import com.shnapps.couple.core.model.PairingRole
import com.shnapps.couple.core.model.PairingState
import com.shnapps.couple.core.model.PairingStatus
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

/**
 * The pairing handshake, from one phone's point of view (BUILD_PROMPT.md section 8).
 *
 * The step is driven by the server's handshake state, so most of these arrange that state
 * on the fake and check the screen follows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val couples = FakeCoupleRepository()
    private val pendingInvite = PendingInvite()
    private val logged = mutableListOf<AnalyticsEvent>()

    private fun TestScope.viewModel(): PairingViewModel =
        PairingViewModel(couples, pendingInvite, AnalyticsLogger { logged += it }).also { vm ->
            // uiState is WhileSubscribed, as on a real screen; keep a subscriber alive.
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        }

    private val PairingViewModel.step get() = uiState.value.step

    @Test
    fun `starts by offering both paths`() = runTest(mainDispatcherRule.testDispatcher) {
        assertThat(viewModel().step).isEqualTo(PairingStep.Choose)
    }

    @Test
    fun `an invite link opens straight onto the code, prefilled`() = runTest(mainDispatcherRule.testDispatcher) {
        pendingInvite.offer("482913")
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.step).isEqualTo(PairingStep.EnterCode)
        assertThat(vm.uiState.value.codeInput).isEqualTo("482913")
        // Consumed once: it must not re-apply after the user navigates away and back.
        assertThat(pendingInvite.pending.value).isNull()
    }

    @Test
    fun `inviting shows the code`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.startInvite()
        advanceUntilIdle()

        assertThat(vm.step).isEqualTo(PairingStep.Invite(FakeCoupleRepository.CODE, FakeCoupleRepository.EXPIRES))
    }

    @Test
    fun `when someone enters the code, the creator is asked to confirm with the symbols`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = viewModel()
            vm.startInvite()
            advanceUntilIdle()

            couples.partnerEnteredCode()
            advanceUntilIdle()

            assertThat(vm.step).isEqualTo(PairingStep.ConfirmPartner(FakeCoupleRepository.SYMBOLS))
            // Entering a code alone must never pair anyone.
            assertThat(couples.couple.value).isNull()
        }

    @Test
    fun `approving pairs the couple`() = runTest(mainDispatcherRule.testDispatcher) {
        couples.partnerEnteredCode()
        val vm = viewModel()

        vm.approve()
        advanceUntilIdle()

        assertThat(couples.calls).contains("respond:true")
        assertThat(vm.step).isEqualTo(PairingStep.Paired)
        assertThat(logged).contains(AnalyticsEvent.CoupleCreated)
    }

    @Test
    fun `declining pairs nobody and returns to the start`() = runTest(mainDispatcherRule.testDispatcher) {
        couples.partnerEnteredCode()
        val vm = viewModel()

        vm.decline()
        advanceUntilIdle()

        assertThat(couples.calls).contains("respond:false")
        assertThat(couples.couple.value).isNull()
        assertThat(vm.step).isEqualTo(PairingStep.Choose)
    }

    @Test
    fun `the code field keeps digits only, at most six`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.startEnteringCode()
        vm.onCodeChange("48a2-91 37")
        advanceUntilIdle()

        assertThat(vm.uiState.value.codeInput).isEqualTo("482913")
        assertThat(vm.uiState.value.canSubmitCode).isTrue()

        vm.onCodeChange("4829")
        advanceUntilIdle()
        assertThat(vm.uiState.value.canSubmitCode).isFalse()
    }

    @Test
    fun `entering a code waits with the same symbols the creator sees`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = viewModel()
            vm.startEnteringCode()
            vm.onCodeChange("482913")
            advanceUntilIdle()

            vm.submitCode()
            advanceUntilIdle()

            assertThat(couples.calls).containsExactly("request:482913")
            assertThat(vm.step).isEqualTo(PairingStep.Waiting(FakeCoupleRepository.SYMBOLS))
        }

    @Test
    fun `a double tap sends one request`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.startEnteringCode()
        vm.onCodeChange("482913")
        advanceUntilIdle()

        vm.submitCode()
        vm.submitCode()
        advanceUntilIdle()

        assertThat(couples.calls.count { it.startsWith("request") }).isEqualTo(1)
    }

    @Test
    fun `a bad code shows one generic message and stays on entry`() = runTest(mainDispatcherRule.testDispatcher) {
        couples.requestResult = Outcome.Failure(AppError.NotFound())
        val vm = viewModel()
        vm.startEnteringCode()
        vm.onCodeChange("000000")
        advanceUntilIdle()

        vm.submitCode()
        advanceUntilIdle()

        assertThat(vm.step).isEqualTo(PairingStep.EnterCode)
        val message = vm.uiState.value.errorMessage.orEmpty().lowercase()
        assertThat(message).isNotEmpty()
        // Missing, expired and used codes are indistinguishable, server and client alike.
        for (leak in listOf("expired", "used", "exist")) assertThat(message).doesNotContain(leak)
    }

    @Test
    fun `rate limiting is explained`() = runTest(mainDispatcherRule.testDispatcher) {
        couples.requestResult = Outcome.Failure(AppError.RateLimited())
        val vm = viewModel()
        vm.startEnteringCode()
        vm.onCodeChange("482913")
        advanceUntilIdle()
        vm.submitCode()
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).contains("Too many tries")
    }

    @Test
    fun `the partner approving on their phone completes pairing here`() =
        runTest(mainDispatcherRule.testDispatcher) {
            couples.pairing.value =
                PairingState(PairingRole.JOINER, PairingStatus.WAITING, "482913", FakeCoupleRepository.SYMBOLS)
            val vm = viewModel()
            advanceUntilIdle()
            assertThat(vm.step).isInstanceOf(PairingStep.Waiting::class.java)

            couples.pairing.value = null
            couples.couple.value = FakeCoupleRepository.COUPLE_ID
            advanceUntilIdle()

            assertThat(vm.step).isEqualTo(PairingStep.Paired)
        }

    @Test
    fun `a declined request says so, and starting again clears it`() = runTest(mainDispatcherRule.testDispatcher) {
        couples.pairing.value = PairingState(PairingRole.JOINER, PairingStatus.DECLINED, "482913")
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.step).isEqualTo(PairingStep.Declined)

        vm.cancel()
        advanceUntilIdle()

        assertThat(vm.step).isEqualTo(PairingStep.Choose)
    }

    @Test
    fun `an in-progress handshake resumes after process death`() = runTest(mainDispatcherRule.testDispatcher) {
        // The server still has the creator mid-handshake; a fresh ViewModel picks it up.
        couples.partnerEnteredCode()
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.step).isEqualTo(PairingStep.ConfirmPartner(FakeCoupleRepository.SYMBOLS))
    }
}
