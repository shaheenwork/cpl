// Room entities, DAOs and migrations.
plugins {
    id("afterhours.android.library")
    id("afterhours.android.hilt")
}

android {
    namespace = "com.shnapps.couple.core.database"
}

ksp {
    // The schema of every released database version is committed, so a migration can be
    // written against what phones actually have (and tested, once there is one).
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}
