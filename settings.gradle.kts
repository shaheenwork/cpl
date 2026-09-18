pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "cpl"

include(":app")

// --- core ---------------------------------------------------------------
// Pure JVM modules. These must compile with no Android SDK on the classpath
// (BUILD_PROMPT.md §4.2) so the engine stays fast to test.
include(":core:model")
include(":core:common")
include(":core:engine")

// Android modules.
include(":core:designsystem")
include(":core:ui")
include(":core:navigation")
include(":core:data")
include(":core:firebase")
include(":core:database")
include(":core:datastore")
include(":core:security")
include(":core:analytics")
include(":core:notifications")
include(":core:testing")

// --- architecture enforcement -------------------------------------------
// Konsist rules that fail the build on layering violations (BUILD_PROMPT.md §4.2).
include(":architecture")

// --- feature ------------------------------------------------------------
// Feature modules are added by the phase that introduces them; see DECISIONS.md D-006.
include(":feature:onboarding")
include(":feature:auth")
include(":feature:applock")
include(":feature:pairing")
include(":feature:preferences")
include(":feature:boundaries")
