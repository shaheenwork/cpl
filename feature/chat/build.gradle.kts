// The conversation between two paired phones, drawn over whatever else is on screen
// (CHAT_PROMPT.md). Hosted by :app above the nav host, so no screen has to know about it.
plugins {
    id("afterhours.android.feature")
}

android {
    namespace = "com.shnapps.couple.feature.chat"
}

dependencies {
    // A photo comes from the system picker or the camera; a voice note from :core:ui's recorder.
    implementation(libs.androidx.activity.compose)
}
