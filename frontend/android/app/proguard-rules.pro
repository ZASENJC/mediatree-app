# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
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

# libplayer.so calls the mpv bridge methods by their JVM names. Keep the object
# and callback names stable when R8 is enabled for release builds.
-keep class is.xyz.mpv.MPVLib { *; }

-keepclasseswithmembernames class * {
    native <methods>;
}

# Optional metadata and authentication paths referenced by transitive libraries.
# The app uses password-based SMB auth and AndroidX Security Crypto; these
# classes are not part of the Android runtime and are not needed at runtime.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
