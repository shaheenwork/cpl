// Repositories and use cases. The layer that turns data sources into domain behaviour.
plugins {
    id("afterhours.android.library")
    id("afterhours.android.hilt")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.shnapps.couple.core.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    implementation(project(":core:firebase"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:analytics"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.androidx.work.testing)
}

// ---- The shipped content bundle (BUILD_PROMPT.md §9.2) ----------------------------------
// The source of truth is content/, validated by tools/validate-content in `check`. This
// copies the committed, validated bundle into the assets, so first launch and a device that
// has never been online both have the full catalogue — and the taxonomy inside it (§9.5).

val contentDirectory: Directory = rootProject.layout.projectDirectory.dir("content")
val bundleFile: RegularFile = contentDirectory.file("dist/bundle.json")

abstract class ShipContentBundleTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val bundle: RegularFileProperty

    @get:OutputDirectory
    abstract val assetsDirectory: DirectoryProperty

    @TaskAction
    fun ship() {
        val target = assetsDirectory.file("content/bundle.json").get().asFile
        target.parentFile.mkdirs()
        bundle.get().asFile.copyTo(target, overwrite = true)
    }
}

androidComponents {
    onVariants { variant ->
        // One task per variant: AGP wires each generated directory to its own location.
        val ship = tasks.register<ShipContentBundleTask>(
            "ship${variant.name.replaceFirstChar { it.uppercase() }}ContentBundle",
        ) {
            bundle.set(bundleFile)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(ship, ShipContentBundleTask::assetsDirectory)
    }
}

tasks.withType<Test>().configureEach {
    // The file tests read the real content: the bundle, the taxonomy, the vocabulary and the
    // policy. Declared as inputs so editing any of them re-runs those tests instead of
    // leaving them UP-TO-DATE (DECISIONS.md D-015).
    inputs.files(
        bundleFile,
        contentDirectory.file("content.json"),
        contentDirectory.file("taxonomy.json"),
        contentDirectory.file("vocabulary.json"),
        contentDirectory.file("policy/prohibited-terms.json"),
    )
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("content")
}
