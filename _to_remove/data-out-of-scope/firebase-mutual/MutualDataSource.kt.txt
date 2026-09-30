package com.shnapps.couple.core.firebase.mutual

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.callFunction
import com.shnapps.couple.core.firebase.firestoreCall
import com.shnapps.couple.core.model.MatchLevel
import com.shnapps.couple.core.model.MutualMatch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `couples/{cid}/mutualPreferences` — matches the server has revealed (BUILD_PROMPT.md §5.2).
 *
 * Readable by both members; the only thing a client may change is its own `seenBy` entry.
 * Matches still waiting for their release time live in a queue no client can read, so this
 * collection only ever holds what both partners are meant to know.
 */
@Singleton
class MutualDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) {
    fun observeMatches(coupleId: String, uid: String): Flow<List<MutualMatch>> = callbackFlow {
        val registration = collection(coupleId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents.orEmpty().mapNotNull { it.toMatch(uid) }.sortedBy { it.revealedAtEpochMillis })
        }
        awaitClose { registration.remove() }
    }

    /** Marks a match as revealed to this user. The rules allow nothing else. */
    suspend fun markSeen(coupleId: String, itemId: String, uid: String): Outcome<Unit> = firestoreCall {
        collection(coupleId).document(itemId).update("$FIELD_SEEN_BY.$uid", true).await()
    }

    /**
     * Asks the server to release anything that has already waited the minimum delay — the
     * "release now" on opening the app (§5.3). Returns how many changes were released.
     */
    suspend fun releaseNow(): Outcome<Int> = functions.callFunction("releaseRevealsNow", null) { data ->
        (data["released"] as? Number)?.toInt() ?: 0
    }

    private fun collection(coupleId: String) =
        firestore.collection(COUPLES).document(coupleId).collection(MUTUAL)
}

private const val COUPLES = "couples"
private const val MUTUAL = "mutualPreferences"
private const val FIELD_SEEN_BY = "seenBy"

/** Tolerant parse: a match level this version does not know is skipped, never crashed on. */
private fun DocumentSnapshot.toMatch(uid: String): MutualMatch? {
    val level = getString("matchLevel")
        ?.let { name -> MatchLevel.entries.firstOrNull { it.name == name } }
        ?: return null
    val seenBy = get(FIELD_SEEN_BY) as? Map<*, *>
    return MutualMatch(
        itemId = id,
        level = level,
        revealedAtEpochMillis = (get("revealedAt") as? Timestamp)?.toDate()?.time ?: 0L,
        seen = seenBy?.get(uid) == true,
    )
}
