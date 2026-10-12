package com.shnapps.couple.feature.store

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.data.entitlement.LevelAccess
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.testing.FakeBillingRepository
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

/**
 * The store (V2 rule 5): three packs, what each costs, and no pressure of any kind.
 *
 * The two rules worth a test each are the ones a store is tempted to break. It must not sell
 * something that is already open — not a pack they own, and not anything at all while the opening
 * month is still running — and it must take "no" for an answer without leaving a mark.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoreViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val billing = FakeBillingRepository()
    private val levels = FakeLevelAccessRepository(LevelAccess.FREE)

    /** The purchase flow needs an Activity; nothing under test looks at it. */
    private val activity: Activity = ActivityStub()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { body() }

    private fun TestScope.screen(level: Intensity? = null) = StoreViewModel(
        billing = billing,
        levels = levels,
        savedStateHandle = SavedStateHandle(mapOf("level" to level?.name)),
    ).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private fun TestScope.act(action: () -> Unit) {
        action()
        advanceUntilIdle()
    }

    @Test
    fun `the pack they tapped is the one in front, priced`() = test {
        val vm = screen(Intensity.HOT)

        assertThat(vm.uiState.value.asked).isEqualTo(Intensity.HOT)
        assertThat(vm.uiState.value.packs.map { it.level })
            .containsExactly(Intensity.SPICY, Intensity.HOT, Intensity.EXTREME)
            .inOrder()
        assertThat(vm.uiState.value.packs.first { it.level == Intensity.HOT }.price).isNotNull()
    }

    @Test
    fun `opened on its own, nothing is in front`() = test {
        val vm = screen()

        assertThat(vm.uiState.value.asked).isNull()
    }

    @Test
    fun `buying a pack opens it, and the levels below it with it`() = test {
        val vm = screen(Intensity.HOT)

        act { vm.buy(activity, Intensity.HOT) }
        levels.access.value = LevelAccess(ceiling = Intensity.HOT, owned = setOf(Intensity.HOT))
        advanceUntilIdle()

        assertThat(billing.bought).containsExactly(Intensity.HOT)
        // The ladder: Spicy came with it, and only Extreme is still for sale.
        assertThat(vm.uiState.value.packs.filter { it.owned }.map { it.level })
            .containsExactly(Intensity.SPICY, Intensity.HOT)
        assertThat(vm.uiState.value.everythingOpen).isFalse()
    }

    @Test
    fun `nothing is for sale while everything is already open`() = test {
        levels.access.value = LevelAccess.OPEN

        val vm = screen()

        assertThat(vm.uiState.value.everythingOpen).isTrue()
        assertThat(vm.uiState.value.access.freeForNow).isTrue()
    }

    @Test
    fun `walking away from a purchase is not an error`() = test {
        val vm = screen()
        billing.failNext = AppError.Validation("cancelled")

        act { vm.buy(activity, Intensity.SPICY) }

        assertThat(vm.uiState.value.error).isNull()
        assertThat(vm.uiState.value.access.anythingLocked).isTrue()
    }

    @Test
    fun `a purchase that could not be made says so, and grants nothing`() = test {
        val vm = screen()
        billing.failNext = AppError.Network()

        act { vm.buy(activity, Intensity.SPICY) }

        assertThat(vm.uiState.value.error).isEqualTo(StoreError.OFFLINE)
        assertThat(vm.uiState.value.access.ceiling).isEqualTo(Intensity.FLIRTY)
        act(vm::dismissError)
        assertThat(vm.uiState.value.error).isNull()
    }

    @Test
    fun `a payment still clearing is said, and grants nothing`() = test {
        val vm = screen()
        billing.failNext = AppError.Validation("pending")

        act { vm.buy(activity, Intensity.HOT) }

        assertThat(vm.uiState.value.error).isEqualTo(StoreError.PENDING)
        assertThat(vm.uiState.value.access.ceiling).isEqualTo(Intensity.FLIRTY)
    }

    @Test
    fun `a pack the store does not have is not called a failed restore`() = test {
        val vm = screen()
        billing.failNext = AppError.Validation("not-for-sale")

        act { vm.buy(activity, Intensity.HOT) }

        assertThat(vm.uiState.value.error).isEqualTo(StoreError.NOT_FOR_SALE)
    }

    @Test
    fun `restoring finds what the store holds, or says there was nothing`() = test {
        val vm = screen()

        act(vm::restore)
        assertThat(vm.uiState.value.restoredNothing).isTrue()

        billing.holding = setOf(Intensity.EXTREME)
        act(vm::restore)

        assertThat(vm.uiState.value.restoredNothing).isFalse()
    }

    @Test
    fun `it asks the store on arrival, so a reinstalled phone finds its packs`() = test {
        billing.holding = setOf(Intensity.SPICY)

        screen()

        // Nobody had to know there is a Restore button for this to have happened.
        assertThat(billing.restores).isEqualTo(1)
        assertThat(billing.bought).isEmpty()
    }
}

/** Enough of an Activity to pass along. The fake store never touches it. */
private class ActivityStub : Activity()
