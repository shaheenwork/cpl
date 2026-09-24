package com.shnapps.couple.feature.paywall

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.billing.BillingRepository
import com.shnapps.couple.core.data.billing.PremiumOffer
import com.shnapps.couple.core.model.Entitlement
import com.shnapps.couple.core.model.Gate
import com.shnapps.couple.core.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Why a purchase did not happen. "Cancelled" is not an error worth a banner. */
enum class PaywallError { OFFLINE, UNAVAILABLE, NOTHING_TO_RESTORE, OTHER }

data class PaywallUiState(
    /** The limit they ran into, if they arrived from one. */
    val gate: Gate? = null,
    val entitlement: Entitlement = Entitlement(),
    val offer: PremiumOffer? = null,
    val busy: Boolean = false,
    val error: PaywallError? = null,
) {
    val alreadyPremium: Boolean get() = entitlement.premium
}

/**
 * What premium includes (BUILD_PROMPT.md §19).
 *
 * Nothing here decides anything: it buys a token from the store and hands it to the server, which
 * decides what it is worth. The screen shows what the server then says.
 */
@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billing: BillingRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // By property name: toRoute() goes through Android's Bundle, which unit tests stub (D-067).
    private val gate: Gate? = Gate.of(savedStateHandle.get<String>(Route.Paywall::gate.name))

    private val local = MutableStateFlow(PaywallUiState(gate = gate))

    val uiState: StateFlow<PaywallUiState> = combine(local, billing.entitlement) { state, entitlement ->
        state.copy(entitlement = entitlement)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PaywallUiState(gate = gate))

    init {
        viewModelScope.launch {
            val offer = billing.offer()
            if (offer is Outcome.Success) local.update { it.copy(offer = offer.value) }
        }
    }

    fun subscribe(activity: Activity) = act { billing.subscribe(activity) }

    fun restore() = act { billing.restore() }

    fun dismissError() = local.update { it.copy(error = null) }

    private fun act(work: suspend () -> Outcome<Entitlement>) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = work()
            local.update { state ->
                when (result) {
                    is Outcome.Success -> state.copy(busy = false)
                    is Outcome.Failure -> state.copy(busy = false, error = result.error.toPaywallError())
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun AppError.toPaywallError(): PaywallError? = when {
    // Walking away from a purchase is not a failure, and is not worth saying anything about.
    this is AppError.Validation && code == "cancelled" -> null
    this is AppError.Network -> PaywallError.OFFLINE
    this is AppError.Validation && code == "billing-unavailable" -> PaywallError.UNAVAILABLE
    this is AppError.NotFound -> PaywallError.NOTHING_TO_RESTORE
    else -> PaywallError.OTHER
}
