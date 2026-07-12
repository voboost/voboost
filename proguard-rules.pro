# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep voboost-config classes
-keep class ru.voboost.config.** { *; }

# Keep Compose classes
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- APK size reduction: R8 (isMinifyEnabled = true) keep rules ---

# Kotlin reflection metadata required by Hoplite YAML decoding of config models.
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod,Exceptions
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# Hoplite + SnakeYAML config parsing (voboost-config). Config model classes are
# kept by voboost-config's consumer-rules.pro; silence optional-dependency warnings.
-dontwarn com.sksamuel.hoplite.**
-dontwarn org.yaml.snakeyaml.**

# OkHttp / Okio reference optional TLS providers absent on Android.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.jsse.**

# BouncyCastle powers OTA Ed25519 signature verification. Keep it intact so R8
# never strips a class the signature path reaches; the unused Picnic/LowMC data
# files are dropped via packaging{} excludes instead.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Preserve feature class simple names used in diagnostic logs.
-keepnames class ru.voboost.feature.** { *; }