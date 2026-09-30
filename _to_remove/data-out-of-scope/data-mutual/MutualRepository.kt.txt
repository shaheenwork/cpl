package com.shnapps.couple.core.data.mutual

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.ApplicationScope
import com.shnapps.couple.core.common.Clock
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.firebase.mutual.MutualDataSource
import com.shnapps.couple.core.model.MutualMatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The couple's revealed matches (BUILD_PROMPT.md §5.2, §14.7).
 *
 * Only what the server has released. There is no way to ask what is pending, or what a
 * partner answered: the answer is never in a match, and pending matches are server-only.
 */
interface MutualRepository {
    /** Revealed matches, oldest first. Empty when not paired. */
    val matches: Flow<List<MutualMatch>>

    /** Records that the signed-in user has now had [itemId] revealed to them. */
    suspend fun markSeen(itemId: String): Outcome<Unit>

    /**
     * Releases anything that has already waited the minimum delay (§5.3). Safe to call on
     * every app open: calls within a few minutes of each other are skipped here, before they
     * cost a function invocation.
     */
    suspend fun releaseNow(): Outcome<Int>
}

@Singleton
class DefaultMutualRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val dataSource: MutualDataSource,
    private val clock: Clock,
    @ApplicationScope appScope: CoroutineScope,
) : MutualRepository {

    private val lastRelease = AtomicLong(NEVER)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val matches: Flow<List<MutualMatch>> = authRepository.currentProfile
        .map { profile -> profile?.let { it.uid to it.coupleId } }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            val (uid, coupleId) = ids ?: (null to null)
            if (uid == null || coupleId == null) {
                flowOf(emptyList())
            } else {
                // Refused mid-flight — at sign-out, or the instant a couple ends and the rules
                // stop letting either partner read it. End quietly (DECISIONS.md D-025).
                dataSource.observeMatches(coupleId, uid).catch { }
            }
        }
        .shareIn(appScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    override suspend fun markSeen(itemId: String): Outcome<Unit> {
        val profile = authRepository.currentProfile.first() ?: return Outcome.Failure(AppError.Unauthenticated())
        val coupleId = profile.coupleId ?: return Outcome.Failure(AppError.NotFound())
        return dataSource.markSeen(coupleId, itemId, profile.uid)
    }

    override suspend fun releaseNow(): Outcome<Int> {
        val now = clock.nowMillis()
        val last = lastRelease.get()
        if (last != NEVER && now - last < RELEASE_THROTTLE_MS) return Outcome.Success(0)
        lastRelease.set(now)
        return dataSource.releaseNow()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val NEVER = Long.MIN_VALUE
        const val RELEASE_THROTTLE_MS = 5 * 60 * 1_000L
    }
}
