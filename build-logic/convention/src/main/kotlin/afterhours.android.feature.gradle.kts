// Everything a :feature:* module needs. Feature modules may depend on :core:* only —
// never on another feature (BUILD_PROMPT.md §4.2, enforced by :architecture).
plugins {
    id("com.android.library")
    id("afterhours.android.library.compose")
    id("afterhours.android.hilt")
}

private val catalog = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

dependencies {
    add("implementation", project(":core:model"))
    add("implementation", project(":core:common"))
    add("implementation", project(":core:ui"))
    add("implementation", project(":core:designsystem"))
    add("implementation", project(":core:navigation"))
    add("implementation", project(":core:analytics"))

    add("implementation", catalog.findLibrary("androidx-lifecycle-runtime-compose").get())
    add("implementation", catalog.findLibrary("androidx-lifecycle-viewmodel-compose").get())
    add("implementation", catalog.findLibrary("androidx-hilt-navigation-compose").get())
    add("implementation", catalog.findLibrary("androidx-navigation-compose").get())

    add("testImplementation", project(":core:testing"))
}
