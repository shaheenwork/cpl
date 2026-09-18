package com.shnapps.couple.core.data.couple

import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
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
) : CoupleRepository {

    // Both derive from the one profile listener the app already holds, rather than opening
    // new ones: a listener per concern is how Firestore costs creep (section 75).
    override val pairingState: Flow<PairingState?> =
        authRepository.currentProfile.map { it?.pairing }.distinctUntilChanged()

    override val coupleId: Flow<String?> =
        authRepository.currentProfile.map { it?.coupleId }.distinctUntilChanged()

    override suspend fun createInviteCode(): Outcome<InviteCode> = pairing.createPairingCode()

    override suspend fun requestPairing(code: String): Outcome<List<String>> = pairing.requestPairing(code)

    override suspend fun respondToPairing(approve: Boolean): Outcome<Unit> = pairing.respondToPairing(approve)

    override suspend fun cancelPairing(): Outcome<Unit> = pairing.cancelPairing()

    override suspend fun unpair(): Outcome<Unit> = pairing.unpairCouple()
}
