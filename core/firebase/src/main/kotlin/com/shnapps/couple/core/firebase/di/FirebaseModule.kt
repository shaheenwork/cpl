package com.shnapps.couple.core.firebase.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.storage.FirebaseStorage
import com.shnapps.couple.core.firebase.FirebaseEnvironment
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the Firebase clients, each pointed at the emulator when the environment says
 * so. Emulator wiring has to happen before a client is first used, which is exactly why
 * every client is constructed here and nowhere else.
 *
 * The clients are process-wide singletons, but a Hilt component is not: instrumented tests
 * build a fresh `SingletonComponent` for every test. Settings and emulator wiring can only
 * be applied before a client's first use — a second attempt throws — so each client is
 * configured once per process, however many components ask for it.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(environment: FirebaseEnvironment): FirebaseAuth =
        FirebaseAuth.getInstance().apply {
            ProcessWide.once("auth") {
                if (environment.useEmulator) {
                    useEmulator(environment.emulatorHost, FirebaseEnvironment.AUTH_PORT)
                }
            }
        }

    @Provides
    @Singleton
    fun provideFirestore(environment: FirebaseEnvironment): FirebaseFirestore =
        FirebaseFirestore.getInstance().apply {
            ProcessWide.once("firestore") {
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        // The emulator is wiped between runs, so persisting its data locally
                        // only creates confusing stale state.
                        if (environment.useEmulator) {
                            MemoryCacheSettings.newBuilder().build()
                        } else {
                            PersistentCacheSettings.newBuilder().build()
                        },
                    )
                    .build()
                if (environment.useEmulator) {
                    useEmulator(environment.emulatorHost, FirebaseEnvironment.FIRESTORE_PORT)
                }
            }
        }

    @Provides
    @Singleton
    fun provideStorage(environment: FirebaseEnvironment): FirebaseStorage =
        FirebaseStorage.getInstance().apply {
            ProcessWide.once("storage") {
                if (environment.useEmulator) {
                    useEmulator(environment.emulatorHost, FirebaseEnvironment.STORAGE_PORT)
                }
            }
        }

    @Provides
    @Singleton
    fun provideFunctions(environment: FirebaseEnvironment): FirebaseFunctions =
        FirebaseFunctions.getInstance().apply {
            ProcessWide.once("functions") {
                if (environment.useEmulator) {
                    useEmulator(environment.emulatorHost, FirebaseEnvironment.FUNCTIONS_PORT)
                }
            }
        }

    @Provides
    @Singleton
    fun provideMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

    @Provides
    @Singleton
    fun provideRemoteConfig(): FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
}

/** Runs each named block at most once per process. */
private object ProcessWide {
    private val done = HashSet<String>()

    @Synchronized
    fun once(key: String, block: () -> Unit) {
        if (done.add(key)) block()
    }
}
