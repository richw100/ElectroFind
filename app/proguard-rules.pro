# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

# Gson deserialises these by reflection — field names must survive shrinking
-keep class com.richwatson.electrofind.api.models.** { *; }
-keep class com.richwatson.electrofind.model.** { *; }
-keep class com.richwatson.electrofind.repository.ChargerRepository$Photon* { *; }

# Gson generic-type plumbing. The anonymous `object : TypeToken<List<Trip>>() {}` subclasses
# used throughout this codebase need their generic signature preserved at runtime so Gson can
# read the type argument reflectively — a plain `-keep` on the class isn't enough for this;
# R8's class-merging pass can still collapse the anonymous subclass into its supertype and lose
# the signature, causing "TypeToken must be created with a type argument" at runtime. This is
# Google's own documented fix for Gson + R8 (gson/UPGRADING.md).
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type
-dontwarn sun.misc.**

# pdfbox-android (com.tom-roush) — the receipt PDF parser. Keep its classes and silence
# warnings about the optional JPEG2000 dependency it never actually loads on Android.
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.**
-dontwarn com.gemalto.jp2.**

# WorkManager instantiates Worker/CoroutineWorker subclasses reflectively via this
# constructor — R8 can otherwise strip or rename it, silently breaking background work
# (RefreshChargersWorker, AutoBackupWorker) with no crash, just work that never runs.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
