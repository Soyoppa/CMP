# kotlinx.serialization — keep generated serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class org.example.project.**$$serializer { *; }
-keepclassmembers class org.example.project.** { *** Companion; }
-keepclasseswithmembers class org.example.project.** { kotlinx.serialization.KSerializer serializer(...); }

# Ktor: engines are discovered via ServiceLoader; optional JVM integrations aren't on Android.
-keep class io.ktor.client.engine.okhttp.OkHttpEngineContainer
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**

# Firebase and Play services ship their own consumer rules — no blanket keeps needed.
