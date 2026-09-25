# R8 keep rules (BUILD_PROMPT.md §18, Phase 22).
#
# Most of this app needs nothing: Hilt, Room and Compose generate code that R8 understands, and
# every Firestore document is mapped by hand in :core:firebase rather than reflected into a class.
# What is left is the handful of places where something outside our code looks ours up by name.

# --- kotlinx.serialization ---------------------------------------------------------------
# The content bundle and the taxonomy are @Serializable. The library ships its own rules, and
# these cover the generated serializers R8 cannot see being used.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.shnapps.couple.** {
    *** Companion;
}
-keepclasseswithmembers class com.shnapps.couple.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.shnapps.couple.**$$serializer { *; }

# --- Firebase Cloud Messaging -----------------------------------------------------------
# The push SDK instantiates the service by name from the manifest.
-keep class com.shnapps.couple.core.firebase.notifications.CplMessagingService { *; }

# --- Crashlytics ------------------------------------------------------------------------
# Line numbers and source files, so a stack trace in the console means something. The mapping
# file is uploaded by the Crashlytics plugin (HUMAN_SETUP.md §2.3).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
