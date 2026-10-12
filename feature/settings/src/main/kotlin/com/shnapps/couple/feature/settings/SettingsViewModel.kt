package com.shnapps.couple.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.account.AccountRepository
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.data.billing.BillingRepository
import com.shnapps.couple.core.data.couple.CoupleRepository
import com.shnapps.couple.core.model.AppLockMode
import com.shnapps.couple.core.model.Entitlement
import com.shnapps.couple.core.security.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Why something on this screen did not happen. */
enum class SettingsError { OFFLINE, OTHER }

/** What an export left on this phone. */
data class ExportSummary(val fileName: String, val bytes: Long)

data class SettingsUiState(
    val email: String? = null,
    val paired: Boolean = false,
    val entitlement: Entitlement = Entitlement(),
    /** Whether the app lock is on, and how it opens: the one fact its row shows before it is opened. */
    val lock: AppLockMode = AppLockMode.OFF,
    val exporting: Boolean = false,
    val export: ExportSummary? = null,
    val busy: Boolean = false,
    val error: SettingsError? = null,
    /** Set once the account is gone: the screen leaves for the beginning. */
    val deleted: Boolean = false,
    /** The question before unpairing is on screen: the pair is not touched until it is answered. */
    val confirmingUnpair: Boolean = false,
    /**
     * The pair has just been ended, from this screen. Said once, in words, until it is dismissed or
     * they set about pairing again — because the only other sign was a row that quietly changed.
     */
    val unpaired: Boolean = false,
)

/**
 * Settings (BUILD_PROMPT.md §14.9). Most rows are doors to screens that already exist; the three
 * that live here are the ones about the account itself — an export, unpairing, and deletion.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val account: AccountRepository,
    private val couples: CoupleRepository,
    appLock: AppLockManager,
    auth: AuthRepository,
    billing: BillingRepository,
) : ViewModel() {

    private val local = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(
        local,
        auth.currentProfile,
        auth.authState,
        billing.entitlement,
        appLock.lockMode,
    ) { state, profile, user, tier, lock ->
        state.copy(
            email = user?.email,
            // The profile catches up with an unpairing a moment after the call returns. Until it
            // does, the screen has been told the pair is gone, and says so rather than disagreeing.
            paired = profile?.coupleId != null && !state.unpaired,
            entitlement = tier,
            lock = lock,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    /** "Everything we hold about you, as a file." Written by the server, fetched as this user. */
    fun export(into: File) {
        if (local.value.exporting) return
        local.update { it.copy(exporting = true, error = null, export = null) }
        viewModelScope.launch {
            val result = account.export(into)
            local.update { state ->
                when (result) {
                    is Outcome.Success -> state.copy(
                        exporting = false,
                        export = ExportSummary(result.value.file.name, result.value.bytes),
                    )
                    is Outcome.Failure -> state.copy(exporting = false, error = result.error.toSettingsError())
                }
            }
        }
    }

    /** The question, first: unpairing clears the pair on both phones, and a stray tap must not. */
    fun askUnpair() = local.update { it.copy(confirmingUnpair = true, error = null) }

    fun keepPair() = local.update { it.copy(confirmingUnpair = false) }

    /** Unpairs for real. Only the confirmation calls this; the row never does. */
    fun unpair() {
        if (local.value.busy) return
        local.update { it.copy(confirmingUnpair = false) }
        act {
            when (val result = couples.unpair()) {
                is Outcome.Success -> {
                    local.update { it.copy(unpaired = true) }
                    result
                }
                is Outcome.Failure -> result
            }
        }
    }

    /** They have read it, or are about to pair again: either way it has said what it had to. */
    fun dismissUnpaired() = local.update { it.copy(unpaired = false) }

    /** Deletes the account, the couple, and everything either of them made (D-093). */
    fun deleteAccount() = act {
        when (val result = account.deleteAccount()) {
            is Outcome.Success -> {
                local.update { it.copy(deleted = true) }
                result
            }
            is Outcome.Failure -> result
        }
    }

    fun dismissError() = local.update { it.copy(error = null) }

    fun dismissExport() = local.update { it.copy(export = null) }

    private fun act(work: suspend () -> Outcome<*>) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = work()
            local.update { state ->
                when (result) {
                    is Outcome.Success -> state.copy(busy = false)
                    is Outcome.Failure -> state.copy(busy = false, error = result.error.toSettingsError())
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun AppError.toSettingsError(): SettingsError =
    if (this is AppError.Network) SettingsError.OFFLINE else SettingsError.OTHER
