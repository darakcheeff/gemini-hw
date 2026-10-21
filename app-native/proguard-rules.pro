# Lightweight rules for app-native
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class com.gemini.hw.core.** { *; }
