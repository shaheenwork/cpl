// Settings the user changes about themselves. Notifications first (§16.2); the rest arrives
// with Phase 20.
plugins {
    id("afterhours.android.feature")
}

android {
    namespace = "com.shnapps.couple.feature.settings"
}

dependencies {
    // Whether the app lock is on, for the row that opens it (D-146). The lock itself is
    // :core:security's, and its screens are :feature:applock's.
    implementation(project(":core:security"))
    // The tests keep a lock in a real in-memory store, which is made of AppPreferences.
    testImplementation(project(":core:datastore"))
}
