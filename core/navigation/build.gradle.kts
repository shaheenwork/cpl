// Type-safe route definitions and deep links (BUILD_PROMPT.md §4.2).
// Features never navigate to each other directly; they go through these routes.
plugins {
    id("afterhours.android.library.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.shnapps.couple.core.navigation"
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.androidx.navigation.compose)
}
