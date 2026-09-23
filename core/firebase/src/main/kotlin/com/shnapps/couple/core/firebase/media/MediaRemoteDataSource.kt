package com.shnapps.couple.core.firebase.media

import android.net.Uri
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.callFunction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A voice note or a photo as the server describes it. Never the bytes (§5.8). */
data class MediaDoc(
    val id: String,
    val ownerUid: String,
    val kind: String,
    val state: String,
    val path: String,
    val note: String?,
    val burnAfterView: Boolean,
    val sentAtMillis: Long?,
    val viewedAtMillis: Long?,
    val expiresAtMillis: Long?,
)

/** Where the server says to upload, and how long what lands there will live. */
data class ReservedMedia(val itemId: String, val path: String, val expiresAtMillis: Long)

/**
 * Sending and opening media (BUILD_PROMPT.md §5.8, §13).
 *
 * The phone never chooses a path: the server reserves one, the rules allow an upload to that
 * path and no other, and the server confirms what landed. Nothing is ever given a download URL —
 * the bytes are fetched with the signed-in user's own credentials, so the rules apply to every
 * read, every time.
 */
interface MediaRemoteDataSource {
    /** A path to upload to, and the record that will describe what arrives. */
    suspend fun reserve(kind: String, note: String?, burnAfterView: Boolean, ttlMs: Long?): Outcome<ReservedMedia>

    /** Uploads [file] to the reserved [path]. */
    suspend fun upload(path: String, file: File, contentType: String): Outcome<Unit>

    /** The object has landed: the server checks it is really there and sends it. */
    suspend fun confirm(itemId: String): Outcome<Unit>

    /** Opening one. Returns whether it burned — the server deleted it as it was read. */
    suspend fun view(itemId: String): Outcome<Boolean>

    /** Fetches the bytes into [into], with this user's own credentials. */
    suspend fun download(path: String, into: File): Outcome<File>

    /** What the couple has sent each other, as the server holds it. */
    fun observe(coupleId: String): Flow<List<MediaDoc>>
}

@Singleton
class FirebaseMediaRemoteDataSource @Inject constructor(
    private val functions: FirebaseFunctions,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) : MediaRemoteDataSource {

    override suspend fun reserve(
        kind: String,
        note: String?,
        burnAfterView: Boolean,
        ttlMs: Long?,
    ): Outcome<ReservedMedia> {
        val payload = buildMap<String, Any> {
            put("kind", kind)
            put("burnAfterView", burnAfterView)
            note?.let { put("note", it) }
            ttlMs?.let { put("ttlMs", it) }
        }
        return functions.callFunction("reserveMedia", payload) { data ->
            ReservedMedia(
                itemId = data["itemId"] as String,
                path = data["path"] as String,
                expiresAtMillis = (data["expiresAt"] as Number).toLong(),
            )
        }
    }

    override suspend fun upload(path: String, file: File, contentType: String): Outcome<Unit> = storageCall {
        val metadata = StorageMetadata.Builder().setContentType(contentType).build()
        storage.reference.child(path).putFile(Uri.fromFile(file), metadata).await()
        Unit
    }

    override suspend fun confirm(itemId: String): Outcome<Unit> =
        functions.callFunction("confirmMedia", mapOf("itemId" to itemId)) { }

    override suspend fun view(itemId: String): Outcome<Boolean> =
        functions.callFunction("viewMedia", mapOf("itemId" to itemId)) { it["burned"] == true }

    override suspend fun download(path: String, into: File): Outcome<File> = storageCall {
        storage.reference.child(path).getFile(into).await()
        into
    }

    override fun observe(coupleId: String): Flow<List<MediaDoc>> = callbackFlow {
        val registration = firestore.collection("couples").document(coupleId).collection("media")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.toMedia() })
            }
        awaitClose { registration.remove() }
    }

    /** The Storage boundary: every failure becomes an AppError, and nothing throws past it. */
    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> storageCall(work: () -> T): Outcome<T> = try {
        Outcome.Success(work())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: StorageException) {
        Outcome.Failure(
            when (error.errorCode) {
                StorageException.ERROR_OBJECT_NOT_FOUND -> AppError.NotFound(error)
                StorageException.ERROR_NOT_AUTHENTICATED -> AppError.Unauthenticated(error)
                StorageException.ERROR_NOT_AUTHORIZED -> AppError.PermissionDenied(error)
                else -> AppError.Network(error)
            },
        )
    } catch (error: Throwable) {
        Outcome.Failure(AppError.Unknown(error))
    }
}

private fun DocumentSnapshot.toMedia(): MediaDoc? {
    val ownerUid = getString("ownerUid") ?: return null
    val kind = getString("kind") ?: return null
    val path = getString("path") ?: return null
    return MediaDoc(
        id = id,
        ownerUid = ownerUid,
        kind = kind,
        state = getString("state") ?: "PENDING",
        path = path,
        note = getString("note"),
        burnAfterView = getBoolean("burnAfterView") == true,
        sentAtMillis = getTimestamp("sentAt")?.toDate()?.time,
        viewedAtMillis = getTimestamp("viewedAt")?.toDate()?.time,
        expiresAtMillis = getTimestamp("expiresAt")?.toDate()?.time,
    )
}
