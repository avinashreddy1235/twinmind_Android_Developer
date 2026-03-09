# Add project specific ProGuard rules here.
-keepattributes *Annotation*

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.twinmind.app.data.local.entity.** { *; }
-keep class com.twinmind.app.domain.model.** { *; }

# Retrofit
-keepattributes Signature, InnerClasses

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class * extends androidx.room.RoomDatabase
