// The conversation between two paired phones, drawn over whatever else is on screen
// (CHAT_PROMPT.md). Hosted by :app above the nav host, so no screen has to know about it.
plugins {
    id("afterhours.android.feature")
}

android {
    namespace = "com.shnapps.couple.feature.chat"
}

dependencies {
    // The camera, the gallery picker and the microphone permission all come back through a
    // launcher, and a launcher belongs to the composition.
    implementation(libs.androidx.activity.compose)
}
