# ==========================================
# Proguard rules for Brexa
# ==========================================

# General Attributes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}
-dontwarn androidx.room.paging.**

# Brexa Domain Entities, Models & Serialization
-keep class com.brexa.app.data.local.entity.** { *; }
-keepclassmembers class com.brexa.app.data.local.entity.** { *; }
-keep class com.brexa.app.domain.model.** { *; }
-keepclassmembers class com.brexa.app.domain.model.** { *; }
-keep class com.brexa.app.data.repository.BackupPayload { *; }
-keepclassmembers class com.brexa.app.data.repository.BackupPayload { *; }
-keep class com.brexa.app.data.repository.CategoryDistribution { *; }
-keepclassmembers class com.brexa.app.data.repository.CategoryDistribution { *; }
-keep class com.brexa.app.data.sync.UpdateInfo { *; }
-keepclassmembers class com.brexa.app.data.sync.UpdateInfo { *; }
-keep class com.brexa.app.data.sync.DriveBackupInfo { *; }
-keepclassmembers class com.brexa.app.data.sync.DriveBackupInfo { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepclassmembers enum * { *; }
-dontwarn sun.misc.**

# Google Play Services & Auth
-keep class com.google.android.gms.auth.api.signin.** { *; }
-keep class com.google.android.gms.common.api.** { *; }

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
