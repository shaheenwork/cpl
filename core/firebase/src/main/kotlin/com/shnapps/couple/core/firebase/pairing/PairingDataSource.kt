package com.shnapps.couple.core.firebase.pairing

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.FirebaseFunctionsException.Code
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.model.InviteCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
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

    // Same exception-boundary pattern as the other data sources: this is where callable
    // failures become typed AppErrors.
    @Suppress("TooGenericExceptionCaught")
    private suspend inline fun <T> call(
        name: String,
        payload: Any?,
        parse: (Map<*, *>) -> T,
    ): Outcome<T> = try {
        val result = functions.getHttpsCallable(name).call(payload).await()
        Outcome.Success(parse(result.getData() as? Map<*, *> ?: emptyMap<String, Any>()))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: FirebaseFunctionsException) {
        Outcome.Failure(error.toAppError())
    } catch (error: Exception) {
        Outcome.Failure(AppError.Unknown(error))
    }
}

/**
 * The server's reason string (`busy`, `own-code`, `already-paired`...) travels in the
 * message, and becomes the Validation code the UI maps to copy.
 */
private fun FirebaseFunctionsException.toAppError(): AppError {
    val reason = message.orEmpty()
    return when (code) {
        Code.NOT_FOUND -> AppError.NotFound(this)
        Code.FAILED_PRECONDITION, Code.INVALID_ARGUMENT -> AppError.Validation(reason, this)
        Code.RESOURCE_EXHAUSTED -> AppError.RateLimited(this)
        Code.UNAUTHENTICATED -> AppError.Unauthenticated(this)
        Code.PERMISSION_DENIED -> AppError.PermissionDenied(this)
        Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED -> AppError.Network(this)
        else -> AppError.Unknown(this)
    }
}
