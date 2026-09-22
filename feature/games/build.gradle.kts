// Games (BUILD_PROMPT.md section 11): the games hub, and one player for every game — a
// renderer per primitive, never one per game.
plugins {
    id("afterhours.android.feature")
}

android {
    namespace = "com.shnapps.couple.feature.games"
}

dependencies {
    // The game interpreter: every game is played by it, and only by it.
    implementation(project(":core:engine"))
}
