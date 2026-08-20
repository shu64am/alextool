# Full obfuscation: rename all app classes, methods and fields so decompiled output is unreadable.
# Only the entry points Android and reflection actually need are kept below.
-keepattributes !SourceFile,!LineNumberTable
-optimizationpasses 2
-dontusemixedcaseclassnames

# Android entry points must keep their original names so the system can find them.
# Everything inside (method bodies, local variables) is still obfuscated/optimized.
-keepnames class com.alexmodzofc.tool.app.AlexToolApplication
-keepnames class com.alexmodzofc.tool.browser.MainActivity
-keepnames class com.alexmodzofc.tool.setup.SetupActivity
-keep class * extends android.app.Activity { <init>(...); }
-keep class * extends android.app.Service { <init>(...); }
-keep class * extends android.content.BroadcastReceiver { <init>(...); }
-keep class * extends android.content.ContentProvider { <init>(...); }
-keep class * extends androidx.work.Worker { <init>(android.content.Context, androidx.work.WorkerParameters); }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-keep class android.webkit.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep public class com.caverock.androidsvg.** { *; }
-keep class com.google.flatbuffers.** { *; }
-dontwarn com.google.flatbuffers.**
# Keep launcher icons and drawables referenced from Kotlin code
-keepnames class com.alexmodzofc.tool.R$drawable
-keepnames class com.alexmodzofc.tool.R$mipmap
