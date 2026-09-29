// Settings the user changes about themselves. Notifications first (§16.2); the rest arrives
// with Phase 20.
plugins {
    id("afterhours.android.feature")
}

android {
    namespace = "com.shnapps.couple.feature.settings"
}

dependencies {
    // The discreet launcher icon (M8): which alias the launcher shows is a platform component
    // setting, and :core:security is where the platform's own privacy surfaces live.
    implementation(project(":core:security"))
}
