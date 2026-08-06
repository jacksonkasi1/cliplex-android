# Add project specific rules here
-keep class com.learnthis.whisper.** { *; }
-keep class com.learnthis.capture.** { *; }
-keep class com.learnthis.util.NativeBridge { *; }

# Keep native method signatures
-keepclasseswithmembernames class * {
 native <methods>;
}

# Keep data classes
-keepclassmembers class com.learnthis.domain.model.** {
 <fields>;
}
-keepclassmembers class com.learnthis.data.model.** {
 <fields>;
}
