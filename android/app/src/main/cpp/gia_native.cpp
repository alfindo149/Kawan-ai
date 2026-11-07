#include <jni.h>
#include <android/log.h>
#include <mutex>
#include <string>
#include "llama.h"

#define LOG_TAG "GIA-Native"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex g_mutex;
static llama_model * g_model = nullptr;
static llama_context * g_context = nullptr;
static bool g_backend_ready = false;

extern "C" JNIEXPORT jstring JNICALL
Java_id_gia_assistant_LocalInference_nativeLoadModel(JNIEnv * env, jclass, jstring modelPath) {
    const char * path = env->GetStringUTFChars(modelPath, nullptr);
    if (path == nullptr) return env->NewStringUTF("Gagal membaca lokasi model.");
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    if (!g_backend_ready) {
        llama_backend_init();
        g_backend_ready = true;
    }

    llama_model_params model_params = llama_model_default_params();
    g_model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);
    if (g_model == nullptr) {
        return env->NewStringUTF("Model GGUF gagal dibuka. Periksa file dan ruang memori HP.");
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = 2048;
    context_params.n_batch = 128;
    context_params.n_threads = 3;
    context_params.n_threads_batch = 3;
    g_context = llama_init_from_model(g_model, context_params);
    if (g_context == nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
        return env->NewStringUTF("Model terbaca, tetapi konteks AI gagal dibuat. Coba model lebih kecil.");
    }
    return env->NewStringUTF("MODEL_LOADED");
}

extern "C" JNIEXPORT void JNICALL
Java_id_gia_assistant_LocalInference_nativeUnloadModel(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}
