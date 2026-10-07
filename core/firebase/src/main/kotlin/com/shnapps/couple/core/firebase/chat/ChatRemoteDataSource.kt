package com.shnapps.couple.core.firebase.chat

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.firestoreCall
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A message as it is stored. The uid is turned into "mine" a layer up; no screen sees one. */
data class MessageDoc(
    val id: String,
    val fromUid: String,
    val kind: String,
    val text: String?,
    val mediaId: String?,
    /** Null while the write is still local: the server's clock has not stamped it yet. */
    val atMillis: Long?,
)

/**
 * What the two of them say to each other (CHAT_PROMPT.md).
 *
 * The one collection under a couple this app writes from the phone. There is nothing for a server
 * to decide about a message, and a callable per line would be latency and cost for nothing — the
 * rules hold the shape instead: whose it is, when it was, and that it is never edited.
 */
interface ChatRemoteDataSource {
    /**
     * The newest [limit] messages, oldest first, **including ones still on their way**.
     *
     * Local writes are listened for on purpose (`MetadataChanges.INCLUDE`): a message appears the
     * moment it is written and carries no timestamp until the server stamps it, which is what the
     * screen draws as "sending".
     */
    fun observe(coupleId: String, limit: Int): Flow<List<MessageDoc>>

    /** Says one thing. Returns as soon as it is written locally — the send never blocks on a round trip. */
    suspend fun send(coupleId: String, uid: String, kind: String, text: String?, mediaId: String?): Outcome<String>

    /** Takes one back, for both of them. */
    suspend fun delete(coupleId: String, messageId: String): Outcome<Unit>

    /** How far each of them has read: this phone's own mark, and both marks to read. */
    suspend fun markRead(coupleId: String, uid: String): Outcome<Unit>

    fun observeRead(coupleId: String): Flow<Map<String, Long>>
}

@Singleton
class FirestoreChatDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ChatRemoteDataSource {

    override fun observe(coupleId: String, limit: Int): Flow<List<MessageDoc>> = callbackFlow {
        val registration = messages(coupleId)
            .orderBy(AT, Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                // Newest first out of Firestore, because that is the page worth having; oldest
                // first on the way out, because that is the order a conversation is read in.
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.toMessage() }.reversed())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun send(
        coupleId: String,
        uid: String,
        kind: String,
        text: String?,
        mediaId: String?,
    ): Outcome<String> = firestoreCall {
        val document = messages(coupleId).document()
        val data = buildMap {
            put(FROM, uid)
            // The server's clock, so neither phone can place a message where it did not happen.
            put(AT, FieldValue.serverTimestamp())
            put(KIND, kind)
            text?.let { put(TEXT, it) }
            mediaId?.let { put(MEDIA_ID, it) }
        }
        // Deliberately not awaited: a Firestore write is queued offline and retried for ever, so a
        // composer that waited for the server would hang on a train. The local write has already
        // happened by the time this returns, and the listener above has already drawn it.
        document.set(data)
        document.id
    }

    override suspend fun delete(coupleId: String, messageId: String): Outcome<Unit> = firestoreCall {
        messages(coupleId).document(messageId).delete()
        Unit
    }

    override suspend fun markRead(coupleId: String, uid: String): Outcome<Unit> = firestoreCall {
        firestore.collection(COUPLES).document(coupleId).collection(CHAT_STATE).document(uid)
            .set(mapOf(LAST_READ to FieldValue.serverTimestamp()))
        Unit
    }

    override fun observeRead(coupleId: String): Flow<Map<String, Long>> = callbackFlow {
        val registration = firestore.collection(COUPLES).document(coupleId).collection(CHAT_STATE)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.documents.orEmpty()
                        .mapNotNull { document ->
                            document.getTimestamp(LAST_READ)?.let { document.id to it.toDate().time }
                        }
                        .toMap(),
                )
            }
        awaitClose { registration.remove() }
    }

    private fun messages(coupleId: String) =
        firestore.collection(COUPLES).document(coupleId).collection(MESSAGES)

    private companion object {
        const val COUPLES = "couples"
        const val MESSAGES = "messages"
        const val CHAT_STATE = "chatState"
        const val FROM = "from"
        const val AT = "at"
        const val KIND = "kind"
        const val TEXT = "text"
        const val MEDIA_ID = "mediaId"
        const val LAST_READ = "lastReadAt"
    }
}

private fun DocumentSnapshot.toMessage(): MessageDoc? {
    val from = getString("from") ?: return null
    val kind = getString("kind") ?: return null
    return MessageDoc(
        id = id,
        fromUid = from,
        kind = kind,
        text = getString("text"),
        mediaId = getString("mediaId"),
        atMillis = getTimestamp("at")?.toDate()?.time,
    )
}
