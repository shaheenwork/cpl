package com.shnapps.couple.core.firebase.content

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.FirebaseEnvironment
import com.shnapps.couple.core.firebase.config.RemoteConfigKeys
import com.shnapps.couple.core.firebase.config.RemoteConfigSource
import com.shnapps.couple.core.firebase.firestoreCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where newer content comes from (BUILD_PROMPT.md §9.2): the version pointer, the bundles
 * in Storage, and the admin deltas in Firestore. Nothing here is per-item: browsing
 * content costs no Firestore reads at all.
 */
interface ContentRemoteDataSource {
    /** The content version clients should be on. */
    suspend fun latestVersion(): Outcome<Int>

    /** The bundle for [version], as published by tools/publish-content. */
    suspend fun downloadBundle(version: Int): Outcome<String>

    /** Deltas written at or after [cursorMillis], oldest first. */
    suspend fun deltasSince(cursorMillis: Long): Outcome<List<ContentDelta>>
}

/** An admin override on one item: see Firestore `content/{id}` and tools/content-delta. */
data class ContentDelta(
    val id: String,
    val status: String,
    val version: Int,
    /** The edited item as JSON, or null when only the status is overridden. */
    val itemJson: String?,
    val updatedAtMillis: Long,
)

@Singleton
class FirebaseContentRemoteDataSource @Inject constructor(
    private val remoteConfig: RemoteConfigSource,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val environment: FirebaseEnvironment,
) : ContentRemoteDataSource {

    override suspend fun latestVersion(): Outcome<Int> =
        if (environment.useEmulator) {
            // Remote Config has no emulator; publish-content --emulator writes here instead
            // (DECISIONS.md D-041). No document yet means nothing has been published.
            firestoreCall {
                val pointer = firestore.document(EMULATOR_POINTER_DOC).get().await()
                pointer.getLong(FIELD_VERSION)?.toInt() ?: 0
            }
        } else {
            remoteConfig.refresh()
            Outcome.Success(remoteConfig.long(RemoteConfigKeys.CONTENT_VERSION).toInt())
        }

    @Suppress("TooGenericExceptionCaught") // The Storage boundary: every failure becomes an AppError.
    override suspend fun downloadBundle(version: Int): Outcome<String> = try {
        val bytes = storage.reference.child("content/bundles/v$version/bundle.json").getBytes(MAX_BUNDLE_BYTES).await()
        Outcome.Success(bytes.decodeToString())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: StorageException) {
        Outcome.Failure(
            when (error.errorCode) {
                StorageException.ERROR_OBJECT_NOT_FOUND -> AppError.NotFound(error)
                StorageException.ERROR_NOT_AUTHENTICATED -> AppError.Unauthenticated(error)
                StorageException.ERROR_NOT_AUTHORIZED -> AppError.PermissionDenied(error)
                StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> AppError.Network(error)
                else -> AppError.Unknown(error)
            },
        )
    } catch (error: Exception) {
        Outcome.Failure(AppError.Unknown(error))
    }

    override suspend fun deltasSince(cursorMillis: Long): Outcome<List<ContentDelta>> = firestoreCall {
        firestore.collection(DELTA_COLLECTION)
            .whereGreaterThanOrEqualTo(FIELD_UPDATED_AT, Timestamp(Date(cursorMillis)))
            .orderBy(FIELD_UPDATED_AT, Query.Direction.ASCENDING)
            .get()
            .await()
            .documents
            .mapNotNull { document ->
                val status = document.getString(FIELD_STATUS) ?: return@mapNotNull null
                val version = document.getLong(FIELD_VERSION)?.toInt() ?: return@mapNotNull null
                val updatedAt = document.getTimestamp(FIELD_UPDATED_AT) ?: return@mapNotNull null

                @Suppress("UNCHECKED_CAST") // Firestore maps always have string keys.
                val item = document.get(FIELD_ITEM) as? Map<String, Any?>
                ContentDelta(
                    id = document.id,
                    status = status,
                    version = version,
                    itemJson = item?.let { JSONObject(it).toString() },
                    updatedAtMillis = updatedAt.toDate().time,
                )
            }
    }

    private companion object {
        const val EMULATOR_POINTER_DOC = "contentMeta/pointer"
        const val DELTA_COLLECTION = "content"
        const val FIELD_VERSION = "version"
        const val FIELD_STATUS = "status"
        const val FIELD_ITEM = "item"
        const val FIELD_UPDATED_AT = "updatedAt"

        /** Ten times the seed bundle. A bigger object is refused rather than read into memory. */
        const val MAX_BUNDLE_BYTES = 5L * 1024 * 1024
    }
}
