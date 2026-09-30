package com.shnapps.couple.core.data.memories

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.firebase.memories.MemoryDoc
import com.shnapps.couple.core.firebase.memories.MemoryRemoteDataSource
import com.shnapps.couple.core.model.Memory
import com.shnapps.couple.core.model.MemoryKind
import com.shnapps.couple.core.model.Mood
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** A page of the library, and whether there is more behind it. */
data class MemoriesPage(val memories: List<Memory>, val hasMore: Boolean)

/**
 * The couple's own library (BUILD_PROMPT.md §14.8).
 *
 * Read a page at a time, newest first: a library is one of the few things in this app that only
 * grows, and a screen that read it whole would cost a read per night every time it opened (§18).
 */
interface MemoryRepository {
    /** The first page, or the one after [beforeMillis] — the date of the last one shown. */
    suspend fun page(beforeMillis: Long? = null): Outcome<MemoriesPage>

    /** Keeps the night [experienceId], in their words. Saving it again edits what they kept. */
    suspend fun save(
        experienceId: String,
        title: String,
        note: String?,
        favoriteMoment: String?,
        rating: Int?,
        mediaIds: List<String> = emptyList(),
    ): Outcome<String>

    suspend fun setFavorite(memoryId: String, favorite: Boolean): Outcome<Unit>

    suspend fun forget(memoryId: String): Outcome<Unit>
}

@Singleton
class DefaultMemoryRepository @Inject constructor(
    private val auth: AuthRepository,
    private val remote: MemoryRemoteDataSource,
) : MemoryRepository {

    override suspend fun page(beforeMillis: Long?): Outcome<MemoriesPage> {
        val coupleId = coupleId() ?: return Outcome.Failure(AppError.Unauthenticated())
        return when (val page = remote.page(coupleId, beforeMillis)) {
            is Outcome.Failure -> page
            is Outcome.Success -> Outcome.Success(
                MemoriesPage(page.value.memories.map { it.toMemory() }, page.value.hasMore),
            )
        }
    }

    override suspend fun save(
        experienceId: String,
        title: String,
        note: String?,
        favoriteMoment: String?,
        rating: Int?,
        mediaIds: List<String>,
    ): Outcome<String> = remote.save(experienceId, title, note, favoriteMoment, rating, mediaIds)

    override suspend fun setFavorite(memoryId: String, favorite: Boolean): Outcome<Unit> {
        val coupleId = coupleId() ?: return Outcome.Failure(AppError.Unauthenticated())
        return remote.setFavorite(coupleId, memoryId, favorite)
    }

    override suspend fun forget(memoryId: String): Outcome<Unit> = remote.forget(memoryId)

    private suspend fun coupleId(): String? = auth.currentProfile.map { it?.coupleId }.first()
}

private fun MemoryDoc.toMemory(): Memory = Memory(
    id = id,
    experienceId = experienceId,
    title = title,
    note = note,
    favoriteMoment = favoriteMoment,
    rating = rating,
    mood = mood.mapNotNull(Mood::fromId),
    durationMin = durationMin,
    kind = if (experienceType == "MYSTERY") MemoryKind.MYSTERY else MemoryKind.NIGHT,
    chapterCount = chapterCount,
    playedThrough = playedThrough,
    mediaIds = mediaIds,
    favorite = favorite,
    dateMillis = dateMillis,
)
