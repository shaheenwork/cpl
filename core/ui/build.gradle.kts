// Shared stateful composables, SecureScreen, and the scaffolding screens share: the
// scrolling column, the heading, the back row and the error banner.
plugins {
    id("afterhours.android.library.compose")
    // The voice recorder is bound here: both players record a round the same way, so the binding
    // lives with the thing rather than with one of the features that uses it.
    id("afterhours.android.hilt")
}

android {
    namespace = "com.shnapps.couple.core.ui"
}

dependencies {
    implementation(project(":core:designsystem"))
    // The primitives every game is played through: rendered here so both players — the session
    // one and the local one — show the same thing (BUILD_PROMPT.md §11.1).
    api(project(":core:engine"))
    // Outcome/AppError: a refusal to record is an error like any other.
    implementation(project(":core:common"))
    // PremiumNudge says which limit was reached, in the words the model holds (§19).
    api(project(":core:model"))

    // SecureScreenTest composes on a real Robolectric activity window.
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.junit)
}
