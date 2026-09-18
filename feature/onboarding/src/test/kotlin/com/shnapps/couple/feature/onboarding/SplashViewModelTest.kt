package com.shnapps.couple.feature.onboarding

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.model.AgeAttestation
import com.shnapps.couple.core.model.UserProfile
import com.shnapps.couple.core.testing.FakeAuthRepository
import com.shnapps.couple.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Where the app sends someone on open (BUILD_PROMPT.md §14.1).
 *
 * The routing order *is* the order of the guarantees, so each branch gets a test — and the
 * one that matters most is that the device-local age hint can never carry someone past the
 * gate on its own (§3.1).
 */
class SplashViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()

    private fun resolve(): StartDestination {
        val viewModel = SplashViewModel(auth)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        return viewModel.uiState.value.destination
    }

    @Test
    fun `signed out goes to auth`() = runTest(mainDispatcherRule.testDispatcher) {
        auth.user.value = null
        assertThat(resolve()).isEqualTo(StartDestination.Auth)
    }

    @Test
    fun `signed in without an attestation goes to the age gate`() = runTest(mainDispatcherRule.testDispatcher) {
        auth.user.value = FakeAuthRepository.DEFAULT_USER
        auth.serverAgeConfirmed = false
        assertThat(resolve()).isEqualTo(StartDestination.AgeGate)
    }

    @Test
    fun `the local hint alone never passes the age gate`() = runTest(mainDispatcherRule.testDispatcher) {
        // The device-local flag says confirmed; the server record does not. A local flag is
        // exactly what someone would clear or forge, so it must not be trusted to open the
        // gate (§3.1).
        auth.user.value = FakeAuthRepository.DEFAULT_USER
        auth.ageHint = true
        auth.serverAgeConfirmed = false

        assertThat(resolve()).isEqualTo(StartDestination.AgeGate)
    }

    @Test
    fun `an existing account is never taken as proof of age`() = runTest(mainDispatcherRule.testDispatcher) {
        // A profile exists — the user has been here before — but carries no attestation.
        auth.user.value = FakeAuthRepository.DEFAULT_USER
        auth.profile.value = UserProfile(uid = FakeAuthRepository.DEFAULT_USER.uid, ageAttestation = null)
        auth.serverAgeConfirmed = false

        assertThat(resolve()).isEqualTo(StartDestination.AgeGate)
    }

    @Test
    fun `confirmed with no profile yet goes to welcome`() = runTest(mainDispatcherRule.testDispatcher) {
        auth.user.value = FakeAuthRepository.DEFAULT_USER
        auth.serverAgeConfirmed = true
        auth.profile.value = null

        assertThat(resolve()).isEqualTo(StartDestination.Welcome)
    }

    @Test
    fun `confirmed but unpaired goes to couple setup`() = runTest(mainDispatcherRule.testDispatcher) {
        auth.user.value = FakeAuthRepository.DEFAULT_USER
        auth.serverAgeConfirmed = true
        auth.profile.value = confirmedProfile(coupleId = null)

        assertThat(resolve()).isEqualTo(StartDestination.CoupleSetup)
    }

    @Test
    fun `confirmed and paired goes home`() = runTest(mainDispatcherRule.testDispatcher) {
        auth.user.value = FakeAuthRepository.DEFAULT_USER
        auth.serverAgeConfirmed = true
        auth.profile.value = confirmedProfile(coupleId = "couple_1")

        assertThat(resolve()).isEqualTo(StartDestination.Home)
    }

    @Test
    fun `stays loading until resolved`() = runTest(mainDispatcherRule.testDispatcher) {
        auth.user.value = FakeAuthRepository.DEFAULT_USER
        val viewModel = SplashViewModel(auth)
        // Nothing has run yet on the standard test dispatcher.
        assertThat(viewModel.uiState.value.destination).isEqualTo(StartDestination.Loading)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.destination).isNotEqualTo(StartDestination.Loading)
    }

    private fun confirmedProfile(coupleId: String?) = UserProfile(
        uid = FakeAuthRepository.DEFAULT_USER.uid,
        ageAttestation = AgeAttestation(confirmed = true, confirmedAtEpochMillis = 1L),
        coupleId = coupleId,
    )
}
