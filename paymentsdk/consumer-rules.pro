# Consumer ProGuard rules applied to any app that depends on this SDK.

# Public API surface — keep names stable for merchants integrating against us.
-keep public class com.pgsdk.core.** { public *; }
-keep public class com.pgsdk.model.** { public *; }
-keep public interface com.pgsdk.core.PGPaymentCallback { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.pgsdk.**$$serializer { *; }
-keepclassmembers class com.pgsdk.** {
    *** Companion;
}
-keepclasseswithmembers class com.pgsdk.** {
    kotlinx.serialization.KSerializer serializer(...);
}
