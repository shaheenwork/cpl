// THE EXPERIENCE ENGINE (§10). Pure JVM and deterministic so it is fast to test.
plugins {
    id("afterhours.jvm.library")
}

dependencies {
    api(project(":core:model"))

    // The shared vectors and the real catalogue are JSON; only the tests read them.
    testImplementation(libs.kotlinx.serialization.json)
}

tasks.withType<Test>().configureEach {
    // The engine tests read files Gradle cannot otherwise see: the vectors shared with the
    // server's TypeScript engine, the spec's catalogue of record, and the shipped bundle.
    // Declared, so changing any of them re-runs the tests (DECISIONS.md D-015).
    inputs.files(
        rootProject.layout.projectDirectory.file("engine/engine-fixtures.json"),
        rootProject.layout.projectDirectory.file("content/dist/bundle.json"),
    )
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("engineVectors")
}
