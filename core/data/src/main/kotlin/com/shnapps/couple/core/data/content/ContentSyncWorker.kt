package com.shnapps.couple.core.data.content

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the content cache current (BUILD_PROMPT.md §9.2): a newer bundle when the pointer
 * moves, and the admin deltas — which is how a global disable reaches phones without a
 * release (§9.6).
 */
@HiltWorker
class ContentSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ContentRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (val outcome = repository.sync()) {
        is Outcome.Success -> Result.success()
        is Outcome.Failure -> when (outcome.error) {
            // Signed out: content is read only by signed-in apps. Nothing to retry until
            // someone signs in, and the next scheduled run will pick it up then.
            is AppError.Unauthenticated, is AppError.PermissionDenied -> Result.success()
            // A bad bundle stays bad however often it is fetched; wait for a new publish.
            is AppError.Validation -> Result.failure()
            else -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}

/** Schedules [ContentSyncWorker]: once now, and then twice a day, whenever there is a network. */
@Singleton
class ContentSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule() {
        val workManager = WorkManager.getInstance(context)
        val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ContentSyncWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .build(),
        )
        // On every process start too, so a disable reaches an app that is opened long
        // before its next periodic run. KEEP: a run already queued is not duplicated.
        workManager.enqueueUniqueWork(
            ON_START_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ContentSyncWorker>()
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .build(),
        )
    }

    private companion object {
        const val PERIODIC_WORK = "content-sync"
        const val ON_START_WORK = "content-sync-on-start"
        const val PERIOD_HOURS = 12L
        const val BACKOFF_MINUTES = 5L
    }
}
