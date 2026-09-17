// Static analysis shared by every module. detekt-formatting wraps the ktlint rule set,
// so one tool covers both lint and formatting (BUILD_PROMPT.md §1.1).
plugins {
    id("io.gitlab.arturbosch.detekt")
}

private val catalog = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    baseline = rootProject.file("config/detekt/baseline.xml").takeIf { it.exists() }
    parallel = true
}

dependencies {
    add("detektPlugins", catalog.findLibrary("detekt-formatting").get())
}

// detekt's own Gradle plugin derives `jvmTarget` and `languageVersion` from the Kotlin
// plugin inside an afterEvaluate hook. AGP 9's built-in Kotlin support does not expose
// what that lookup expects, so detekt falls back to the raw JDK version string
// ("25.0.2"), which JvmTarget.fromString cannot parse — the task then dies with a bare
// `IllegalArgumentException: 25.0.2`.
//
// Overriding these has to happen in a LATER afterEvaluate than detekt's own, otherwise
// detekt's configureEach action runs second and wins.
//
// detekt 1.23.8 embeds the Kotlin 2.0.x compiler, hence language version 2.0 rather
// than the 2.2 the project itself compiles with. detekt runs without type resolution,
// so this only affects parsing.
afterEvaluate {
    val target = catalog.findVersion("javaTarget").get().requiredVersion
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = target
        languageVersion = "2.0"
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
        }
    }
    tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
        jvmTarget = target
        languageVersion = "2.0"
    }
}
