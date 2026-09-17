// Base Android library convention. AGP 9 supplies Kotlin support built in, so the
// Kotlin Android plugin is deliberately NOT applied here (see DECISIONS.md D-002).
plugins {
    id("com.android.library")
    id("afterhours.detekt")
}

private val catalog = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")
private val javaTarget = JavaVersion.toVersion(catalog.findVersion("javaTarget").get().requiredVersion)

android {
    compileSdk {
        version = release(catalog.findVersion("compileSdk").get().requiredVersion.toInt())
    }

    defaultConfig {
        minSdk = catalog.findVersion("minSdk").get().requiredVersion.toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = javaTarget
        targetCompatibility = javaTarget
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    add("implementation", catalog.findLibrary("kotlinx-coroutines-core").get())
    add("implementation", catalog.findLibrary("kotlinx-coroutines-android").get())

    add("testImplementation", catalog.findLibrary("junit").get())
    add("testImplementation", catalog.findLibrary("truth").get())
    add("testImplementation", catalog.findLibrary("kotlinx-coroutines-test").get())
    add("testImplementation", catalog.findLibrary("turbine").get())
    add("testImplementation", catalog.findLibrary("mockk").get())
    add("testImplementation", catalog.findLibrary("robolectric").get())

    add("androidTestImplementation", catalog.findLibrary("androidx-junit").get())
    add("androidTestImplementation", catalog.findLibrary("androidx-espresso-core").get())
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
