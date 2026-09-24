package com.shnapps.couple.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.notifications.NotificationsRepository
import com.shnapps.couple.core.model.NotificationSettings
import com.shnapps.couple.core.model.QuietHours
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationSettingsUiState(
    val settings: NotificationSettings = NotificationSettings(),
    val busy: Boolean = false,
    val failed: Boolean = false,
) {
    val quietHoursOn: Boolean get() = settings.quietHours != null
}

/**
 * The three notification choices of §16.2, and quiet hours.
 *
 * They are kept on the server because the server is what decides whether to send: a phone that
 * simply refused to show something would still have had it delivered, and a lock screen is not a
 * place to correct a mistake after the fact.
 */
@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val notifications: NotificationsRepository,
) : ViewModel() {

    private val local = MutableStateFlow(NotificationSettingsUiState())

    val uiState: StateFlow<NotificationSettingsUiState> =
        combine(local, notifications.settings) { state, settings ->
            state.copy(settings = settings)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NotificationSettingsUiState())

    fun setEnabled(enabled: Boolean) = save(uiState.value.settings.copy(enabled = enabled))

    /** Never "show content": there is none in a payload. Whether it may name the kind of thing. */
    fun setShowContent(show: Boolean) = save(uiState.value.settings.copy(showContent = show))

    fun setQuietHours(on: Boolean) =
        save(uiState.value.settings.copy(quietHours = if (on) QuietHours.NIGHT else null))

    fun dismissError() = local.update { it.copy(failed = false) }

    private fun save(settings: NotificationSettings) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, failed = false) }
        viewModelScope.launch {
            val result = notifications.update(settings)
            local.update { it.copy(busy = false, failed = result is Outcome.Failure) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
