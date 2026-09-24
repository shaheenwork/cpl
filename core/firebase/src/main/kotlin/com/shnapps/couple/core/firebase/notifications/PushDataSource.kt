package com.shnapps.couple.core.firebase.notifications

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.firestoreCall
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where this phone can be reached (BUILD_PROMPT.md §16).
 *
 * A token is written to the signed-in user's own `fcmTokens` and nowhere else — the rules allow
 * no other path, because a token in someone else's list would forward their notifications to this
 * phone. Signing out deletes it, so a shared or sold device stops receiving anything.
 */
interface PushDataSource {
    /** This phone's current token, or null when the device has no push service to ask. */
    suspend fun currentToken(): String?

    suspend fun register(uid: String, token: String): Outcome<Unit>

    suspend fun unregister(uid: String, token: String): Outcome<Unit>
}

@Singleton
class FirebasePushDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
) : PushDataSource {

    override suspend fun currentToken(): String? = runCatching { messaging.token.await() }.getOrNull()

    override suspend fun register(uid: String, token: String): Outcome<Unit> = firestoreCall {
        tokens(uid, token).set(
            mapOf(
                "token" to token,
                "platform" to "ANDROID",
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun unregister(uid: String, token: String): Outcome<Unit> = firestoreCall {
        tokens(uid, token).delete().await()
    }

    /** The document is named after the token, so registering twice replaces rather than piles up. */
    private fun tokens(uid: String, token: String) =
        firestore.collection("users").document(uid).collection("fcmTokens").document(token.takeLast(MAX_ID))

    private companion object {
        /** Firestore document ids cap at 1500 bytes; a token's tail is unique enough and short. */
        const val MAX_ID = 100
    }
}
