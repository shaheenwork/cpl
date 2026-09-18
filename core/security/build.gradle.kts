// App lock, FLAG_SECURE, boundary chokepoint client wrapper, App Check glue (§3).
plugins {
    id("afterhours.android.library")
    id("afterhours.android.hilt")
}

android {
    namespace = "com.shnapps.couple.core.security"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))
    implementation(libs.androidx.biometric)

    testImplementation(project(":core:testing"))
}
