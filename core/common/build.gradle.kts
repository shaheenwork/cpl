// Result types, dispatchers, clock, id generation, extensions.
plugins {
    id("afterhours.jvm.library")
}

dependencies {
    // Only for qualifier annotations such as @ApplicationScope; pure JVM, no Android.
    api(libs.javax.inject)
}
