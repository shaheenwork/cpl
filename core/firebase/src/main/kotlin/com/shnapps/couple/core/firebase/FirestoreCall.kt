package com.shnapps.couple.core.firebase

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import kotlinx.coroutines.CancellationException

/**
 * The exception boundary for Firestore calls: every failure leaves :core:firebase as a
 * typed [AppError], never as a Firebase exception (BUILD_PROMPT.md §4.2).
 *
 * Catching broadly is the point of this function. Narrowing it would let unknown failures
 * escape untyped; cancellation is rethrown first so structured concurrency still works.
 */
@Suppress("TooGenericExceptionCaught")
internal inline fun <T> firestoreCall(block: () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: FirebaseFirestoreException) {
    Outcome.Failure(error.toAppError())
} catch (error: Exception) {
    Outcome.Failure(AppError.Unknown(error))
}

internal fun FirebaseFirestoreException.toAppError(): AppError = when (code) {
    Code.PERMISSION_DENIED -> AppError.PermissionDenied(this)
    Code.UNAUTHENTICATED -> AppError.Unauthenticated(this)
    Code.NOT_FOUND -> AppError.NotFound(this)
    Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED -> AppError.Network(this)
    Code.RESOURCE_EXHAUSTED -> AppError.RateLimited(this)
    else -> AppError.Unknown(this)
}
