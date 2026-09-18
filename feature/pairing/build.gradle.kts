// Pairing two accounts into a couple: invite code, link, QR, and the confirmation
// handshake (BUILD_PROMPT.md section 8).
plugins {
    id("afterhours.android.feature")
}

android {
    namespace = "com.shnapps.couple.feature.pairing"
}

dependencies {
    // QR encoding only. Pure Java, no camera: scanning is left to the phone's own camera,
    // which opens the invite link the code encodes.
    implementation(libs.zxing.core)
}
