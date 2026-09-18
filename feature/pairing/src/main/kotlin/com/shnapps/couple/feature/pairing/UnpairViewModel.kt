package com.shnapps.couple.feature.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.couple.CoupleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ends the couple for both partners (BUILD_PROMPT.md section 8). Settings owns this from Phase 20. */
@HiltViewModel
class UnpairViewModel @Inject constructor(
    private val couples: CoupleRepository,
) : ViewModel() {

    fun unpair(onUnpaired: () -> Unit) {
        viewModelScope.launch {
            if (couples.unpair() is Outcome.Success) onUnpaired()
        }
    }
}
