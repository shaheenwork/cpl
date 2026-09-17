// Konsist architecture rules. These fail the build on layering violations so that the
// module graph in BUILD_PROMPT.md §4.2 and the privacy chokepoints in §5/§17 stay real
// rather than aspirational.
plugins {
    id("afterhours.jvm.library")
}

dependencies {
    testImplementation(libs.konsist)
}
