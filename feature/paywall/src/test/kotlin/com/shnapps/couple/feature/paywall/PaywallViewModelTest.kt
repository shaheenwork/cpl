package com.shnapps.couple.feature.paywall

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.model.Entitlement
import com.shnapps.couple.core.model.Gate
import com.shnapps.couple.core.model.SubscriptionState
import com.shnapps.couple.core.model.Tier
import com.shnapps.couple.core.testing.FakeBillingRepository
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
 * The paywall (BUILD_PROMPT.md §19): it says what is included, buys nothing on its own, and takes
 * "no" for an answer without complaint.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val billing = FakeBillingRepository()

    /** The purchase flow needs an Activity; nothing under test looks at it. */
    private val activity: Activity = ActivityStub()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { body() }

    private fun TestScope.screen(gate: Gate? = null) = PaywallViewModel(
        billing = billing,
        savedStateHandle = SavedStateHandle(mapOf("gate" to gate?.name)),
    ).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private fun TestScope.act(action: () -> Unit) {
        action()
        advanceUntilIdle()
    }

    @Test
    fun `says which limit they reached, when they came from one`() = test {
        val vm = screen(Gate.MULTI_DAY_ARC)

        assertThat(vm.uiState.value.gate).isEqualTo(Gate.MULTI_DAY_ARC)
        assertThat(vm.uiState.value.offer?.price).isEqualTo("£4.99")
        assertThat(vm.uiState.value.alreadyPremium).isFalse()
    }

    @Test
    fun `opened on its own, it is about what is included rather than about a limit`() = test {
        val vm = screen()

        assertThat(vm.uiState.value.gate).isNull()
    }

    @Test
    fun `a purchase is worth what the server says it is`() = test {
        val vm = screen(Gate.MYSTERY_NIGHT)

        act { vm.subscribe(activity) }

        assertThat(billing.purchases).isEqualTo(1)
        assertThat(vm.uiState.value.entitlement.tier).isEqualTo(Tier.PREMIUM)
        assertThat(vm.uiState.value.alreadyPremium).isTrue()
    }

    @Test
    fun `walking away from a purchase is not an error`() = test {
        val vm = screen()
        billing.failNext = AppError.Validation("cancelled")

        act { vm.subscribe(activity) }

        assertThat(vm.uiState.value.error).isNull()
        assertThat(vm.uiState.value.entitlement.premium).isFalse()
    }

    @Test
    fun `a purchase that could not be made says so, and grants nothing`() = test {
        val vm = screen()
        billing.failNext = AppError.Network()

        act { vm.subscribe(activity) }

        assertThat(vm.uiState.value.error).isEqualTo(PaywallError.OFFLINE)
        assertThat(vm.uiState.value.entitlement.premium).isFalse()
        act(vm::dismissError)
        assertThat(vm.uiState.value.error).isNull()
    }

    @Test
    fun `restoring finds a purchase, or says there is nothing to find`() = test {
        val vm = screen()

        act(vm::restore)
        assertThat(vm.uiState.value.error).isEqualTo(PaywallError.NOTHING_TO_RESTORE)

        billing.restorable = true
        act(vm::dismissError)
        act(vm::restore)

        assertThat(vm.uiState.value.entitlement.tier).isEqualTo(Tier.PREMIUM)
    }

    @Test
    fun `a couple already in sees that, and is not sold anything`() = test {
        billing.current.value = Entitlement(tier = Tier.PREMIUM, state = SubscriptionState.CANCELED)

        val vm = screen()

        assertThat(vm.uiState.value.alreadyPremium).isTrue()
        assertThat(vm.uiState.value.entitlement.endingSoon).isTrue()
    }
}

/** Enough of an Activity to pass along. The fake store never touches it. */
private class ActivityStub : Activity()
