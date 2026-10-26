# Proguard rules for Brexa
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
