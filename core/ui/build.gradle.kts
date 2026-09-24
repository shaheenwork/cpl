// Shared stateful composables, SecureScreen, and the scaffolding screens share: the
// scrolling column, the heading, the back row and the error banner.
plugins {
    id("afterhours.android.library.compose")
}

android {
    namespace = "com.shnapps.couple.core.ui"
}

dependencies {
    implementation(project(":core:designsystem"))
    // PremiumNudge says which limit was reached, in the words the model holds (§19).
    api(project(":core:model"))

    // SecureScreenTest composes on a real Robolectric activity window.
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.junit)
}
