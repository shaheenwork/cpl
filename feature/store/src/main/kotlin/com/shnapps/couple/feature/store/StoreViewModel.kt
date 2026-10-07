package com.shnapps.couple.feature.store

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.billing.BillingRepository
import com.shnapps.couple.core.data.billing.PackOffer
import com.shnapps.couple.core.data.entitlement.LevelAccess
import com.shnapps.couple.core.data.entitlement.LevelAccessRepository
import com.shnapps.couple.core.model.Intensity
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
enum class StoreError { OFFLINE, UNAVAILABLE, NOTHING_TO_RESTORE, OTHER }

data class StoreUiState(
    /** The level they tapped to get here, so its pack is the one in front. */
    val asked: Intensity? = null,
    val access: LevelAccess = LevelAccess.FREE,
    /** What the store charges, when it has said. Missing prices are left blank, never guessed. */
    val offers: List<PackOffer> = emptyList(),
    val busy: Boolean = false,
    val error: StoreError? = null,
    /** Set after a restore that found nothing, so the screen can say so once. */
    val restoredNothing: Boolean = false,
) {
    /** The packs, in order, with what each costs here. */
    val packs: List<Pack>
        get() = LevelAccess.PACKS.map { level ->
            Pack(
                level = level,
                price = offers.firstOrNull { it.level == level }?.price,
                owned = access.isOwned(level),
            )
        }

    /** Nothing left to sell: they own the top pack, or the free run has not ended yet. */
    val everythingOpen: Boolean get() = !access.anythingLocked

    data class Pack(val level: Intensity, val price: String?, val owned: Boolean)
}

/**
 * The packs, and what they cost (V2 rule 5).
 *
 * Three products and nothing else on the screen: no countdown, no crossed-out price, no fourth
 * tile that is really the third one again. Each pack opens the levels below it as well, which is
 * said in words rather than implied by the order, because that is the one fact a couple needs
 * before they pay and the one most stores leave for afterwards.
 *
 * It never sells what they already have, and it never sells anything at all while the opening
 * month is running — a store that takes money for something already unlocked is a store nobody
 * should trust with the rest of this app.
 */
@HiltViewModel
class StoreViewModel @Inject constructor(
    private val billing: BillingRepository,
    levels: LevelAccessRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // By property name: toRoute() goes through Android's Bundle, which unit tests stub (D-067).
    private val asked: Intensity? = savedStateHandle.get<String>(Route.Store::level.name)
        ?.let { name -> Intensity.entries.firstOrNull { it.name == name } }

    private val local = MutableStateFlow(StoreUiState(asked = asked))

    val uiState: StateFlow<StoreUiState> = combine(local, levels.access) { state, access ->
        state.copy(access = access)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StoreUiState(asked = asked))

    init {
        viewModelScope.launch {
            val offers = billing.offers()
            if (offers is Outcome.Success) local.update { it.copy(offers = offers.value) }
        }
        // Asked on arrival, so a phone that has been reinstalled or had its data cleared finds its
        // packs again without anybody having to know there is a button for it.
        viewModelScope.launch { billing.restore() }
    }

    fun buy(activity: Activity, level: Intensity) = act { billing.buy(activity, level) }

    fun restore() = act { billing.restore() }

    fun dismissError() = local.update { it.copy(error = null, restoredNothing = false) }

    private fun act(work: suspend () -> Outcome<Set<Intensity>>) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, error = null, restoredNothing = false) }
        viewModelScope.launch {
            val result = work()
            local.update { state ->
                when (result) {
                    is Outcome.Success -> state.copy(busy = false, restoredNothing = result.value.isEmpty())
                    is Outcome.Failure -> state.copy(busy = false, error = result.error.toStoreError())
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun AppError.toStoreError(): StoreError? = when {
    // Walking away from a purchase is not a failure, and is not worth saying anything about.
    this is AppError.Validation && code == "cancelled" -> null
    this is AppError.Network -> StoreError.OFFLINE
    this is AppError.Validation && code == "billing-unavailable" -> StoreError.UNAVAILABLE
    this is AppError.NotFound -> StoreError.NOTHING_TO_RESTORE
    else -> StoreError.OTHER
}
