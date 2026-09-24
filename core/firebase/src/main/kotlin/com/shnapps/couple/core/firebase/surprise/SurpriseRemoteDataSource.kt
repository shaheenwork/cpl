package com.shnapps.couple.core.firebase.surprise

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.callFunction
import com.shnapps.couple.core.firebase.firestoreCall
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** A surprise as the server describes it. Never what is in it (§5.7). */
data class SurpriseDoc(
    val id: String,
    val type: String,
    val title: String,
    val createdBy: String,
    val forUid: String,
    val state: String,
    val scheduledForMillis: Long?,
    val trigger: String?,
    val hasMedia: Boolean,
)

/** What was inside, read only once the rules allow it. */
data class SurprisePayload(val text: String?, val mediaId: String?)

/** A countdown, written by either partner (§13). */
data class CountdownDoc(val id: String, val title: String, val targetAtMillis: Long)

/**
 * Things left for later (BUILD_PROMPT.md §5.7, §13).
 *
 * Every write here is a callable: a client cannot create a surprise, cannot deliver one, and
 * cannot mark one opened. Reading the payload is an ordinary Firestore read that the rules refuse
 * until the server has delivered it — so a phone asking early is denied rather than trusted.
 */
interface SurpriseRemoteDataSource {
    suspend fun create(
        type: String,
        title: String,
        text: String?,
        mediaId: String?,
        scheduledForMillis: Long?,
        trigger: String?,
    ): Outcome<String>

    /** "Open when you can't sleep": the server hands over the oldest one kept for that moment. */
    suspend fun openWhen(trigger: String): Outcome<String>

    /** Opening one that has been delivered. */
    suspend fun open(surpriseId: String): Outcome<Unit>

    /** What was inside. Denied by the rules until it has been delivered (§7.3 row S). */
    suspend fun payload(coupleId: String, surpriseId: String): Outcome<SurprisePayload>

    fun observe(coupleId: String): Flow<List<SurpriseDoc>>

    fun observeCountdowns(coupleId: String): Flow<List<CountdownDoc>>

    /** A countdown is the couple's own: no server logic, and either of them may keep or drop it. */
    suspend fun addCountdown(coupleId: String, uid: String, title: String, targetAtMillis: Long): Outcome<Unit>

    suspend fun removeCountdown(coupleId: String, countdownId: String): Outcome<Unit>
}

@Singleton
class FirebaseSurpriseRemoteDataSource @Inject constructor(
    private val functions: FirebaseFunctions,
    private val firestore: FirebaseFirestore,
) : SurpriseRemoteDataSource {

    override suspend fun create(
        type: String,
        title: String,
        text: String?,
        mediaId: String?,
        scheduledForMillis: Long?,
        trigger: String?,
    ): Outcome<String> {
        val payload = buildMap<String, Any> {
            put("type", type)
            put("title", title)
            text?.let { put("text", it) }
            mediaId?.let { put("mediaId", it) }
            scheduledForMillis?.let { put("scheduledFor", it) }
            trigger?.let { put("trigger", it) }
        }
        return functions.callFunction("createSurprise", payload) { it["surpriseId"] as String }
    }

    override suspend fun openWhen(trigger: String): Outcome<String> =
        functions.callFunction("openWhen", mapOf("trigger" to trigger)) { it["surpriseId"] as String }

    override suspend fun open(surpriseId: String): Outcome<Unit> =
        functions.callFunction("openSurprise", mapOf("surpriseId" to surpriseId)) { }

    override suspend fun payload(coupleId: String, surpriseId: String): Outcome<SurprisePayload> = firestoreCall {
        val doc = firestore.collection(COUPLES).document(coupleId)
            .collection(SURPRISES).document(surpriseId)
            .collection(PAYLOAD).document(CONTENT)
            .get()
            .await()
        SurprisePayload(text = doc.getString("text"), mediaId = doc.getString("mediaId"))
    }

    override fun observe(coupleId: String): Flow<List<SurpriseDoc>> = callbackFlow {
        val registration = firestore.collection(COUPLES).document(coupleId).collection(SURPRISES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.toSurprise() })
            }
        awaitClose { registration.remove() }
    }

    override fun observeCountdowns(coupleId: String): Flow<List<CountdownDoc>> = callbackFlow {
        val registration = firestore.collection(COUPLES).document(coupleId).collection(COUNTDOWNS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.toCountdown() })
            }
        awaitClose { registration.remove() }
    }

    override suspend fun addCountdown(
        coupleId: String,
        uid: String,
        title: String,
        targetAtMillis: Long,
    ): Outcome<Unit> = firestoreCall {
        firestore.collection(COUPLES).document(coupleId).collection(COUNTDOWNS).document()
            .set(
                mapOf(
                    "title" to title,
                    "targetAt" to Timestamp(targetAtMillis / MILLIS_PER_SECOND, 0),
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
    }

    override suspend fun removeCountdown(coupleId: String, countdownId: String): Outcome<Unit> = firestoreCall {
        firestore.collection(COUPLES).document(coupleId).collection(COUNTDOWNS).document(countdownId)
            .delete()
            .await()
    }

    private companion object {
        const val COUPLES = "couples"
        const val SURPRISES = "surprises"
        const val COUNTDOWNS = "countdowns"
        const val PAYLOAD = "payload"
        const val CONTENT = "content"
        const val MILLIS_PER_SECOND = 1000L
    }
}

private fun DocumentSnapshot.toSurprise(): SurpriseDoc? {
    val type = getString("type") ?: return null
    val createdBy = getString("createdBy") ?: return null
    val forUid = getString("forUid") ?: return null
    return SurpriseDoc(
        id = id,
        type = type,
        title = getString("title").orEmpty(),
        createdBy = createdBy,
        forUid = forUid,
        state = getString("state") ?: "SCHEDULED",
        scheduledForMillis = getTimestamp("scheduledFor")?.toDate()?.time,
        trigger = getString("trigger"),
        hasMedia = getBoolean("hasMedia") == true,
    )
}

private fun DocumentSnapshot.toCountdown(): CountdownDoc? {
    val target = getTimestamp("targetAt")?.toDate()?.time ?: return null
    return CountdownDoc(id = id, title = getString("title").orEmpty(), targetAtMillis = target)
}
