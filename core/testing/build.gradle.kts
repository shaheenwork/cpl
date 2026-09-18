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
    api(libs.junit)
    api(libs.truth)
    api(libs.kotlin.test)
    api(libs.turbine)
    api(libs.mockk)
    api(libs.kotlinx.coroutines.test)
}
