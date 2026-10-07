package com.shnapps.couple.core.data.couple

import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.data.identity.Identity
import com.shnapps.couple.core.firebase.pairing.PairingDataSource
import com.shnapps.couple.core.model.InviteCode
import com.shnapps.couple.core.model.PairingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultCoupleRepository @Inject constructor(
    authRepository: AuthRepository,
    private val pairing: PairingDataSource,
    private val identity: Identity,
) : CoupleRepository {

    // Both derive from the one profile listener the app already holds, rather than opening
    // new ones: a listener per concern is how Firestore costs creep (section 75).
    override val pairingState: Flow<PairingState?> =
        authRepository.currentProfile.map { it?.pairing }.distinctUntilChanged()

    override val coupleId: Flow<String?> =
        authRepository.currentProfile.map { it?.coupleId }.distinctUntilChanged()

    /**
     * Both halves of the handshake make an identity first, and they are the only things that do.
     *
     * In v2 nobody signs in, so a phone arrives here with no uid at all — and the server needs one
     * to hang a pair on. It is minted here rather than at launch, so a phone that only ever plays
     * together never authenticates and never appears in the project (V2 rule 1). It is minted in
     * the repository rather than in the screen so that nothing can reach the server without one by
     * forgetting to ask.
     */
    override suspend fun createInviteCode(): Outcome<InviteCode> =
        withIdentity { pairing.createPairingCode() }

    override suspend fun requestPairing(code: String): Outcome<List<String>> =
        withIdentity { pairing.requestPairing(code) }

    private suspend fun <T> withIdentity(work: suspend () -> Outcome<T>): Outcome<T> =
        when (val ready = identity.ensureServerIdentity()) {
            is Outcome.Success -> work()
            is Outcome.Failure -> Outcome.Failure(ready.error)
        }

    override suspend fun respondToPairing(approve: Boolean): Outcome<Unit> = pairing.respondToPairing(approve)

    override suspend fun cancelPairing(): Outcome<Unit> = pairing.cancelPairing()

    override suspend fun unpair(): Outcome<Unit> = pairing.unpairCouple()
}
