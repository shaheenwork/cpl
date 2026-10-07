package com.shnapps.couple.feature.onboarding

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.testing.FakeIdentity
import com.shnapps.couple.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Where the app sends someone on open (V2 rule 1).
 *
 * There used to be six answers here, and five of them were about an account: a session to validate,
 * a profile to fetch, welcome beats to have seen, a couple to have set up. V2 has no accounts, so
 * there are two answers and one fact decides between them — has anybody on this phone said they are
 * eighteen.
 *
 * What is worth asserting is what it no longer does: it never waits on a network, and it never
 * takes anything but that attestation as permission to go past the gate.
 */
class SplashViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val identity = FakeIdentity()

    @Test
    fun `nobody has said they are eighteen, so the gate comes first`() = runTest(mainDispatcherRule.testDispatcher) {
        identity.ageConfirmed = false

        val viewModel = SplashViewModel(identity)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.destination).isEqualTo(StartDestination.AgeGate)
    }

    @Test
    fun `somebody already has, so the app opens where it left off`() = runTest(mainDispatcherRule.testDispatcher) {
        identity.ageConfirmed = true

        val viewModel = SplashViewModel(identity)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.destination).isEqualTo(StartDestination.Home)
    }

    @Test
    fun `it is loading until it has decided, and never renders a guess`() = runTest(mainDispatcherRule.testDispatcher) {
        // The first frame is the one that traps people: a screen that assumes "home" and then
        // corrects itself has already shown the app to somebody who never passed the gate (D-027).
        val viewModel = SplashViewModel(identity)

        assertThat(viewModel.uiState.value.destination).isEqualTo(StartDestination.Loading)
    }

    @Test
    fun `nothing about an account is consulted, because there is none`() = runTest(mainDispatcherRule.testDispatcher) {
        identity.ageConfirmed = true

        SplashViewModel(identity)
        advanceUntilIdle()

        // Opening the app creates no identity with the server. Only pairing does that, and a phone
        // that never pairs never appears in the project at all.
        assertThat(identity.identityCalls).isEqualTo(0)
    }
}
