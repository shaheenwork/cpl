package com.shnapps.couple.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Signing out (BUILD_PROMPT.md §7).
 *
 * The repository wipes device-local state as part of this — the app-lock PIN and routing
 * hints belong to the account that is leaving, and must not carry into the next person's
 * session on a shared phone.
 */
@HiltViewModel
class SignOutViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onSignedOut()
        }
    }
}
