package com.shnapps.couple.feature.night

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.data.night.NightRepository
import com.shnapps.couple.core.model.NightSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The couple's night under way, for whichever phone opens the app (D-059). */
@HiltViewModel
class ActiveNightViewModel @Inject constructor(nights: NightRepository) : ViewModel() {
    val active: StateFlow<NightSession?> =
        nights.activeSession.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
