// The home surface (BUILD_PROMPT.md section 14.2): the way in to everything, and the
// TOGETHER / APART switch that drives it.
plugins {
    id("afterhours.android.feature")
}

android {
    namespace = "com.shnapps.couple.feature.home"
}

dependencies {
    // The home this phone opens on is a device setting, like the app lock's (§14.2).
    implementation(project(":core:datastore"))
}
