# CodeSide - keep rules added as needed.
# Model/profile serialization uses kotlinx.serialization.
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.codeside.**$$serializer { *; }
-keepclassmembers class com.codeside.** {
    *** Companion;
}
-keepclasseswithmembers class com.codeside.** {
    kotlinx.serialization.KSerializer serializer(...);
}
