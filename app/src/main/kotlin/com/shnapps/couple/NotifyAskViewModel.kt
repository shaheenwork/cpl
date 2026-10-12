package com.shnapps.couple

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.data.notifications.NotificationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Whether it is time to ask for permission to notify (D-151): once the two of them are paired, and
 * once only.
 *
 * It used to be asked the first time the app opened, to someone with nobody to send them anything.
 * A permission is asked for at the moment it is worth something, and that is when there is a
 * partner whose message, game or chat it is for.
 */
@HiltViewModel
class NotifyAskViewModel @Inject constructor(
    private val notifications: NotificationsRepository,
) : ViewModel() {

    val shouldAsk: StateFlow<Boolean> = notifications.shouldAskPermission
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    fun asked() {
        viewModelScope.launch { notifications.permissionAsked() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
