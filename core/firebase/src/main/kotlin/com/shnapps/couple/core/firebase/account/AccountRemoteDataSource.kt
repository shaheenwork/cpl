package com.shnapps.couple.core.firebase.account

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.callFunction
import com.shnapps.couple.core.firebase.storageCall
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Where the server put an export, and how big it is. The bytes are fetched separately. */
data class ExportDoc(val path: String, val bytes: Long, val expiresAtMillis: Long)

/**
 * The account itself (BUILD_PROMPT.md §3.5, §14.9): reporting, an export, and deletion.
 *
 * All three are callables, because all three are decisions a phone must not make for itself: a
 * report is written where no client can read it, an export is assembled from documents a phone
 * cannot all see, and a deletion has to reach Auth, Firestore and Storage in one go.
 */
interface AccountRemoteDataSource {
    suspend fun report(target: String, id: String, reason: String): Outcome<Unit>

    /** Asks the server to write the export, and says where it went. */
    suspend fun requestExport(): Outcome<ExportDoc>

    /** Fetches the export with this user's own credentials, into [into]. */
    suspend fun downloadExport(path: String, into: File): Outcome<File>

    suspend fun deleteAccount(): Outcome<Unit>

    /** After the account is gone, so does the session on this phone. */
    fun signOut()
}

@Singleton
class FirebaseAccountRemoteDataSource @Inject constructor(
    private val functions: FirebaseFunctions,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
) : AccountRemoteDataSource {

    override suspend fun report(target: String, id: String, reason: String): Outcome<Unit> =
        functions.callFunction(
            "reportContent",
            mapOf("target" to target, "id" to id, "reason" to reason),
        ) { }

    override suspend fun requestExport(): Outcome<ExportDoc> =
        functions.callFunction("requestDataExport", emptyMap<String, Any>()) { result ->
            ExportDoc(
                path = result["exportPath"] as String,
                bytes = (result["bytes"] as? Number)?.toLong() ?: 0L,
                expiresAtMillis = (result["expiresAtMillis"] as? Number)?.toLong() ?: 0L,
            )
        }

    override suspend fun downloadExport(path: String, into: File): Outcome<File> = storageCall {
        into.parentFile?.mkdirs()
        storage.reference.child(path).getFile(into).await()
        into
    }

    override suspend fun deleteAccount(): Outcome<Unit> =
        functions.callFunction("deleteAccount", mapOf("confirm" to true)) { }

    override fun signOut() = auth.signOut()
}
