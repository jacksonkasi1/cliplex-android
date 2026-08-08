#include <jni.h>

#include <algorithm>
#include <chrono>
#include <cctype>
#include <cstdint>
#include <cstdio>
#include <iomanip>
#include <limits>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

#include <android/log.h>

#include "whisper.h"

#define LOG_TAG "ClipLexNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

using Clock = std::chrono::steady_clock;

std::mutex g_mutex;
whisper_context *g_context = nullptr;
std::string g_model_path;
double g_last_model_load_ms = 0.0;
long long g_inference_count = 0;

struct ModelLoadOutcome {
    bool success = false;
    bool cache_hit = false;
    double native_load_ms = 0.0;
    std::string error_code;
    std::string error_message;
};

struct NativeSegment {
    long long start_ms = 0;
    long long end_ms = 0;
    std::string text;
    float no_speech_probability = 0.0f;
};

struct NativeInferenceTimings {
    double jni_pcm_conversion_ms = 0.0;
    double whisper_mel_and_pre_encode_ms = 0.0;
    double whisper_sample_ms_per_run = 0.0;
    double whisper_encode_ms_per_run = 0.0;
    double whisper_decode_ms_per_run = 0.0;
    double whisper_batch_decode_ms_per_run = 0.0;
    double whisper_prompt_ms_per_run = 0.0;
    double whisper_inference_ms = 0.0;
    double native_total_ms = 0.0;
};

struct NativeTranscriptionOutcome {
    bool success = false;
    std::string error_code;
    std::string error_message;
    std::string detected_language;
    std::vector<NativeSegment> segments;
    NativeInferenceTimings timings;
    int sample_count = 0;
    long long audio_duration_ms = 0;
    int thread_count = 0;
    bool fast_mode_requested = false;
    bool fast_mode_applied = false;
    int audio_context_override = 0;
    bool model_was_warm = false;
    long long inference_index = 0;
    double model_load_ms = 0.0;
    bool segment_callback_requested = false;
    bool segment_callback_failed = false;
    int callback_segments_emitted = 0;
    std::string loaded_model_file;
    bool model_is_multilingual = false;
    std::string requested_language;
    bool translation_enabled = false;
    bool detect_language_enabled = false;
};

struct EncoderTimingState {
    Clock::time_point inference_started;
    double first_encoder_start_ms = -1.0;
};

struct ScopedJniSegmentCallback {
    JNIEnv *owner_env = nullptr;
    JavaVM *java_vm = nullptr;
    jobject callback = nullptr;
    jmethodID on_segment = nullptr;
    std::string fallback_language;
    bool failed = false;
    int segments_emitted = 0;

    ScopedJniSegmentCallback(JNIEnv *env, jobject callback_object, std::string language)
            : owner_env(env), fallback_language(std::move(language)) {
        if (env == nullptr || callback_object == nullptr) return;
        if (env->GetJavaVM(&java_vm) != JNI_OK) {
            java_vm = nullptr;
            failed = true;
            return;
        }
        callback = env->NewGlobalRef(callback_object);
        if (callback == nullptr) {
            failed = true;
            return;
        }
        jclass callback_class = env->GetObjectClass(callback_object);
        if (callback_class != nullptr) {
            on_segment = env->GetMethodID(
                    callback_class,
                    "onSegment",
                    "(JJLjava/lang/String;Ljava/lang/String;F)V");
            env->DeleteLocalRef(callback_class);
        }
        if (on_segment == nullptr || env->ExceptionCheck()) {
            env->ExceptionClear();
            failed = true;
        }
    }

    ~ScopedJniSegmentCallback() {
        if (owner_env != nullptr && callback != nullptr) owner_env->DeleteGlobalRef(callback);
    }

    ScopedJniSegmentCallback(const ScopedJniSegmentCallback &) = delete;
    ScopedJniSegmentCallback &operator=(const ScopedJniSegmentCallback &) = delete;

    bool ready() const {
        return !failed && java_vm != nullptr && callback != nullptr && on_segment != nullptr;
    }
};

double elapsed_ms(const Clock::time_point &started, const Clock::time_point &finished = Clock::now()) {
    return std::chrono::duration<double, std::milli>(finished - started).count();
}

// JNI's NewStringUTF consumes modified UTF-8, while whisper.cpp emits standard
// UTF-8. In particular, passing a four-byte supplementary code point directly
// to NewStringUTF is invalid and can abort under CheckJNI. Decode standard UTF-8
// explicitly and hand JNI UTF-16 code units instead.
jstring new_jstring_from_utf8(JNIEnv *env, const std::string &utf8) {
    if (env == nullptr) return nullptr;

    std::vector<jchar> utf16;
    utf16.reserve(utf8.size());
    size_t index = 0;
    while (index < utf8.size()) {
        const auto first = static_cast<uint8_t>(utf8[index]);
        uint32_t code_point = 0xfffd;
        size_t sequence_length = 1;

        if (first <= 0x7f) {
            code_point = first;
        } else if (first >= 0xc2 && first <= 0xdf && index + 1 < utf8.size()) {
            const auto second = static_cast<uint8_t>(utf8[index + 1]);
            if ((second & 0xc0) == 0x80) {
                code_point = ((first & 0x1fU) << 6U) | (second & 0x3fU);
                sequence_length = 2;
            }
        } else if (first >= 0xe0 && first <= 0xef && index + 2 < utf8.size()) {
            const auto second = static_cast<uint8_t>(utf8[index + 1]);
            const auto third = static_cast<uint8_t>(utf8[index + 2]);
            const bool valid_second = (second & 0xc0) == 0x80 &&
                                      !(first == 0xe0 && second < 0xa0) &&
                                      !(first == 0xed && second >= 0xa0);
            if (valid_second && (third & 0xc0) == 0x80) {
                code_point = ((first & 0x0fU) << 12U) |
                             ((second & 0x3fU) << 6U) |
                             (third & 0x3fU);
                sequence_length = 3;
            }
        } else if (first >= 0xf0 && first <= 0xf4 && index + 3 < utf8.size()) {
            const auto second = static_cast<uint8_t>(utf8[index + 1]);
            const auto third = static_cast<uint8_t>(utf8[index + 2]);
            const auto fourth = static_cast<uint8_t>(utf8[index + 3]);
            const bool valid_second = (second & 0xc0) == 0x80 &&
                                      !(first == 0xf0 && second < 0x90) &&
                                      !(first == 0xf4 && second >= 0x90);
            if (valid_second && (third & 0xc0) == 0x80 && (fourth & 0xc0) == 0x80) {
                code_point = ((first & 0x07U) << 18U) |
                             ((second & 0x3fU) << 12U) |
                             ((third & 0x3fU) << 6U) |
                             (fourth & 0x3fU);
                sequence_length = 4;
            }
        }

        index += sequence_length;
        if (code_point <= 0xffff) {
            utf16.push_back(static_cast<jchar>(code_point));
        } else {
            code_point -= 0x10000;
            utf16.push_back(static_cast<jchar>(0xd800U + (code_point >> 10U)));
            utf16.push_back(static_cast<jchar>(0xdc00U + (code_point & 0x3ffU)));
        }
    }

    if (utf16.size() > static_cast<size_t>(std::numeric_limits<jsize>::max())) return nullptr;
    static constexpr jchar empty = 0;
    return env->NewString(utf16.empty() ? &empty : utf16.data(), static_cast<jsize>(utf16.size()));
}

jstring new_jstring_from_utf8(JNIEnv *env, const char *utf8) {
    return new_jstring_from_utf8(env, std::string(utf8 == nullptr ? "" : utf8));
}

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
                    std::snprintf(encoded, sizeof(encoded), "\\u%04x", c);
                    output += encoded;
                } else {
                    output += static_cast<char>(c);
                }
        }
    }
    return output;
}

std::string json_escape(const std::string &value) {
    return json_escape(value.c_str());
}

std::string file_name_from_path(const std::string &path) {
    const size_t separator = path.find_last_of("/\\");
    return separator == std::string::npos ? path : path.substr(separator + 1);
}

std::string normalized_language(std::string language) {
    language.erase(language.begin(), std::find_if(language.begin(), language.end(), [](unsigned char c) {
        return !std::isspace(c);
    }));
    language.erase(std::find_if(language.rbegin(), language.rend(), [](unsigned char c) {
        return !std::isspace(c);
    }).base(), language.end());
    std::transform(language.begin(), language.end(), language.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    return language.empty() ? "auto" : language;
}

bool encoder_begin_callback(whisper_context *, whisper_state *, void *user_data) {
    auto *timing = static_cast<EncoderTimingState *>(user_data);
    if (timing != nullptr && timing->first_encoder_start_ms < 0.0) {
        timing->first_encoder_start_ms = elapsed_ms(timing->inference_started);
    }
    return true;
}

void new_segment_callback(
        whisper_context *,
        whisper_state *state,
        int n_new,
        void *user_data) {
    auto *callback = static_cast<ScopedJniSegmentCallback *>(user_data);
    if (callback == nullptr || !callback->ready() || state == nullptr || n_new <= 0) return;

    JNIEnv *env = nullptr;
    bool attached_here = false;
    const jint environment_status = callback->java_vm->GetEnv(
            reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (environment_status == JNI_EDETACHED) {
        if (callback->java_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            callback->failed = true;
            return;
        }
        attached_here = true;
    } else if (environment_status != JNI_OK || env == nullptr) {
        callback->failed = true;
        return;
    }

    const int total_segments = whisper_full_n_segments_from_state(state);
    const int first_new_segment = std::max(0, total_segments - n_new);
    const int language_id = whisper_full_lang_id_from_state(state);
    const char *detected = language_id >= 0 ? whisper_lang_str(language_id) : nullptr;
    const std::string language = detected == nullptr || detected[0] == '\0'
                                 ? callback->fallback_language
                                 : detected;

    for (int index = first_new_segment; index < total_segments && callback->ready(); ++index) {
        const char *text = whisper_full_get_segment_text_from_state(state, index);
        jstring java_text = new_jstring_from_utf8(env, text);
        jstring java_language = new_jstring_from_utf8(env, language);
        if (java_text == nullptr || java_language == nullptr || env->ExceptionCheck()) {
            env->ExceptionClear();
            if (java_text != nullptr) env->DeleteLocalRef(java_text);
            if (java_language != nullptr) env->DeleteLocalRef(java_language);
            callback->failed = true;
            break;
        }
        const jlong start_ms = whisper_full_get_segment_t0_from_state(state, index) * 10;
        const jlong end_ms = whisper_full_get_segment_t1_from_state(state, index) * 10;
        const jfloat no_speech_probability =
                whisper_full_get_segment_no_speech_prob_from_state(state, index);
        env->CallVoidMethod(
                callback->callback,
                callback->on_segment,
                start_ms,
                end_ms,
                java_text,
                java_language,
                no_speech_probability);
        env->DeleteLocalRef(java_text);
        env->DeleteLocalRef(java_language);
        if (env->ExceptionCheck()) {
            // A UI/listener failure must never invalidate the authoritative final result.
            env->ExceptionClear();
            callback->failed = true;
            break;
        }
        ++callback->segments_emitted;
    }

    if (attached_here) callback->java_vm->DetachCurrentThread();
}

void append_model_info_json(std::ostringstream &json) {
    if (g_context == nullptr) {
        json << "null";
        return;
    }
    json << "{\"fileName\":\"" << json_escape(file_name_from_path(g_model_path))
         << "\",\"type\":\"" << json_escape(whisper_model_type_readable(g_context))
         << "\",\"isMultilingual\":" << (whisper_is_multilingual(g_context) != 0 ? "true" : "false")
         << ",\"vocabularySize\":" << whisper_model_n_vocab(g_context)
         << ",\"audioContextSize\":" << whisper_model_n_audio_ctx(g_context)
         << ",\"textContextSize\":" << whisper_model_n_text_ctx(g_context)
         << ",\"melBins\":" << whisper_model_n_mels(g_context)
         << ",\"quantizationType\":" << whisper_model_ftype(g_context)
         << '}';
}

void append_runtime_info_json(std::ostringstream &json) {
    json << "\"systemInfo\":\"" << json_escape(whisper_print_system_info()) << "\",\"model\":";
    append_model_info_json(json);
}

ModelLoadOutcome ensure_model_locked(const std::string &model_path) {
    ModelLoadOutcome outcome;
    if (model_path.empty()) {
        outcome.error_code = "INVALID_MODEL_PATH";
        outcome.error_message = "Model path is empty";
        return outcome;
    }
    if (g_context != nullptr && g_model_path == model_path) {
        outcome.success = true;
        outcome.cache_hit = true;
        return outcome;
    }

    const auto started = Clock::now();
    whisper_context_params params = whisper_context_default_params();
    whisper_context *loaded = whisper_init_from_file_with_params(model_path.c_str(), params);
    outcome.native_load_ms = elapsed_ms(started);
    if (loaded == nullptr) {
        outcome.error_code = "MODEL_LOAD_FAILED";
        outcome.error_message = "whisper_init_from_file_with_params returned null";
        LOGE("Failed to load Whisper model file=%s loadMs=%.2f",
             file_name_from_path(model_path).c_str(), outcome.native_load_ms);
        return outcome;
    }

    if (g_context != nullptr) whisper_free(g_context);
    g_context = loaded;
    g_model_path = model_path;
    g_last_model_load_ms = outcome.native_load_ms;
    g_inference_count = 0;
    outcome.success = true;
    LOGI("Whisper model loaded file=%s type=%s loadMs=%.2f system=%s",
         file_name_from_path(g_model_path).c_str(), whisper_model_type_readable(g_context),
         outcome.native_load_ms, whisper_print_system_info());
    return outcome;
}

std::string model_load_json(const ModelLoadOutcome &outcome) {
    std::ostringstream json;
    json << std::fixed << std::setprecision(3)
         << "{\"success\":" << (outcome.success ? "true" : "false")
         << ",\"errorCode\":\"" << json_escape(outcome.error_code)
         << "\",\"errorMessage\":\"" << json_escape(outcome.error_message)
         << "\",\"cacheHit\":" << (outcome.cache_hit ? "true" : "false")
         << ",\"nativeLoadMs\":" << outcome.native_load_ms << ',';
    append_runtime_info_json(json);
    json << '}';
    return json.str();
}

NativeTranscriptionOutcome run_transcription(
        JNIEnv *env,
        jshortArray samples,
        const std::string &requested_language,
        int requested_threads,
        bool fast_mode_requested,
        jobject segment_callback_object) {
    NativeTranscriptionOutcome outcome;
    const auto native_started = Clock::now();
    outcome.fast_mode_requested = fast_mode_requested;
    outcome.segment_callback_requested = segment_callback_object != nullptr;
    outcome.requested_language = normalized_language(requested_language);

    if (samples == nullptr) {
        outcome.error_code = "INVALID_AUDIO";
        outcome.error_message = "PCM sample array is null";
        outcome.timings.native_total_ms = elapsed_ms(native_started);
        return outcome;
    }

    const auto conversion_started = Clock::now();
    const jsize count = env->GetArrayLength(samples);
    outcome.sample_count = static_cast<int>(count);
    outcome.audio_duration_ms = static_cast<long long>(count) * 1000LL / 16000LL;
    std::vector<jshort> pcm16(static_cast<size_t>(count));
    if (count > 0) env->GetShortArrayRegion(samples, 0, count, pcm16.data());
    if (env->ExceptionCheck()) {
        outcome.error_code = "JNI_AUDIO_COPY_FAILED";
        outcome.error_message = "GetShortArrayRegion failed";
        outcome.timings.jni_pcm_conversion_ms = elapsed_ms(conversion_started);
        outcome.timings.native_total_ms = elapsed_ms(native_started);
        return outcome;
    }
    std::vector<float> pcm(static_cast<size_t>(count));
    std::transform(pcm16.begin(), pcm16.end(), pcm.begin(),
                   [](jshort sample) { return static_cast<float>(sample) / 32768.0f; });
    outcome.timings.jni_pcm_conversion_ms = elapsed_ms(conversion_started);

    std::lock_guard<std::mutex> guard(g_mutex);
    if (g_context == nullptr) {
        outcome.error_code = "MODEL_NOT_LOADED";
        outcome.error_message = "Transcription requested without a loaded model";
        outcome.timings.native_total_ms = elapsed_ms(native_started);
        LOGE("%s", outcome.error_message.c_str());
        return outcome;
    }

    const std::string &language = outcome.requested_language;
    outcome.loaded_model_file = file_name_from_path(g_model_path);
    outcome.model_is_multilingual = whisper_is_multilingual(g_context) != 0;
    outcome.thread_count = std::max(1, requested_threads);
    outcome.fast_mode_applied = fast_mode_requested && count >= 1600 && count <= 16000 * 10 && language == "en";
    outcome.model_was_warm = g_inference_count > 0;
    outcome.inference_index = g_inference_count + 1;
    outcome.model_load_ms = g_last_model_load_ms;

    if (!outcome.model_is_multilingual && language != "en") {
        outcome.error_code = "MODEL_LANGUAGE_MISMATCH";
        outcome.error_message = "Loaded English-only model '" + outcome.loaded_model_file +
                                "' cannot transcribe requested language '" + language +
                                "'; select English or load the multilingual model";
        outcome.timings.native_total_ms = elapsed_ms(native_started);
        LOGE("Model/language mismatch model=%s requestedLanguage=%s multilingual=false",
             outcome.loaded_model_file.c_str(), language.c_str());
        return outcome;
    }

    if (count < 1600) {
        outcome.success = true;
        outcome.detected_language = language == "en" ? "en" : "";
        outcome.timings.native_total_ms = elapsed_ms(native_started);
        return outcome;
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = outcome.thread_count;
    params.translate = false;
    params.no_context = true;
    // The short-English path only reduces encoder context. Keep timestamps and
    // sentence segmentation so downstream video lessons remain synchronized.
    params.no_timestamps = false;
    params.single_segment = false;
    params.token_timestamps = false;
    params.audio_ctx = outcome.fast_mode_applied ? 512 : 0;
    outcome.audio_context_override = params.audio_ctx;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.language = language.c_str();
    // "auto" already asks whisper_full() to detect the language before decoding.
    // detect_language is a separate detect-only mode that returns with no segments.
    params.detect_language = false;
    outcome.translation_enabled = params.translate;
    outcome.detect_language_enabled = params.detect_language;
    params.suppress_blank = true;

    EncoderTimingState encoder_timing;
    encoder_timing.inference_started = Clock::now();
    params.encoder_begin_callback = encoder_begin_callback;
    params.encoder_begin_callback_user_data = &encoder_timing;

    ScopedJniSegmentCallback segment_callback(env, segment_callback_object, language);
    if (segment_callback_object != nullptr && segment_callback.ready()) {
        params.new_segment_callback = new_segment_callback;
        params.new_segment_callback_user_data = &segment_callback;
    }

    LOGI("Whisper transcription started samples=%d durationMs=%lld language=%s threads=%d fast=%s warm=%s model=%s multilingual=%s translate=%s detectLanguage=%s",
         outcome.sample_count, outcome.audio_duration_ms, params.language, outcome.thread_count,
         outcome.fast_mode_applied ? "true" : "false", outcome.model_was_warm ? "true" : "false",
         outcome.loaded_model_file.c_str(), outcome.model_is_multilingual ? "true" : "false",
         outcome.translation_enabled ? "true" : "false",
         outcome.detect_language_enabled ? "true" : "false");
    whisper_reset_timings(g_context);
    const auto inference_started = Clock::now();
    const int result = whisper_full(g_context, params, pcm.data(), static_cast<int>(pcm.size()));
    outcome.segment_callback_failed = segment_callback.failed;
    outcome.callback_segments_emitted = segment_callback.segments_emitted;
    outcome.timings.whisper_inference_ms = elapsed_ms(inference_started);
    outcome.timings.whisper_mel_and_pre_encode_ms =
            std::max(0.0, encoder_timing.first_encoder_start_ms);

    std::unique_ptr<whisper_timings> timings(whisper_get_timings(g_context));
    if (timings != nullptr) {
        outcome.timings.whisper_sample_ms_per_run = timings->sample_ms;
        outcome.timings.whisper_encode_ms_per_run = timings->encode_ms;
        outcome.timings.whisper_decode_ms_per_run = timings->decode_ms;
        outcome.timings.whisper_batch_decode_ms_per_run = timings->batchd_ms;
        outcome.timings.whisper_prompt_ms_per_run = timings->prompt_ms;
    }
    if (result != 0) {
        outcome.error_code = "WHISPER_FULL_FAILED";
        outcome.error_message = "whisper_full failed with code " + std::to_string(result);
        outcome.timings.native_total_ms = elapsed_ms(native_started);
        LOGE("%s", outcome.error_message.c_str());
        return outcome;
    }

    ++g_inference_count;
    const int language_id = whisper_full_lang_id(g_context);
    const char *detected_language = language_id >= 0 ? whisper_lang_str(language_id) : nullptr;
    outcome.detected_language = detected_language == nullptr ? "" : detected_language;
    if (outcome.detected_language.empty() && language == "en") outcome.detected_language = "en";

    const int segment_count = whisper_full_n_segments(g_context);
    outcome.segments.reserve(static_cast<size_t>(segment_count));
    for (int i = 0; i < segment_count; ++i) {
        NativeSegment segment;
        segment.start_ms = whisper_full_get_segment_t0(g_context, i) * 10;
        segment.end_ms = whisper_full_get_segment_t1(g_context, i) * 10;
        const char *text = whisper_full_get_segment_text(g_context, i);
        segment.text = text == nullptr ? "" : text;
        segment.no_speech_probability = whisper_full_get_segment_no_speech_prob(g_context, i);
        outcome.segments.push_back(std::move(segment));
    }
    outcome.success = true;
    outcome.timings.native_total_ms = elapsed_ms(native_started);
    LOGI("Whisper transcription completed language=%s segments=%d inferenceMs=%.2f nativeMs=%.2f melPreEncodeMs=%.2f sampleMsPerRun=%.2f encodeMsPerRun=%.2f decodeMsPerRun=%.2f",
         outcome.detected_language.c_str(), segment_count, outcome.timings.whisper_inference_ms,
         outcome.timings.native_total_ms, outcome.timings.whisper_mel_and_pre_encode_ms,
         outcome.timings.whisper_sample_ms_per_run, outcome.timings.whisper_encode_ms_per_run,
         outcome.timings.whisper_decode_ms_per_run);
    return outcome;
}

void append_segments_json(std::ostringstream &json, const NativeTranscriptionOutcome &outcome) {
    json << '[';
    for (size_t i = 0; i < outcome.segments.size(); ++i) {
        if (i > 0) json << ',';
        const NativeSegment &segment = outcome.segments[i];
        json << "{\"start\":" << segment.start_ms
             << ",\"end\":" << segment.end_ms
             << ",\"text\":\"" << json_escape(segment.text)
             << "\",\"noSpeechProb\":" << segment.no_speech_probability
             << '}';
    }
    json << ']';
}

std::string legacy_transcription_json(const NativeTranscriptionOutcome &outcome) {
    std::ostringstream json;
    json << "{\"language\":\"" << json_escape(outcome.detected_language) << "\",\"segments\":";
    append_segments_json(json, outcome);
    json << '}';
    return json.str();
}

std::string detailed_transcription_json(const NativeTranscriptionOutcome &outcome) {
    std::ostringstream json;
    json << std::fixed << std::setprecision(3)
         << "{\"success\":" << (outcome.success ? "true" : "false")
         << ",\"errorCode\":\"" << json_escape(outcome.error_code)
         << "\",\"errorMessage\":\"" << json_escape(outcome.error_message)
         << "\",\"language\":\"" << json_escape(outcome.detected_language)
         << "\",\"segments\":";
    append_segments_json(json, outcome);
    json << ",\"diagnostics\":{\"sampleCount\":" << outcome.sample_count
         << ",\"audioDurationMs\":" << outcome.audio_duration_ms
         << ",\"threadCount\":" << outcome.thread_count
         << ",\"fastModeRequested\":" << (outcome.fast_mode_requested ? "true" : "false")
         << ",\"fastModeApplied\":" << (outcome.fast_mode_applied ? "true" : "false")
         << ",\"audioContextOverride\":" << outcome.audio_context_override
         << ",\"modelWasWarm\":" << (outcome.model_was_warm ? "true" : "false")
         << ",\"inferenceIndex\":" << outcome.inference_index
         << ",\"modelLoadMs\":" << outcome.model_load_ms
         << ",\"segmentCallbackRequested\":" << (outcome.segment_callback_requested ? "true" : "false")
         << ",\"segmentCallbackFailed\":" << (outcome.segment_callback_failed ? "true" : "false")
         << ",\"callbackSegmentsEmitted\":" << outcome.callback_segments_emitted
         << ",\"loadedModelFile\":\"" << json_escape(outcome.loaded_model_file)
         << "\",\"modelIsMultilingual\":" << (outcome.model_is_multilingual ? "true" : "false")
         << ",\"requestedLanguage\":\"" << json_escape(outcome.requested_language)
         << "\",\"translationEnabled\":" << (outcome.translation_enabled ? "true" : "false")
         << ",\"detectLanguageEnabled\":" << (outcome.detect_language_enabled ? "true" : "false")
         << ",\"jniPcmConversionMs\":" << outcome.timings.jni_pcm_conversion_ms
         << ",\"whisperMelAndPreEncodeMs\":" << outcome.timings.whisper_mel_and_pre_encode_ms
         << ",\"whisperSampleMsPerRun\":" << outcome.timings.whisper_sample_ms_per_run
         << ",\"whisperEncodeMsPerRun\":" << outcome.timings.whisper_encode_ms_per_run
         << ",\"whisperDecodeMsPerRun\":" << outcome.timings.whisper_decode_ms_per_run
         << ",\"whisperBatchDecodeMsPerRun\":" << outcome.timings.whisper_batch_decode_ms_per_run
         << ",\"whisperPromptMsPerRun\":" << outcome.timings.whisper_prompt_ms_per_run
         << ",\"whisperInferenceMs\":" << outcome.timings.whisper_inference_ms
         << ",\"nativeTotalMs\":" << outcome.timings.native_total_ms
         << "},";
    append_runtime_info_json(json);
    json << '}';
    return json.str();
}

std::string string_from_jstring(JNIEnv *env, jstring value, const char *fallback) {
    if (value == nullptr) return fallback;
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return fallback;
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void free_model_locked() {
    if (g_context != nullptr) whisper_free(g_context);
    g_context = nullptr;
    g_model_path.clear();
    g_last_model_load_ms = 0.0;
    g_inference_count = 0;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_jacksonkasi_cliplex_util_NativeBridge_getNativeVersion(JNIEnv *env, jobject) {
    return new_jstring_from_utf8(env, whisper_print_system_info());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_jacksonkasi_cliplex_util_NativeBridge_isNativeReady(JNIEnv *, jobject) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_jacksonkasi_cliplex_util_NativeBridge_whisperLoadModel(JNIEnv *env, jobject, jstring model_path) {
    const std::string path = string_from_jstring(env, model_path, "");
    std::lock_guard<std::mutex> guard(g_mutex);
    return ensure_model_locked(path).success ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jacksonkasi_cliplex_util_NativeBridge_whisperTranscribe(
        JNIEnv *env, jobject, jshortArray samples, jstring language, jint n_threads) {
    const NativeTranscriptionOutcome outcome = run_transcription(
            env, samples, string_from_jstring(env, language, "auto"), n_threads, false, nullptr);
    if (!outcome.success) return nullptr;
    const std::string json = legacy_transcription_json(outcome);
    return new_jstring_from_utf8(env, json);
}

extern "C" JNIEXPORT void JNICALL
Java_com_jacksonkasi_cliplex_util_NativeBridge_whisperFreeModel(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> guard(g_mutex);
    free_model_locked();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jacksonkasi_cliplex_whisper_NativeBridge_getNativeSystemInfo(JNIEnv *env, jobject) {
    return new_jstring_from_utf8(env, whisper_print_system_info());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jacksonkasi_cliplex_whisper_NativeBridge_whisperLoadModelDetailed(
        JNIEnv *env, jobject, jstring model_path) {
    const std::string path = string_from_jstring(env, model_path, "");
    std::lock_guard<std::mutex> guard(g_mutex);
    const std::string json = model_load_json(ensure_model_locked(path));
    return new_jstring_from_utf8(env, json);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jacksonkasi_cliplex_whisper_NativeBridge_whisperTranscribeDetailed(
        JNIEnv *env,
        jobject,
        jshortArray samples,
        jstring language,
        jint n_threads,
        jboolean fast_mode_requested,
        jobject segment_callback) {
    const NativeTranscriptionOutcome outcome = run_transcription(
            env,
            samples,
            string_from_jstring(env, language, "auto"),
            n_threads,
            fast_mode_requested == JNI_TRUE,
            segment_callback);
    const std::string json = detailed_transcription_json(outcome);
    return new_jstring_from_utf8(env, json);
}

extern "C" JNIEXPORT void JNICALL
Java_com_jacksonkasi_cliplex_whisper_NativeBridge_whisperFreeModel(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> guard(g_mutex);
    free_model_locked();
}
