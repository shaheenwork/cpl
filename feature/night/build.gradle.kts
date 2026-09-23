// Build Our Night and the experience player (BUILD_PROMPT.md sections 3.1, 14.5 and 14.6).
plugins {
    id("afterhours.android.feature")
}

android {
    namespace = "com.shnapps.couple.feature.night"
}

dependencies {
    // "Make it more…" is the engine's remixer, run on the phone over what the server released.
    implementation(project(":core:engine"))
}
