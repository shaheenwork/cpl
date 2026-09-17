// Pure JVM module: no Android SDK on the classpath. Used by :core:model, :core:common
// and, critically, :core:engine so the experience engine stays fast to test (§4.2).
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("afterhours.detekt")
}

private val catalog = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")
private val javaTarget = JavaVersion.toVersion(catalog.findVersion("javaTarget").get().requiredVersion)

java {
    sourceCompatibility = javaTarget
    targetCompatibility = javaTarget
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(javaTarget.toString()))
        allWarningsAsErrors.set(false)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    add("implementation", catalog.findLibrary("kotlinx-coroutines-core").get())
    add("testImplementation", catalog.findLibrary("junit").get())
    add("testImplementation", catalog.findLibrary("truth").get())
    add("testImplementation", catalog.findLibrary("kotlinx-coroutines-test").get())
    add("testImplementation", catalog.findLibrary("turbine").get())
    add("testImplementation", catalog.findLibrary("mockk").get())
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
