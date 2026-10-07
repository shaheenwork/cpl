package com.shnapps.couple.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.account.AccountRepository
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.data.billing.BillingRepository
import com.shnapps.couple.core.data.couple.CoupleRepository
import com.shnapps.couple.core.data.entitlement.LevelAccess
import com.shnapps.couple.core.data.entitlement.LevelAccessRepository
import com.shnapps.couple.core.model.Entitlement
import com.shnapps.couple.core.security.DiscreetIcon
import com.shnapps.couple.core.security.DiscreetIcons
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
    /** Which packs this phone has, and how far they reach (V2 rule 5). */
    val access: LevelAccess = LevelAccess.FREE,
    val exporting: Boolean = false,
    val export: ExportSummary? = null,
    val busy: Boolean = false,
    val error: SettingsError? = null,
    /** Set once the account is gone: the screen leaves for the beginning. */
    val deleted: Boolean = false,
    val unpaired: Boolean = false,
    /** Which launcher icon is showing (M8). Read from the components, not from a stored guess. */
    val icon: DiscreetIcon = DiscreetIcon.DEFAULT,
)

/**
 * Settings (BUILD_PROMPT.md §14.9). Most rows are doors to screens that already exist; the three
 * that live here are the ones about the account itself — an export, unpairing, and deletion.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val account: AccountRepository,
    private val couples: CoupleRepository,
    private val icons: DiscreetIcons,
    auth: AuthRepository,
    billing: BillingRepository,
    levels: LevelAccessRepository,
) : ViewModel() {

    private val local = MutableStateFlow(SettingsUiState(icon = icons.current()))

    /**
     * Shows [icon] on the home screen (M8).
     *
     * Read back from the components rather than trusted: the platform is the record of which alias
     * is enabled, and a stored copy of it would be a second answer that could disagree.
     */
    fun setIcon(icon: DiscreetIcon) {
        icons.show(icon)
        local.update { it.copy(icon = icons.current()) }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        local,
        auth.currentProfile,
        auth.authState,
        billing.entitlement,
        levels.access,
    ) { state, profile, user, tier, access ->
        state.copy(
            email = user?.email,
            paired = profile?.coupleId != null,
            entitlement = tier,
            access = access,
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

    fun unpair() = act {
        when (val result = couples.unpair()) {
            is Outcome.Success -> {
                local.update { it.copy(unpaired = true) }
                result
            }
            is Outcome.Failure -> result
        }
    }

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
