# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class app.focusus.launcher.**$$serializer { *; }
-keepclassmembers class app.focusus.launcher.** {
    *** Companion;
}
-keepclasseswithmembers class app.focusus.launcher.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Services and receivers are referenced from the manifest
-keep class app.focusus.launcher.service.** { *; }
