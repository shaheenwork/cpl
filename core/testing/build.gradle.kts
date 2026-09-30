// Fakes, fixtures and test rules shared across modules.
plugins {
    id("afterhours.android.library")
}

android {
    namespace = "com.shnapps.couple.core.testing"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    api(project(":core:data"))
    api(project(":core:datastore"))
    // The game fake releases games the only way there is: ReleasedGame.fromServer.
    implementation(project(":core:engine"))
    // The launcher-icon fake stands in for the one that talks to the package manager (M8).
    api(project(":core:security"))
    api(libs.junit)
    api(libs.truth)
    api(libs.kotlin.test)
    api(libs.turbine)
    api(libs.kotlinx.coroutines.test)
}
