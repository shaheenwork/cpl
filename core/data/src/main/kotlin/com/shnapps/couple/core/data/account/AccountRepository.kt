package com.shnapps.couple.core.data.account

import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.datastore.AppPreferencesStore
import com.shnapps.couple.core.firebase.account.AccountRemoteDataSource
import com.shnapps.couple.core.model.ReportReason
import com.shnapps.couple.core.model.ReportTarget
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** An export, on this phone: where it is, how big, and when the server's copy goes away. */
data class ExportedData(val file: File, val bytes: Long, val expiresAtMillis: Long)

/**
 * The account itself (BUILD_PROMPT.md §3.5, §14.9): reporting, an export, and deletion.
 *
 * All three go through the server. A phone cannot write a report where the partner cannot read it,
 * cannot assemble an export from documents it is not allowed to see, and cannot delete an account
 * across Auth, Firestore and Storage — so it asks, and shows what came back.
 */
interface AccountRepository {
    /** Reports something the app showed, or something the partner sent (§3.5). */
    suspend fun report(target: ReportTarget, id: String, reason: ReportReason): Outcome<Unit>

    /** Asks for an export and fetches it into [directory] with this user's own credentials. */
    suspend fun export(directory: File): Outcome<ExportedData>

    /** Deletes the account and everything with it, then signs this phone out (D-093). */
    suspend fun deleteAccount(): Outcome<Unit>
}

@Singleton
class DefaultAccountRepository @Inject constructor(
    private val remote: AccountRemoteDataSource,
    private val preferences: AppPreferencesStore,
) : AccountRepository {

    override suspend fun report(target: ReportTarget, id: String, reason: ReportReason): Outcome<Unit> =
        remote.report(target.name, id, reason.name)

    override suspend fun export(directory: File): Outcome<ExportedData> =
        when (val requested = remote.requestExport()) {
            is Outcome.Failure -> requested
            is Outcome.Success -> {
                val into = File(directory, requested.value.path.substringAfterLast('/'))
                when (val downloaded = remote.downloadExport(requested.value.path, into)) {
                    is Outcome.Failure -> downloaded
                    is Outcome.Success -> Outcome.Success(
                        ExportedData(
                            file = downloaded.value,
                            bytes = requested.value.bytes,
                            expiresAtMillis = requested.value.expiresAtMillis,
                        ),
                    )
                }
            }
        }

    override suspend fun deleteAccount(): Outcome<Unit> =
        when (val deleted = remote.deleteAccount()) {
            is Outcome.Failure -> deleted
            is Outcome.Success -> {
                // Nothing of the departed account stays on this phone either: the lock, the hints,
                // the names they typed into a game.
                preferences.clear()
                remote.signOut()
                Outcome.Success(Unit)
            }
        }
}
