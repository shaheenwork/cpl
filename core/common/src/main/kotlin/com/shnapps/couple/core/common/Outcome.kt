package com.shnapps.couple.core.common

import kotlin.coroutines.cancellation.CancellationException

/**
 * The result type that crosses every layer boundary. Repositories return [Outcome];
 * exceptions do not propagate out of the data layer (BUILD_PROMPT.md §4.1).
 */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>

    fun getOrNull(): T? = (this as? Success)?.value

    fun <R> map(transform: (T) -> R): Outcome<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }
}

/** Typed failures. Deliberately closed so that UI can exhaustively handle every case. */
sealed interface AppError {
    val cause: Throwable?

    data class Network(override val cause: Throwable? = null) : AppError
    data class Unauthenticated(override val cause: Throwable? = null) : AppError
    data class PermissionDenied(override val cause: Throwable? = null) : AppError
    data class NotFound(override val cause: Throwable? = null) : AppError
    data class Conflict(override val cause: Throwable? = null) : AppError
    data class RateLimited(override val cause: Throwable? = null) : AppError
    data class Unknown(override val cause: Throwable? = null) : AppError
}

/**
 * Wraps a call at the data-layer boundary so that no exception escapes into the domain.
 *
 * Catching broadly is deliberate here and confined to this one function: it is the seam
 * where third-party failures (Firebase, IO, serialization) become typed [AppError]s.
 * Cancellation is rethrown first so that structured concurrency still works.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> outcomeOf(block: () -> T): Outcome<T> =
    try {
        Outcome.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Outcome.Failure(AppError.Unknown(error))
    }
