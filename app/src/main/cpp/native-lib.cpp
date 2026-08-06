#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "LearnThisNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_learnthis_util_NativeBridge_getNativeVersion(
 JNIEnv *env,
 jobject /* this */
 ) {
 LOGI("Native library loaded - Phase 01 stub");
 return env->NewStringUTF("1.0.0-alpha01-native-stub");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_learnthis_util_NativeBridge_isNativeReady(
 JNIEnv *env,
 jobject /* this */
 ) {
 return JNI_TRUE;
}
