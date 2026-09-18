package com.shnapps.couple.core.firebase.pairing

import com.google.firebase.functions.FirebaseFunctions
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.callFunction
import com.shnapps.couple.core.model.InviteCode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The pairing callables (BUILD_PROMPT.md section 8). Every state change in pairing goes
 * through Cloud Functions, inside transactions; nothing here writes Firestore directly.
 *
 * Results of the handshake arrive on the user's own profile document (`pairing`, then
 * `coupleId`), which the app already listens to — so these calls only need to report
 * success or a typed failure.
 */
@Singleton
class PairingDataSource @Inject constructor(
    private val functions: FirebaseFunctions,
) {
    suspend fun createPairingCode(): Outcome<InviteCode> = call("createPairingCode", null) { data ->
        InviteCode(
            code = data["code"] as String,
            expiresAtEpochMillis = (data["expiresAtMs"] as Number).toLong(),
        )
    }

    /** Returns the verification symbols both phones now show. */
    suspend fun requestPairing(code: String): Outcome<List<String>> =
        call("requestPairing", mapOf("code" to code)) { data ->
            (data["verification"] as List<*>).filterIsInstance<String>()
        }

    suspend fun respondToPairing(approve: Boolean): Outcome<Unit> =
        call("respondToPairing", mapOf("approve" to approve)) { }

    suspend fun cancelPairing(): Outcome<Unit> = call("cancelPairing", null) { }

    suspend fun unpairCouple(): Outcome<Unit> = call("unpairCouple", mapOf("confirm" to true)) { }

    private suspend inline fun <T> call(name: String, payload: Any?, parse: (Map<*, *>) -> T): Outcome<T> =
        functions.callFunction(name, payload, parse)
}
