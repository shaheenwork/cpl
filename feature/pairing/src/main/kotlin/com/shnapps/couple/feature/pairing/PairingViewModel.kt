package com.shnapps.couple.feature.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.analytics.AnalyticsEvent
import com.shnapps.couple.core.analytics.AnalyticsLogger
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.couple.CoupleRepository
import com.shnapps.couple.core.data.couple.PendingInvite
import com.shnapps.couple.core.model.PairingRole
import com.shnapps.couple.core.model.PairingState
import com.shnapps.couple.core.model.PairingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the user is in pairing. Server state decides; local choice only fills the gaps. */
sealed interface PairingStep {
    /** Nothing in progress: invite, or enter a code. */
    data object Choose : PairingStep

    /** Creator: code issued, waiting for the partner to enter it. */
    data class Invite(val code: String, val expiresAtEpochMillis: Long?) : PairingStep

    /** Creator: someone entered the code. Confirm it is really the partner. */
    data class ConfirmPartner(val verification: List<String>) : PairingStep

    data object EnterCode : PairingStep

    /** Joiner: waiting for the creator to confirm. */
    data class Waiting(val verification: List<String>) : PairingStep

    /** Joiner: the creator declined, or withdrew the code. */
    data object Declined : PairingStep

    data object Paired : PairingStep
}

data class PairingUiState(
    val step: PairingStep = PairingStep.Choose,
    val codeInput: String = "",
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmitCode: Boolean get() = !isBusy && codeInput.length == CODE_LENGTH

    companion object {
        const val CODE_LENGTH = 6
    }
}

/**
 * Drives the pairing handshake (BUILD_PROMPT.md section 8).
 *
 * The step comes from the **server's** view of the handshake — the user's own profile
 * document — not from what this screen last did. So both phones converge on the same
 * truth, and a process death mid-handshake resumes exactly where it was.
 */
@HiltViewModel
class PairingViewModel @Inject constructor(
    private val couples: CoupleRepository,
    pendingInvite: PendingInvite,
    private val analytics: AnalyticsLogger,
) : ViewModel() {

    private val enteringCode = MutableStateFlow(false)
    private val form = MutableStateFlow(Form())

    private data class Form(val codeInput: String = "", val isBusy: Boolean = false, val error: String? = null)

    val uiState: StateFlow<PairingUiState> =
        combine(couples.pairingState, couples.coupleId, enteringCode, form) { pairing, coupleId, entering, form ->
            PairingUiState(
                step = stepFor(pairing, coupleId, entering),
                codeInput = form.codeInput,
                isBusy = form.isBusy,
                errorMessage = form.error,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PairingUiState())

    init {
        // A code that arrived by link before the user got here.
        pendingInvite.consume()?.let { code ->
            form.update { it.copy(codeInput = code) }
            enteringCode.value = true
        }
    }

    fun startInvite() = run { couples.createInviteCode() }

    fun startEnteringCode() {
        enteringCode.value = true
        form.update { it.copy(error = null) }
    }

    fun backToChoose() {
        enteringCode.value = false
        form.update { Form() }
    }

    fun onCodeChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(PairingUiState.CODE_LENGTH)
        form.update { it.copy(codeInput = digits, error = null) }
    }

    fun submitCode() {
        if (!uiState.value.canSubmitCode) return
        run { couples.requestPairing(form.value.codeInput) }
    }

    fun approve() = run(onSuccess = { analytics.log(AnalyticsEvent.CoupleCreated) }) {
        couples.respondToPairing(approve = true)
    }

    fun decline() = run { couples.respondToPairing(approve = false) }

    fun cancel() = run(onSuccess = { backToChoose() }) { couples.cancelPairing() }

    private fun <T> run(onSuccess: () -> Unit = {}, work: suspend () -> Outcome<T>) {
        if (form.value.isBusy) return
        form.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            when (val result = work()) {
                is Outcome.Success -> {
                    form.update { it.copy(isBusy = false) }
                    onSuccess()
                }
                is Outcome.Failure -> form.update { it.copy(isBusy = false, error = result.error.toMessage()) }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        fun stepFor(pairing: PairingState?, coupleId: String?, enteringCode: Boolean): PairingStep = when {
            coupleId != null -> PairingStep.Paired
            pairing?.role == PairingRole.CREATOR && pairing.status == PairingStatus.REQUESTED ->
                PairingStep.ConfirmPartner(pairing.verification)
            pairing?.role == PairingRole.CREATOR ->
                PairingStep.Invite(pairing.code, pairing.expiresAtEpochMillis)
            pairing?.role == PairingRole.JOINER && pairing.status == PairingStatus.DECLINED ->
                PairingStep.Declined
            pairing?.role == PairingRole.JOINER -> PairingStep.Waiting(pairing.verification)
            enteringCode -> PairingStep.EnterCode
            else -> PairingStep.Choose
        }
    }
}

/**
 * "No such code", "expired" and "already used" are one message on purpose: the server
 * does not distinguish them either, so a guesser learns nothing about which codes exist.
 */
private fun AppError.toMessage(): String = when (this) {
    is AppError.NotFound -> "That code didn't work. Check the digits, or ask for a new one."
    is AppError.RateLimited -> "Too many tries. Wait a few minutes and try again."
    is AppError.Network -> "No connection. Check your signal and try again."
    is AppError.Validation -> when (code) {
        "own-code" -> "That's your own code — your partner needs to enter it."
        "busy" -> "Someone is already using that code. Ask your partner for a new one."
        "already-paired" -> "You're already paired."
        "already-requesting" -> "You're already waiting on a code. Cancel it first."
        "expired" -> "That request timed out. Start again with a new code."
        else -> "That didn't work. Try again."
    }
    else -> "Something went wrong. Try again."
}
