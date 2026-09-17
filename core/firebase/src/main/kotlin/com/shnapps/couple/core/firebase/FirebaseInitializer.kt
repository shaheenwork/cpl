package com.shnapps.couple.core.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time Firebase setup, called from the Application class.
 *
 * Deliberately does NOT touch Firestore/Auth/Storage: those are created by
 * [com.shnapps.couple.core.firebase.di.FirebaseModule], which has to be the single place
 * that wires emulator endpoints before first use.
 */
@Singleton
class FirebaseInitializer @Inject constructor(
    private val environment: FirebaseEnvironment,
) {

    fun initialize(context: Context, isDebugBuild: Boolean) {
        FirebaseApp.initializeApp(context)

        installAppCheck(isDebugBuild)

        // Nothing from a dev run should reach real Crashlytics or Analytics dashboards.
        val collectionEnabled = !environment.useEmulator
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = collectionEnabled
        FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(collectionEnabled)
    }

    private fun installAppCheck(isDebugBuild: Boolean) {
        val appCheck = FirebaseAppCheck.getInstance()
        val factory = if (isDebugBuild || environment.useEmulator) {
            // Prints a debug token on first run. Register it in the Firebase console
            // under App Check → Apps → Manage debug tokens (HUMAN_SETUP.md §2.4).
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        appCheck.installAppCheckProviderFactory(factory)
    }
}
