# Aura Chat / OkHttp / Room
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# PDFBox-Android
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.apache.**
-dontwarn java.awt.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**
