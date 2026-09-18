package com.shnapps.couple.core.firebase.boundaries

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.firestoreCall
import com.shnapps.couple.core.model.Boundary
import com.shnapps.couple.core.model.BoundaryLevel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `users/{uid}/boundaries/{themeId}` — a user's private boundaries (BUILD_PROMPT.md §6.1).
 *
 * Owner-only for life, by rule (§5.1). Only ever the signed-in user's own collection. The
 * couple's combined filters are computed from these by the server and are unreadable by any
 * client (§5.4), so nothing here — or anywhere in the app — ever sees a partner's settings.
 */
@Singleton
class BoundaryDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    fun observeBoundaries(uid: String): Flow<Map<String, Boundary>> = callbackFlow {
        val registration = collection(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val boundaries = snapshot?.documents.orEmpty()
                .mapNotNull { document -> document.toBoundary()?.let { document.id to it } }
                .toMap()
            trySend(boundaries)
        }
        awaitClose { registration.remove() }
    }

    /** Sets a boundary, replacing any earlier one. `updatedAt` is the server's time; the rules insist. */
    suspend fun setBoundary(uid: String, themeId: String, boundary: Boundary): Outcome<Unit> = firestoreCall {
        val fields = buildMap<String, Any> {
            put(FIELD_LEVEL, boundary.level.name)
            boundary.note?.let { put(FIELD_NOTE, it) }
            put(FIELD_UPDATED_AT, FieldValue.serverTimestamp())
        }
        collection(uid).document(themeId).set(fields).await()
    }

    /** Clears a boundary. Deleted, not blanked: an unset theme has no document. */
    suspend fun clearBoundary(uid: String, themeId: String): Outcome<Unit> = firestoreCall {
        collection(uid).document(themeId).delete().await()
    }

    private fun collection(uid: String) =
        firestore.collection(USERS).document(uid).collection(BOUNDARIES)
}

private const val USERS = "users"
private const val BOUNDARIES = "boundaries"
private const val FIELD_LEVEL = "level"
private const val FIELD_NOTE = "note"
private const val FIELD_UPDATED_AT = "updatedAt"

/**
 * Tolerant parse: a level this version does not know is skipped rather than crashing. The
 * server treats the same unknown level as NEVER (fail closed), so skipping it here can only
 * under-report a boundary on screen, never weaken one.
 */
private fun DocumentSnapshot.toBoundary(): Boundary? {
    val level = getString(FIELD_LEVEL)
        ?.let { name -> BoundaryLevel.entries.firstOrNull { it.name == name } }
        ?: return null
    val note = getString(FIELD_NOTE)?.takeIf { it.length <= Boundary.MAX_NOTE_LENGTH }
    return Boundary(level, note)
}
