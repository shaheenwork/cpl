// Sign in, sign up, sign out and password reset (BUILD_PROMPT.md §7).
plugins {
    id("afterhours.android.feature")
}

android {
    namespace = "com.shnapps.couple.feature.auth"
}

dependencies {
    // The reconciler reads what this phone learned before anyone signed in.
    testImplementation(project(":core:datastore"))
}
