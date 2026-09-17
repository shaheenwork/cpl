package com.shnapps.couple.core.firebase

/**
 * Which backend this build talks to. Supplied by :app from its flavor BuildConfig, so
 * that :core:firebase never has to know about build variants (BUILD_PROMPT.md §4.3).
 *
 * The `dev` flavor points every service at the Firebase Emulator Suite, which is why a
 * developer needs no Firebase project and no credentials to run the app.
 */
data class FirebaseEnvironment(
    val name: String,
    val useEmulator: Boolean,
    val emulatorHost: String = ANDROID_EMULATOR_LOOPBACK,
) {
    companion object {
        /** From inside an Android emulator, the host machine is reachable at 10.0.2.2. */
        const val ANDROID_EMULATOR_LOOPBACK = "10.0.2.2"

        // Firebase Emulator Suite defaults; mirrored in firebase.json.
        const val AUTH_PORT = 9099
        const val FIRESTORE_PORT = 8080
        const val STORAGE_PORT = 9199
        const val FUNCTIONS_PORT = 5001
    }
}
