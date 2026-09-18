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
}
