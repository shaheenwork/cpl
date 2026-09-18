// Everything a :feature:* module needs. Feature modules may depend on :core:* only —
// never on another feature (BUILD_PROMPT.md §4.2, enforced by :architecture).
plugins {
    id("com.android.library")
    id("afterhours.android.library.compose")
    id("afterhours.android.hilt")
    // Every feature screen is screenshot-tested on the JVM (DECISIONS.md D-011).
    id("io.github.takahirom.roborazzi")
}

private val catalog = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

dependencies {
    add("implementation", project(":core:model"))
    add("implementation", project(":core:common"))
    add("implementation", project(":core:ui"))
    add("implementation", project(":core:designsystem"))
    add("implementation", project(":core:navigation"))
    add("implementation", project(":core:analytics"))
    add("implementation", project(":core:data"))

    add("implementation", catalog.findLibrary("androidx-lifecycle-runtime-compose").get())
    add("implementation", catalog.findLibrary("androidx-lifecycle-viewmodel-compose").get())
    add("implementation", catalog.findLibrary("androidx-hilt-navigation-compose").get())
    add("implementation", catalog.findLibrary("androidx-navigation-compose").get())

    add("testImplementation", project(":core:testing"))
    add("testImplementation", catalog.findLibrary("roborazzi").get())
    add("testImplementation", catalog.findLibrary("roborazzi-compose").get())
    add("testImplementation", catalog.findLibrary("roborazzi-junit-rule").get())
    add("testImplementation", catalog.findLibrary("androidx-compose-ui-test-junit4").get())
    add("testImplementation", catalog.findLibrary("androidx-junit").get())
}

// Roborazzi captures nothing unless a record/verify flag is set, so without this a visual
// regression in a feature screen would pass a green `check` (DECISIONS.md D-011).
tasks.named("check") {
    dependsOn("verifyRoborazziDebug")
}
