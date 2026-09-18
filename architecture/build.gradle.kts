// Konsist architecture rules. These fail the build on layering violations so that the
// module graph in BUILD_PROMPT.md §4.2 and the privacy chokepoints in §5/§17 stay real
// rather than aspirational.
plugins {
    id("afterhours.jvm.library")
}

dependencies {
    testImplementation(libs.konsist)
}

// Konsist reads every Kotlin source in the repository at runtime, which Gradle cannot see.
// Without declaring those files as inputs, this task counts as UP-TO-DATE after any change
// made outside this module — so the architecture rules silently never ran on the builds
// that mattered. Found by planting a feature-to-feature import and watching `check` pass
// (DECISIONS.md D-015).
tasks.withType<Test>().configureEach {
    inputs.files(
        rootProject.fileTree(rootProject.projectDir) {
            include("**/src/**/*.kt")
            exclude("**/build/**", "build-logic/**")
        },
    )
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("konsistScannedSources")
}
