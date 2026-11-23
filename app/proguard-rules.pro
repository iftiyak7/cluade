# Hayat release shrinking rules.
# Navigation type-safe routes and backup DTOs use kotlinx.serialization.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** Companion;
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.hayat.**$$serializer { *; }
# Enums are stored by name in Room and DataStore: names must survive obfuscation.
-keepclassmembers enum com.hayat.core.model.** { <fields>; public static **[] values(); public static ** valueOf(java.lang.String); }
# Adhan uses no reflection; keep its public API for clarity of stack traces.
-keep class com.batoulapps.adhan.** { public *; }
# Never log in release.
-assumenosideeffects class android.util.Log { public static int v(...); public static int d(...); public static int i(...); }
