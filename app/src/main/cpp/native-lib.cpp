#include <jni.h>
#include <algorithm>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "LearnThisNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
std::mutex g_mutex;
whisper_context *g_context = nullptr;

std::string json_escape(const char *value) {
    std::string output;
    if (value == nullptr) return output;
    for (const unsigned char c : std::string(value)) {
        switch (c) {
            case '"': output += "\\\""; break;
            case '\\': output += "\\\\"; break;
            case '\b': output += "\\b"; break;
            case '\f': output += "\\f"; break;
            case '\n': output += "\\n"; break;
            case '\r': output += "\\r"; break;
            case '\t': output += "\\t"; break;
            default:
                if (c < 0x20) {
                    char encoded[7];
                    snprintf(encoded, sizeof(encoded), "\\u%04x", c);
                    output += encoded;
                } else {
                    output += static_cast<char>(c);
                }
        }
    }
    return output;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_learnthis_util_NativeBridge_getNativeVersion(
 JNIEnv *env,
 jobject /* this */
 ) {
 return env->NewStringUTF(whisper_print_system_info());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_learnthis_util_NativeBridge_isNativeReady(
 JNIEnv *env,
 jobject /* this */
 ) {
 return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_learnthis_util_NativeBridge_whisperLoadModel(
        JNIEnv *env, jobject, jstring model_path) {
    if (model_path == nullptr) return JNI_FALSE;
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) return JNI_FALSE;

    std::lock_guard<std::mutex> guard(g_mutex);
    whisper_context_params params = whisper_context_default_params();
    whisper_context *loaded = whisper_init_from_file_with_params(path, params);
    env->ReleaseStringUTFChars(model_path, path);
    if (loaded == nullptr) {
        LOGE("Failed to load Whisper model");
        return JNI_FALSE;
    }
    if (g_context != nullptr) whisper_free(g_context);
    g_context = loaded;
    LOGI("Whisper model loaded");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_learnthis_util_NativeBridge_whisperTranscribe(
        JNIEnv *env, jobject, jshortArray samples, jstring language, jint n_threads) {
    if (samples == nullptr) return nullptr;
    const jsize count = env->GetArrayLength(samples);
    if (count < 1600) return env->NewStringUTF("{\"language\":\"\",\"segments\":[]}");

    std::vector<jshort> pcm16(static_cast<size_t>(count));
    env->GetShortArrayRegion(samples, 0, count, pcm16.data());
    if (env->ExceptionCheck()) return nullptr;
    std::vector<float> pcm(static_cast<size_t>(count));
    std::transform(pcm16.begin(), pcm16.end(), pcm.begin(),
                   [](jshort sample) { return static_cast<float>(sample) / 32768.0f; });

    std::string language_value = "auto";
    if (language != nullptr) {
        const char *chars = env->GetStringUTFChars(language, nullptr);
        if (chars != nullptr) {
            language_value = chars;
            env->ReleaseStringUTFChars(language, chars);
        }
    }

    std::lock_guard<std::mutex> guard(g_mutex);
    if (g_context == nullptr) {
        LOGE("Transcription requested without a loaded model");
        return nullptr;
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = std::max(1, static_cast<int>(n_threads));
    params.translate = false;
    params.no_context = true;
    params.no_timestamps = false;
    params.single_segment = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.language = language_value.empty() ? "auto" : language_value.c_str();
    params.detect_language = language_value.empty() || language_value == "auto";
    params.suppress_blank = true;

    whisper_reset_timings(g_context);
    const int result = whisper_full(g_context, params, pcm.data(), static_cast<int>(pcm.size()));
    if (result != 0) {
        LOGE("whisper_full failed: %d", result);
        return nullptr;
    }

    const int language_id = whisper_full_lang_id(g_context);
    const char *detected_language = language_id >= 0 ? whisper_lang_str(language_id) : "";
    std::ostringstream json;
    json << "{\"language\":\"" << json_escape(detected_language) << "\",\"segments\":[";
    const int segment_count = whisper_full_n_segments(g_context);
    for (int i = 0; i < segment_count; ++i) {
        if (i > 0) json << ',';
        json << "{\"start\":" << whisper_full_get_segment_t0(g_context, i) * 10
             << ",\"end\":" << whisper_full_get_segment_t1(g_context, i) * 10
             << ",\"text\":\"" << json_escape(whisper_full_get_segment_text(g_context, i))
             << "\",\"noSpeechProb\":" << whisper_full_get_segment_no_speech_prob(g_context, i)
             << '}';
    }
    json << "]}";
    return env->NewStringUTF(json.str().c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_learnthis_util_NativeBridge_whisperFreeModel(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> guard(g_mutex);
    if (g_context != nullptr) {
        whisper_free(g_context);
        g_context = nullptr;
    }
}
