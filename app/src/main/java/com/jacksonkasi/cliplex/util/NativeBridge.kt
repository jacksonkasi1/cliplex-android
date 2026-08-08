package com.jacksonkasi.cliplex.util

object NativeBridge {
 init {
 System.loadLibrary("cliplex-native")
 }

 external fun getNativeVersion(): String
 external fun isNativeReady(): Boolean
 external fun whisperLoadModel(modelPath: String): Boolean
 external fun whisperTranscribe(samples: ShortArray, language: String, nThreads: Int): String?
 external fun whisperFreeModel()
}
