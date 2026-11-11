# Pleos SDKs talk to vehicle services over AIDL and use reflection internally.
-keep class ai.pleos.** { *; }
-keep interface ai.pleos.** { *; }
-dontwarn ai.pleos.**

# Strip debug logging calls that might survive inlining.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# The Pleos SDK parses capability results with Gson TypeToken, which needs generic signatures.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
