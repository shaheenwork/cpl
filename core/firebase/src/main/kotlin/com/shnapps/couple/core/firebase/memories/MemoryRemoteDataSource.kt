package com.shnapps.couple.core.firebase.memories

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.callFunction
import com.shnapps.couple.core.firebase.firestoreCall
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** A memory as the server holds it (§14.8). Never a content id. */
data class MemoryDoc(
    val id: String,
    val experienceId: String,
    val title: String,
    val note: String?,
    val favoriteMoment: String?,
    val rating: Int?,
    val mood: List<String>,
    val durationMin: Int,
    val experienceType: String,
    val chapterCount: Int,
    val playedThrough: Int,
    val mediaIds: List<String>,
    val favorite: Boolean,
    val dateMillis: Long,
)

/** One page of the library, and where the next one starts. */
data class MemoryPage(val memories: List<MemoryDoc>, val lastDateMillis: Long?, val hasMore: Boolean)

/**
 * The couple's library (BUILD_PROMPT.md §6.1, §14.8).
 *
 * Read in pages of twenty rather than all at once (§18): a library grows for as long as a couple
 * lasts, and a screen that read it whole would cost a read per night, every time it opened.
 *
 * Keeping and forgetting are callables, because what a memory says about a night — its length,
 * its moods, that it was played at all — is the server's to fill in. Marking one a favourite is
 * the single field a phone writes.
 */
interface MemoryRemoteDataSource {
    suspend fun page(coupleId: String, beforeMillis: Long?): Outcome<MemoryPage>

    suspend fun save(
        experienceId: String,
        title: String,
        note: String?,
        favoriteMoment: String?,
        rating: Int?,
        mediaIds: List<String>,
    ): Outcome<String>

    suspend fun setFavorite(coupleId: String, memoryId: String, favorite: Boolean): Outcome<Unit>

    suspend fun forget(memoryId: String): Outcome<Unit>
}

@Singleton
class FirebaseMemoryRemoteDataSource @Inject constructor(
    private val functions: FirebaseFunctions,
    private val firestore: FirebaseFirestore,
) : MemoryRemoteDataSource {

    override suspend fun page(coupleId: String, beforeMillis: Long?): Outcome<MemoryPage> = firestoreCall {
        var query: Query = firestore.collection("couples").document(coupleId).collection("memories")
            .orderBy("date", Query.Direction.DESCENDING)
        if (beforeMillis != null) {
            // To the nanosecond: two nights kept in the same second must not push one off a page.
            query = query.startAfter(
                Timestamp(beforeMillis / MILLIS, ((beforeMillis % MILLIS) * NANOS_PER_MILLI).toInt()),
            )
        }
        val snapshot = query.limit(PAGE_SIZE + 1).get().await()
        val docs = snapshot.documents.mapNotNull { it.toMemory() }
        val page = docs.take(PAGE_SIZE.toInt())
        MemoryPage(
            memories = page,
            lastDateMillis = page.lastOrNull()?.dateMillis,
            hasMore = docs.size > page.size,
        )
    }

    override suspend fun save(
        experienceId: String,
        title: String,
        note: String?,
        favoriteMoment: String?,
        rating: Int?,
        mediaIds: List<String>,
    ): Outcome<String> {
        val payload = buildMap<String, Any> {
            put("experienceId", experienceId)
            put("title", title)
            note?.let { put("note", it) }
            favoriteMoment?.let { put("favoriteMoment", it) }
            rating?.let { put("rating", it) }
            if (mediaIds.isNotEmpty()) put("mediaIds", mediaIds)
        }
        return functions.callFunction("saveMemory", payload) { it["memoryId"] as String }
    }

    override suspend fun setFavorite(coupleId: String, memoryId: String, favorite: Boolean): Outcome<Unit> =
        firestoreCall {
            firestore.collection("couples").document(coupleId).collection("memories").document(memoryId)
                .update("favorite", favorite).await()
            Unit
        }

    override suspend fun forget(memoryId: String): Outcome<Unit> =
        functions.callFunction("forgetMemory", mapOf("memoryId" to memoryId)) { }

    private companion object {
        /** Twenty a page (§18.2): enough to fill a screen, few enough to be one read. */
        const val PAGE_SIZE = 20L
        const val MILLIS = 1000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

private fun DocumentSnapshot.toMemory(): MemoryDoc? {
    val title = getString("title") ?: return null
    return MemoryDoc(
        id = id,
        experienceId = getString("experienceId").orEmpty(),
        title = title,
        note = getString("note"),
        favoriteMoment = getString("favoriteMoment"),
        rating = getLong("rating")?.toInt(),
        mood = (get("mood") as? List<*>).orEmpty().filterIsInstance<String>(),
        durationMin = getLong("durationMin")?.toInt() ?: 0,
        experienceType = getString("experienceType") ?: "NIGHT",
        chapterCount = getLong("chapterCount")?.toInt() ?: 0,
        playedThrough = getLong("playedThrough")?.toInt() ?: 0,
        mediaIds = (get("mediaIds") as? List<*>).orEmpty().filterIsInstance<String>(),
        favorite = getBoolean("favorite") ?: false,
        dateMillis = getTimestamp("date")?.toDate()?.time ?: 0L,
    )
}
