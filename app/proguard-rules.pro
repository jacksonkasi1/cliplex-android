# Add project specific rules here
-keep class com.jacksonkasi.cliplex.whisper.** { *; }
-keep class com.jacksonkasi.cliplex.capture.** { *; }
-keep class com.jacksonkasi.cliplex.util.NativeBridge { *; }

# Keep native method signatures
-keepclasseswithmembernames class * {
 native <methods>;
}

# Keep data classes
-keepclassmembers class com.jacksonkasi.cliplex.domain.model.** {
 <fields>;
}
-keepclassmembers class com.jacksonkasi.cliplex.data.model.** {
 <fields>;
}
