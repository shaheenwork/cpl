package com.shnapps.couple.core.testing

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.memories.MemoriesPage
import com.shnapps.couple.core.data.memories.MemoryRepository
import com.shnapps.couple.core.model.Memory
import com.shnapps.couple.core.model.MemoryKind

/**
 * In-memory [MemoryRepository] that pages the way the server does: newest first, twenty at a
 * time, and one memory per night.
 */
class FakeMemoryRepository(initial: List<Memory> = emptyList()) : MemoryRepository {

    val kept = initial.toMutableList()
    var failNext: AppError? = null
    var pagesAsked = 0
        private set

    override suspend fun page(beforeMillis: Long?): Outcome<MemoriesPage> {
        failNext?.let {
            failNext = null
            return Outcome.Failure(it)
        }
        pagesAsked += 1
        val ordered = kept.sortedByDescending { it.dateMillis }
        val after = if (beforeMillis == null) ordered else ordered.filter { it.dateMillis < beforeMillis }
        return Outcome.Success(MemoriesPage(after.take(PAGE), after.size > PAGE))
    }

    override suspend fun save(
        experienceId: String,
        title: String,
        note: String?,
        favoriteMoment: String?,
        rating: Int?,
        mediaIds: List<String>,
    ): Outcome<String> {
        failNext?.let {
            failNext = null
            return Outcome.Failure(it)
        }
        kept.removeAll { it.experienceId == experienceId }
        kept += Memory(
            id = experienceId,
            experienceId = experienceId,
            title = title,
            note = note,
            favoriteMoment = favoriteMoment,
            rating = rating,
            mood = emptyList(),
            durationMin = 60,
            kind = MemoryKind.NIGHT,
            chapterCount = 4,
            playedThrough = 4,
            mediaIds = mediaIds,
            favorite = false,
            dateMillis = DATE,
        )
        return Outcome.Success(experienceId)
    }

    override suspend fun setFavorite(memoryId: String, favorite: Boolean): Outcome<Unit> {
        failNext?.let {
            failNext = null
            return Outcome.Failure(it)
        }
        val index = kept.indexOfFirst { it.id == memoryId }
        if (index >= 0) kept[index] = kept[index].copy(favorite = favorite)
        return Outcome.Success(Unit)
    }

    override suspend fun forget(memoryId: String): Outcome<Unit> {
        kept.removeAll { it.id == memoryId }
        return Outcome.Success(Unit)
    }

    private companion object {
        const val PAGE = 20
        const val DATE = 1_790_000_000_000L
    }
}
