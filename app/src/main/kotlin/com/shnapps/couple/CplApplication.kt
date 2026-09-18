package com.shnapps.couple

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.shnapps.couple.core.common.ApplicationScope
import com.shnapps.couple.core.data.content.ContentSyncScheduler
import com.shnapps.couple.core.firebase.FirebaseInitializer
import com.shnapps.couple.core.firebase.config.RemoteConfigSource
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * In the `dev` flavor every Firebase client is redirected to the local Emulator Suite, so
 * the app runs with no Firebase project and no credentials (BUILD_PROMPT.md §4.3).
 *
 * Also the WorkManager configuration: workers are built by Hilt, so the default initializer
 * is removed from the manifest and WorkManager starts from here on first use.
 */
@HiltAndroidApp
class CplApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var firebaseInitializer: FirebaseInitializer

    @Inject
    lateinit var remoteConfigSource: RemoteConfigSource

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var contentSyncScheduler: ContentSyncScheduler

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        firebaseInitializer.initialize(
            context = this,
            isDebugBuild = BuildConfig.DEBUG,
        )
        // Off the main thread and not awaited: XML defaults are already in force, so a
        // slow or failed fetch never blocks launch.
        applicationScope.launch { remoteConfigSource.initialize() }
        // Content is already on the device (§9.2); this only looks for something newer.
        contentSyncScheduler.schedule()
    }
}
