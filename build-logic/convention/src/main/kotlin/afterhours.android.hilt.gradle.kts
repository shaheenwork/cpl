// Hilt + KSP. No `android { }` access needed, so this is safe to apply to both the
// application module and library modules.
plugins {
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

private val catalog = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

dependencies {
    add("implementation", catalog.findLibrary("hilt-android").get())
    add("ksp", catalog.findLibrary("hilt-compiler").get())
}
