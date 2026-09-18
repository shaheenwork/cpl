package com.shnapps.couple.core.data.couple

import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.model.InviteCode
import com.shnapps.couple.core.model.PairingState
import kotlinx.coroutines.flow.Flow

/**
 * Pairing and the couple (BUILD_PROMPT.md section 8).
 *
 * Every mutation goes through a Cloud Function transaction. Progress and outcomes are
 * observed through [pairingState] and [coupleId], which come from the user's own profile
 * document — so both phones converge on the same state no matter which one acted.
 */
interface CoupleRepository {
    val pairingState: Flow<PairingState?>
    val coupleId: Flow<String?>

    suspend fun createInviteCode(): Outcome<InviteCode>

    /** Returns the verification symbols to show while the creator decides. */
    suspend fun requestPairing(code: String): Outcome<List<String>>

    suspend fun respondToPairing(approve: Boolean): Outcome<Unit>

    suspend fun cancelPairing(): Outcome<Unit>

    suspend fun unpair(): Outcome<Unit>
}
