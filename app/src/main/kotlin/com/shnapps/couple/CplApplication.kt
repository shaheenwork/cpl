package com.shnapps.couple

import android.app.Application
import com.shnapps.couple.core.common.ApplicationScope
import com.shnapps.couple.core.firebase.FirebaseInitializer
import com.shnapps.couple.core.firebase.config.RemoteConfigSource
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * In the `dev` flavor every Firebase client is redirected to the local Emulator Suite, so
 * the app runs with no Firebase project and no credentials (BUILD_PROMPT.md §4.3).
 */
@HiltAndroidApp
class CplApplication : Application() {

    @Inject
    lateinit var firebaseInitializer: FirebaseInitializer

    @Inject
    lateinit var remoteConfigSource: RemoteConfigSource

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        firebaseInitializer.initialize(
            context = this,
            isDebugBuild = BuildConfig.DEBUG,
        )
        // Off the main thread and not awaited: XML defaults are already in force, so a
        // slow or failed fetch never blocks launch.
        applicationScope.launch { remoteConfigSource.initialize() }
    }
}
