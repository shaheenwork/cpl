// Android library that renders Compose UI.
// com.android.library is re-declared here so this script gets the typed `android { }`
// accessor; applying it twice is a no-op.
plugins {
    id("com.android.library")
    id("afterhours.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

private val catalog = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

android {
    buildFeatures {
        compose = true
    }
}

dependencies {
    val bom = catalog.findLibrary("androidx-compose-bom").get()
    add("implementation", platform(bom))
    add("androidTestImplementation", platform(bom))

    add("implementation", catalog.findLibrary("androidx-compose-ui").get())
    add("implementation", catalog.findLibrary("androidx-compose-ui-graphics").get())
    add("implementation", catalog.findLibrary("androidx-compose-ui-tooling-preview").get())
    add("implementation", catalog.findLibrary("androidx-compose-material3").get())
    add("implementation", catalog.findLibrary("androidx-compose-material-icons-extended").get())
    add("implementation", catalog.findLibrary("androidx-lifecycle-runtime-compose").get())

    add("debugImplementation", catalog.findLibrary("androidx-compose-ui-tooling").get())
    add("debugImplementation", catalog.findLibrary("androidx-compose-ui-test-manifest").get())
    add("androidTestImplementation", catalog.findLibrary("androidx-compose-ui-test-junit4").get())
}
