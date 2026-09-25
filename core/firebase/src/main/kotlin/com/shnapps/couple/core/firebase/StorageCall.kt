package com.shnapps.couple.core.firebase

import com.google.firebase.storage.StorageException
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import kotlinx.coroutines.CancellationException

/**
 * The Storage boundary (BUILD_PROMPT.md §4.2): every failure becomes an [AppError] and nothing
 * Firebase-shaped escapes this module.
 *
 * Catching broadly is the point of this function; cancellation is rethrown first so structured
 * concurrency still works.
 */
@Suppress("TooGenericExceptionCaught")
internal inline fun <T> storageCall(work: () -> T): Outcome<T> = try {
    Outcome.Success(work())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: StorageException) {
    Outcome.Failure(
        when (error.errorCode) {
            StorageException.ERROR_OBJECT_NOT_FOUND -> AppError.NotFound(error)
            StorageException.ERROR_NOT_AUTHENTICATED -> AppError.Unauthenticated(error)
            StorageException.ERROR_NOT_AUTHORIZED -> AppError.PermissionDenied(error)
            else -> AppError.Network(error)
        },
    )
} catch (error: Throwable) {
    Outcome.Failure(AppError.Unknown(error))
}
