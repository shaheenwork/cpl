package com.shnapps.couple.core.testing

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.account.AccountRepository
import com.shnapps.couple.core.data.account.ExportedData
import com.shnapps.couple.core.model.ReportReason
import com.shnapps.couple.core.model.ReportTarget
import java.io.File

/** What was reported, and how: the fake keeps it so a test can say the partner was never told. */
data class ReportedItem(val target: ReportTarget, val id: String, val reason: ReportReason)

/** In-memory [AccountRepository]. Nothing here writes to a disk a test did not name. */
class FakeAccountRepository : AccountRepository {

    val reports = mutableListOf<ReportedItem>()
    var exports = 0
        private set
    var deleted = false
        private set
    var failNext: AppError? = null

    /** The bytes a fake export pretends to have written. */
    var exportBytes: Long = 42L

    override suspend fun report(target: ReportTarget, id: String, reason: ReportReason): Outcome<Unit> {
        failNext?.let {
            failNext = null
            return Outcome.Failure(it)
        }
        reports += ReportedItem(target, id, reason)
        return Outcome.Success(Unit)
    }

    override suspend fun export(directory: File): Outcome<ExportedData> {
        failNext?.let {
            failNext = null
            return Outcome.Failure(it)
        }
        exports += 1
        return Outcome.Success(
            ExportedData(
                file = File(directory, "afterhours-export.json"),
                bytes = exportBytes,
                expiresAtMillis = 0L,
            ),
        )
    }

    override suspend fun deleteAccount(): Outcome<Unit> {
        failNext?.let {
            failNext = null
            return Outcome.Failure(it)
        }
        deleted = true
        return Outcome.Success(Unit)
    }
}
