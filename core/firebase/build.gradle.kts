// Firebase data sources and DTOs. This is the ONLY module allowed to import Firebase
// types — :architecture fails the build if any other module does (BUILD_PROMPT.md §4.2).
plugins {
    id("afterhours.android.library")
    id("afterhours.android.hilt")
}

android {
    namespace = "com.shnapps.couple.core.firebase"
}

dependencies {
    api(platform(libs.firebase.bom))
    api(libs.firebase.auth)
    api(libs.firebase.firestore)
    api(libs.firebase.storage)
    api(libs.firebase.functions)
    // Bridges Firebase Task<T> to suspend functions; needed by every Firebase data source.
    api(libs.kotlinx.coroutines.play.services)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.performance)
    implementation(libs.firebase.config)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)

    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:analytics"))
}
