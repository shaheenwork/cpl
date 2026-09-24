package com.shnapps.couple.core.data.surprise

import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.firebase.surprise.ArcDoc
import com.shnapps.couple.core.firebase.surprise.ArcRemoteDataSource
import com.shnapps.couple.core.model.Arc
import com.shnapps.couple.core.model.ArcDay
import com.shnapps.couple.core.model.ArcDayState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A few days of this (BUILD_PROMPT.md §13): one, three, seven or fourteen days, a night each.
 *
 * The phone holds none of the rules. Which day is open is the server's answer, and a day's night
 * is built the moment it is opened — never before, so nothing is generated for a day the couple
 * may never reach.
 */
interface ArcRepository {
    /** The arc they are in, if they are in one. */
    val arc: Flow<Arc?>

    suspend fun start(lengthDays: Int): Outcome<String>

    /** Opens [dayIndex] and returns the night to play. Refused when it is not that day's turn. */
    suspend fun openDay(arcId: String, dayIndex: Int): Outcome<String>

    suspend fun end(arcId: String): Outcome<Unit>
}

@Singleton
class DefaultArcRepository @Inject constructor(
    auth: AuthRepository,
    private val remote: ArcRemoteDataSource,
) : ArcRepository {

    private val coupleIds: Flow<String?> = auth.currentProfile
        .map { it?.coupleId }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val arc: Flow<Arc?> = coupleIds.flatMapLatest { coupleId ->
        if (coupleId == null) {
            flowOf(null)
        } else {
            remote.observe(coupleId)
                .map { docs -> docs.map { it.toArc() }.firstOrNull { it.active } }
                .catch { emit(null) }
        }
    }

    override suspend fun start(lengthDays: Int): Outcome<String> = remote.start(lengthDays)

    override suspend fun openDay(arcId: String, dayIndex: Int): Outcome<String> = remote.openDay(arcId, dayIndex)

    override suspend fun end(arcId: String): Outcome<Unit> = remote.close(arcId)
}

private fun ArcDoc.toArc(): Arc = Arc(
    id = id,
    title = title,
    lengthDays = lengthDays,
    active = state == "ACTIVE",
    days = days.map { day ->
        ArcDay(
            index = day.index,
            title = day.title,
            state = ArcDayState.entries.firstOrNull { it.name == day.state } ?: ArcDayState.LOCKED,
            unlockAtMillis = day.unlockAtMillis,
            experienceId = day.experienceId,
        )
    },
)
