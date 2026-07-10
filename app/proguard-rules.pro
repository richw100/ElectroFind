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

# Gson generic-type plumbing (TypeToken subclasses lose their type argument otherwise)
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type
-dontwarn sun.misc.**

# WorkManager instantiates Worker/CoroutineWorker subclasses reflectively via this
# constructor — R8 can otherwise strip or rename it, silently breaking background work
# (RefreshChargersWorker, AutoBackupWorker) with no crash, just work that never runs.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
