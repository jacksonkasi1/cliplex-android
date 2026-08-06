package com.learnthis.util

object NativeBridge {
 init {
 System.loadLibrary("learn-this-native")
 }

 external fun getNativeVersion(): String
 external fun isNativeReady(): Boolean
}
