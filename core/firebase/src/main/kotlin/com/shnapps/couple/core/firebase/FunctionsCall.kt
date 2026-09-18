package com.shnapps.couple.core.firebase

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.FirebaseFunctionsException.Code
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Calls a Cloud Function and brings the result back across the data-layer boundary as an
 * [Outcome]: callable failures become typed [AppError]s, and nothing Firebase-shaped
 * escapes :core:firebase (BUILD_PROMPT.md §4.2).
 *
 * Catching broadly is the point of this function; cancellation is rethrown first so
 * structured concurrency still works.
 */
@Suppress("TooGenericExceptionCaught")
internal suspend inline fun <T> FirebaseFunctions.callFunction(
    name: String,
    payload: Any?,
    parse: (Map<*, *>) -> T,
): Outcome<T> = try {
    val result = getHttpsCallable(name).call(payload).await()
    Outcome.Success(parse(result.getData() as? Map<*, *> ?: emptyMap<String, Any>()))
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: FirebaseFunctionsException) {
    Outcome.Failure(error.toAppError())
} catch (error: Exception) {
    Outcome.Failure(AppError.Unknown(error))
}

/**
 * The server's reason string (`busy`, `own-code`, `already-paired`...) travels in the
 * message, and becomes the Validation code the UI maps to copy.
 */
internal fun FirebaseFunctionsException.toAppError(): AppError {
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
