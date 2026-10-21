# Core library proguard rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
