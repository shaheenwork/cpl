// DataStore-backed settings and session-local flags.
plugins {
    id("afterhours.android.library")
    id("afterhours.android.hilt")
}

android {
    namespace = "com.shnapps.couple.core.datastore"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.androidx.datastore.preferences)

    // The real store, against real DataStore. ApplicationProvider comes with androidx.test.ext,
    // and Robolectric itself comes from the library convention.
    testImplementation(libs.androidx.junit)
}
