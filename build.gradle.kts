// Top-level build file. Plugins are declared here with `apply false` so that the
// convention plugins in build-logic/ can apply them by id without restating versions.
plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.roborazzi) apply false
}

// ---- The content gate (BUILD_PROMPT.md §9.4) --------------------------------------------
// tools/validate-content fails the build on invalid content, and on a committed bundle that
// no longer matches the packs it was built from. The tooling is TypeScript on Node, like the
// Cloud Functions; the app build never runs it, only `check` does (DECISIONS.md D-039).

val npm: List<String> =
    if (System.getProperty("os.name").lowercase().contains("windows")) listOf("cmd", "/c", "npm") else listOf("npm")

val installContentTools = tasks.register<Exec>("installContentTools") {
    description = "Installs the content tools' dependencies (npm ci in tools/)."
    workingDir = file("tools")
    commandLine(npm + listOf("ci", "--no-audit", "--no-fund"))
    inputs.file("tools/package-lock.json").withPathSensitivity(PathSensitivity.RELATIVE)
    // npm's own record of what it installed; hashing all of node_modules would be slow.
    outputs.file("tools/node_modules/.package-lock.json")
}

val contentToolSources: FileCollection = files("tools/src", "tools/package.json", "tools/tsconfig.json")

val validateContent = tasks.register<Exec>("validateContent") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Validates content/ and checks that content/dist/bundle.json is current."
    dependsOn(installContentTools)
    workingDir = file("tools")
    commandLine(npm + listOf("run", "--silent", "validate"))
    inputs.dir("content").withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("content")
    inputs.files(contentToolSources).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("tools")
    val stamp = layout.buildDirectory.file("content/validated.stamp")
    outputs.file(stamp)
    doLast { stamp.get().asFile.writeText("content valid\n") }
}

val testContentTools = tasks.register<Exec>("testContentTools") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the content tools' own tests (node:test)."
    dependsOn(installContentTools)
    workingDir = file("tools")
    commandLine(npm + listOf("test", "--silent"))
    inputs.dir("content").withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("content")
    inputs.files(contentToolSources).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("tools")
    val stamp = layout.buildDirectory.file("content/tools-tested.stamp")
    outputs.file(stamp)
    doLast { stamp.get().asFile.writeText("tools tests passed\n") }
}

tasks.named("check") {
    dependsOn(validateContent, testContentTools)
}
