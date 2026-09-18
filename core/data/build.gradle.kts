// Repositories and use cases. The layer that turns data sources into domain behaviour.
plugins {
    id("afterhours.android.library")
    id("afterhours.android.hilt")
}

android {
    namespace = "com.shnapps.couple.core.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    implementation(project(":core:firebase"))
    implementation(project(":core:datastore"))
    implementation(project(":core:analytics"))
}
