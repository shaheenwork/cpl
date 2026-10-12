import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("afterhours.android.hilt")
    id("afterhours.detekt")
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// The upload key, when this machine has one (HUMAN_SETUP.md §3.3). It lives in `keystore.properties`
// at the repository root, which is git-ignored; `keystore.properties.example` shows the four lines.
// Without the file a release build is still signed, with the debug key, so it can be installed and
// run locally but can never be uploaded to Play.
val uploadKey: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.isFile }
    ?.let { file -> Properties().also { properties -> file.inputStream().use(properties::load) } }

android {
    namespace = "com.shnapps.couple"

    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt())
    }

    defaultConfig {
        applicationId = "com.shnapps.couple"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // Play refuses an upload whose versionCode it has already seen. Raise it for each one with
        // `-PversionCode=2` rather than editing this file.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = "1.0"

        testInstrumentationRunner = "com.shnapps.couple.HiltTestRunner"
    }

    // BUILD_PROMPT.md §4.3. `dev` must work with no human setup beyond
    // `firebase emulators:start`; the suffixed application ids let all three
    // environments sit on one device at the same time.
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("boolean", "USE_FIREBASE_EMULATOR", "true")
            buildConfigField("String", "ENVIRONMENT", "\"dev\"")
            // Where the Emulator Suite is, as the phone can see it. 10.0.2.2 is the Android
            // emulator's alias for this machine and means nothing on a real device, so a phone
            // on the same network is given the machine's own address instead:
            //
            //     ./gradlew installDevDebug -PdevEmulatorHost=192.168.1.20
            //
            // and `firebase emulators:start` has to be listening on more than localhost, which
            // firebase.json now sets (D-134).
            buildConfigField(
                "String",
                "DEV_EMULATOR_HOST",
                "\"${project.findProperty("devEmulatorHost") ?: "10.0.2.2"}\"",
            )
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("boolean", "USE_FIREBASE_EMULATOR", "false")
            buildConfigField("String", "ENVIRONMENT", "\"staging\"")
            buildConfigField("String", "DEV_EMULATOR_HOST", "\"\"")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("boolean", "USE_FIREBASE_EMULATOR", "false")
            buildConfigField("String", "ENVIRONMENT", "\"prod\"")
            buildConfigField("String", "DEV_EMULATOR_HOST", "\"\"")
        }
    }

    signingConfigs {
        if (uploadKey != null) {
            create("upload") {
                storeFile = rootProject.file(uploadKey.getProperty("storeFile"))
                storePassword = uploadKey.getProperty("storePassword")
                keyAlias = uploadKey.getProperty("keyAlias")
                keyPassword = uploadKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 on, with the keep rules in proguard-rules.pro (§18). Most of this app needs
            // none: documents are mapped by hand, and Hilt, Room and Compose generate code R8
            // understands. What is kept is named there, with why.
            optimization {
                enable = true
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                file("proguard-rules.pro"),
            )
            // The upload key when `keystore.properties` exists. Otherwise local verification only:
            // a release build has to be signable to be installed and run at all (the Phase 22 exit
            // criterion), but one signed this way cannot go to Play.
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.javaTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.javaTarget.get())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The store this build talks to (§4.3, D-091). `dev` gets a fake one, so everything below
    // billing can be exercised with no Play account; staging and prod share the real client.
    sourceSets {
        getByName("staging") { kotlin.srcDir("src/play/kotlin") }
        getByName("prod") { kotlin.srcDir("src/play/kotlin") }
    }
}

/**
 * Disable the staging and prod variants until their google-services.json is present.
 *
 * Without this, `./gradlew check` fails on a fresh clone because the Google Services
 * plugin refuses to run without a config file — even though `dev` needs none, since it
 * talks to the Firebase Emulator Suite (BUILD_PROMPT.md §4.3).
 *
 * The variants re-enable themselves the moment a human drops the real files in
 * (HUMAN_SETUP.md §2.2). Silently skipping the plugin instead would produce a staging or
 * prod build that looks fine and cannot reach Firebase at runtime.
 */
androidComponents {
    beforeVariants { variant ->
        val environment = variant.productFlavors
            .firstOrNull { (dimension, _) -> dimension == "environment" }
            ?.second
            ?: return@beforeVariants

        if (environment != "dev" && !file("src/$environment/google-services.json").exists()) {
            logger.lifecycle(
                "Disabling '${variant.name}': app/src/$environment/google-services.json " +
                    "is missing. See HUMAN_SETUP.md section 2.2.",
            )
            variant.enable = false
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":core:analytics"))
    implementation(project(":core:security"))
    implementation(project(":core:data"))
    implementation(project(":core:firebase"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:instant"))
    implementation(project(":feature:applock"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:pairing"))
    implementation(project(":feature:boundaries"))
    implementation(project(":feature:games"))
    implementation(project(":feature:home"))
    implementation(project(":feature:settings"))
    implementation(project(":core:notifications"))
    implementation(project(":feature:store"))

    // Only the builds that talk to Play: `dev` has a fake client and never loads the library.
    "stagingImplementation"(libs.play.billing)
    "prodImplementation"(libs.play.billing)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    // MainActivity is a FragmentActivity so BiometricPrompt can attach (§3.4).
    implementation(libs.androidx.fragment.ktx)
    // CplApplication hands WorkManager Hilt's worker factory (ContentSyncWorker).
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)

    // §18 asks for a full run-through with no retained objects. Debug builds only: LeakCanary
    // installs itself from its own content provider, so nothing in this app references it.
    debugImplementation(libs.leakcanary)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
