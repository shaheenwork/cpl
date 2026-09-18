package com.shnapps.couple.core.testing

import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.couple.CoupleRepository
import com.shnapps.couple.core.model.InviteCode
import com.shnapps.couple.core.model.PairingRole
import com.shnapps.couple.core.model.PairingState
import com.shnapps.couple.core.model.PairingStatus
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [CoupleRepository] that plays the server's part: a successful call moves the
 * handshake state the way the Cloud Function would, so ViewModel tests exercise the real
 * reaction to state changes rather than to stubbed return values.
 */
class FakeCoupleRepository : CoupleRepository {

    val pairing = MutableStateFlow<PairingState?>(null)
    val couple = MutableStateFlow<String?>(null)

    var createResult: Outcome<InviteCode> = Outcome.Success(InviteCode(CODE, EXPIRES))
    var requestResult: Outcome<List<String>> = Outcome.Success(SYMBOLS)
    var respondResult: Outcome<Unit> = Outcome.Success(Unit)
    var cancelResult: Outcome<Unit> = Outcome.Success(Unit)
    var unpairResult: Outcome<Unit> = Outcome.Success(Unit)

    /** Every call, in order, e.g. "request:482913". */
    val calls = mutableListOf<String>()

    override val pairingState = pairing
    override val coupleId = couple

    override suspend fun createInviteCode(): Outcome<InviteCode> {
        calls += "create"
        return createResult.also { result ->
            if (result is Outcome.Success) {
                pairing.value = PairingState(
                    role = PairingRole.CREATOR,
                    status = PairingStatus.OPEN,
                    code = result.value.code,
                    expiresAtEpochMillis = result.value.expiresAtEpochMillis,
                )
            }
        }
    }

    override suspend fun requestPairing(code: String): Outcome<List<String>> {
        calls += "request:$code"
        return requestResult.also { result ->
            if (result is Outcome.Success) {
                pairing.value = PairingState(PairingRole.JOINER, PairingStatus.WAITING, code, result.value)
            }
        }
    }

    override suspend fun respondToPairing(approve: Boolean): Outcome<Unit> {
        calls += "respond:$approve"
        return respondResult.also { result ->
            if (result is Outcome.Success) {
                pairing.value = null
                if (approve) couple.value = COUPLE_ID
            }
        }
    }

    override suspend fun cancelPairing(): Outcome<Unit> {
        calls += "cancel"
        return cancelResult.also { if (it is Outcome.Success) pairing.value = null }
    }

    override suspend fun unpair(): Outcome<Unit> {
        calls += "unpair"
        return unpairResult.also { if (it is Outcome.Success) couple.value = null }
    }

    /** The creator's side of the handshake, once someone has entered their code. */
    fun partnerEnteredCode(symbols: List<String> = SYMBOLS) {
        pairing.value = PairingState(PairingRole.CREATOR, PairingStatus.REQUESTED, CODE, symbols, EXPIRES)
    }

    companion object {
        const val CODE = "482913"
        const val EXPIRES = 1_000_000L
        const val COUPLE_ID = "couple_1"
        val SYMBOLS = listOf("🌙", "🍷", "🔥")
    }
}
