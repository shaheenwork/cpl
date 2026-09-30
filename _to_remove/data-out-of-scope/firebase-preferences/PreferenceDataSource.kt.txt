package com.shnapps.couple.core.firebase.preferences

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.firestoreCall
import com.shnapps.couple.core.model.PreferenceAnswer
import com.shnapps.couple.core.model.PreferenceValue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `users/{uid}/preferences/{itemId}` — a user's private answers (BUILD_PROMPT.md §6.1).
 *
 * Owner-only for life, by rule (§5.1): there is no path in the security rules by which a
 * partner, or anyone else, can read these documents. This class only ever touches the
 * signed-in user's own collection.
 *
 * Kept apart from `UserProfileDataSource` on purpose, so the type carrying a user's most
 * sensitive answers is never casually reachable from the one carrying their display name.
 */
@Singleton
class PreferenceDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    /**
     * Every answer the user has given, by item id.
     *
     * Served from the local cache first and kept live, so answers made offline appear
     * immediately and sync when the connection returns.
     */
    fun observeAnswers(uid: String): Flow<Map<String, PreferenceAnswer>> = callbackFlow {
        val registration = collection(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val answers = snapshot?.documents.orEmpty()
                .mapNotNull { document -> document.toAnswer()?.let { document.id to it } }
                .toMap()
            trySend(answers)
        }
        awaitClose { registration.remove() }
    }

    /**
     * Records an answer, replacing any earlier one.
     *
     * `updatedAt` is the server's time, and the rules reject any other. The document holds
     * exactly these four fields — the rules reject extras, so nothing can ride along.
     */
    suspend fun setAnswer(
        uid: String,
        itemId: String,
        answer: PreferenceAnswer,
        taxonomyVersion: Int,
    ): Outcome<Unit> = firestoreCall {
        collection(uid).document(itemId).set(
            mapOf(
                FIELD_VALUE to answer.value.name,
                FIELD_SECRET to answer.secret,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                FIELD_TAXONOMY_VERSION to taxonomyVersion,
            ),
        ).await()
    }

    /** Forgets an answer entirely. Deleted, not blanked: an unanswered item has no document. */
    suspend fun clearAnswer(uid: String, itemId: String): Outcome<Unit> = firestoreCall {
        collection(uid).document(itemId).delete().await()
    }

    private fun collection(uid: String) =
        firestore.collection(USERS).document(uid).collection(PREFERENCES)
}

private const val USERS = "users"
private const val PREFERENCES = "preferences"
private const val FIELD_VALUE = "value"
private const val FIELD_SECRET = "secret"
private const val FIELD_UPDATED_AT = "updatedAt"
private const val FIELD_TAXONOMY_VERSION = "taxonomyVersion"

/**
 * Tolerant parse: an unrecognised value is skipped rather than crashing, so a newer
 * server-side vocabulary never takes down an older client.
 */
private fun DocumentSnapshot.toAnswer(): PreferenceAnswer? {
    val value = getString(FIELD_VALUE)
        ?.let { name -> PreferenceValue.entries.firstOrNull { it.name == name } }
        ?: return null
    // The rules only allow `secret` alongside CURIOUS; honour that here too rather than
    // trusting the stored pair blindly.
    val secret = getBoolean(FIELD_SECRET) == true && value == PreferenceValue.CURIOUS
    return PreferenceAnswer(value, secret)
}
