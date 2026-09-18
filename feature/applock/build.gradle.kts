// The lock screen: biometric with a PIN fallback (BUILD_PROMPT.md §3.4, §57).
plugins {
    id("afterhours.android.feature")
}

android {
    namespace = "com.shnapps.couple.feature.applock"
}

dependencies {
    implementation(project(":core:security"))
    testImplementation(project(":core:datastore"))
    implementation(libs.androidx.biometric)
}
