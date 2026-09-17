// Theme, tokens, motion and the component inventory (§15).
plugins {
    id("afterhours.android.library.compose")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.shnapps.couple.core.designsystem"
}

dependencies {
    // IntensityDial, BoundarySlider and PreferenceSwipeCard express domain concepts
    // directly (Intensity 1..5, the five boundary levels, the five preference answers).
    // Typing them beats stringly-typed parameters, and :core:model is pure Kotlin with no
    // Android or Firebase dependency, so nothing leaks into the design system.
    api(project(":core:model"))

    implementation(libs.androidx.compose.material.icons.extended)

    // Screenshot tests run on the JVM via Robolectric, so the design system is verifiable
    // without a device — which matters here, since the emulator needs a hypervisor this
    // machine does not yet have (HUMAN_SETUP.md section 1.3).
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.junit)
}

// Without this, `check` runs the snapshot tests but Roborazzi captures nothing unless a
// record/verify flag is set — so a visual regression would sail through a green build.
// Wiring the verify task in makes the goldens a real gate.
//
// Re-record after an intentional visual change:
//   ./gradlew :core:designsystem:recordRoborazziDebug
tasks.named("check") {
    dependsOn("verifyRoborazziDebug")
}
