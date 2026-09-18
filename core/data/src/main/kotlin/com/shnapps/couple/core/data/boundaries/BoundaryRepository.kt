package com.shnapps.couple.core.data.boundaries

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.ApplicationScope
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.firebase.boundaries.BoundaryDataSource
import com.shnapps.couple.core.model.Boundary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed-in user's private boundaries (BUILD_PROMPT.md §3.2, §5.1).
 *
 * Only ever the user's own. The couple's combined filters are built by the server from both
 * partners' boundaries and no client can read them (§5.4), so there is deliberately no way to
 * ask this repository what a partner has set.
 */
interface BoundaryRepository {
    /** The user's own boundaries, keyed by taxonomy theme id. Empty when signed out. */
    val boundaries: Flow<Map<String, Boundary>>

    suspend fun setBoundary(themeId: String, boundary: Boundary): Outcome<Unit>

    /** Removes the boundary: the theme goes back to unrestricted. */
    suspend fun clearBoundary(themeId: String): Outcome<Unit>
}

@Singleton
class DefaultBoundaryRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val dataSource: BoundaryDataSource,
    @ApplicationScope appScope: CoroutineScope,
) : BoundaryRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val boundaries: Flow<Map<String, Boundary>> = authRepository.authState
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyMap())
            } else {
                // Refused mid-flight (typically during sign-out): end quietly; the auth
                // change that follows starts the next listener (DECISIONS.md D-025).
                dataSource.observeBoundaries(user.uid).catch { }
            }
        }
        .shareIn(appScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    override suspend fun setBoundary(themeId: String, boundary: Boundary): Outcome<Unit> {
        val uid = authRepository.authState.first()?.uid ?: return Outcome.Failure(AppError.Unauthenticated())
        return dataSource.setBoundary(uid, themeId, boundary)
    }

    override suspend fun clearBoundary(themeId: String): Outcome<Unit> {
        val uid = authRepository.authState.first()?.uid ?: return Outcome.Failure(AppError.Unauthenticated())
        return dataSource.clearBoundary(uid, themeId)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
