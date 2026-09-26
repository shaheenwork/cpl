// Playing before there is an account, a partner, or anything to set up (REDESIGN_PROMPT.md §5).
plugins {
    id("afterhours.android.feature")
}

android {
    namespace = "com.shnapps.couple.feature.instant"
}

dependencies {
    // The heat and the refusals this phone holds before there is anywhere else to keep them.
    implementation(project(":core:datastore"))
    implementation(project(":core:engine"))
    testImplementation(project(":core:datastore"))
}
