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
    implementation(project(":core:datastore"))
    implementation(project(":core:analytics"))
    implementation(libs.kotlinx.serialization.json)
}

// ---- The bundled taxonomy (BUILD_PROMPT.md §9.5) ---------------------------------------
// The source of truth is content/taxonomy.json, beside the rest of the authored content.
// This copies exactly that file into the assets, so what ships is always what the tests
// in this module validated.

val taxonomyFile: RegularFile = rootProject.layout.projectDirectory.file("content/taxonomy.json")

abstract class BundleTaxonomyTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val taxonomy: RegularFileProperty

    @get:OutputDirectory
    abstract val assetsDirectory: DirectoryProperty

    @TaskAction
    fun bundle() {
        val target = assetsDirectory.file("content/taxonomy.json").get().asFile
        target.parentFile.mkdirs()
        taxonomy.get().asFile.copyTo(target, overwrite = true)
    }
}

androidComponents {
    onVariants { variant ->
        // One task per variant: AGP wires each generated directory to its own location.
        val bundle = tasks.register<BundleTaxonomyTask>(
            "bundle${variant.name.replaceFirstChar { it.uppercase() }}Taxonomy",
        ) {
            taxonomy.set(taxonomyFile)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(bundle, BundleTaxonomyTask::assetsDirectory)
    }
}

tasks.withType<Test>().configureEach {
    // TaxonomyFileTest reads the real file from content/. Declared as an input so editing the
    // taxonomy re-runs the tests instead of leaving them UP-TO-DATE (DECISIONS.md D-015).
    inputs.file(taxonomyFile)
        .withPathSensitivity(PathSensitivity.NONE)
        .withPropertyName("taxonomy")
}
