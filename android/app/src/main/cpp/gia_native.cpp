#include <jni.h>
#include <android/log.h>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>
#include "llama.h"

#define LOG_TAG "GIA-Native"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex g_mutex;
static llama_model * g_model = nullptr;
static llama_context * g_context = nullptr;
static bool g_backend_ready = false;

extern "C" JNIEXPORT jstring JNICALL
Java_id_gia_assistant_LocalInference_nativeLoadModel(JNIEnv * env, jclass, jstring modelPath) {
    if (!modelPath) return env->NewStringUTF("Lokasi model kosong.");
    const char * path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) return env->NewStringUTF("Gagal membaca lokasi model.");
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_context) { llama_free(g_context); g_context = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    if (!g_backend_ready) { llama_backend_init(); g_backend_ready = true; }

    llama_model_params mp = llama_model_default_params();
    g_model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!g_model) return env->NewStringUTF("Model GGUF gagal dibuka.");

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = 2048;
    cp.n_batch = 128;
    cp.n_threads = 3;
    cp.n_threads_batch = 3;
    g_context = llama_init_from_model(g_model, cp);
    if (!g_context) {
        llama_model_free(g_model); g_model = nullptr;
        return env->NewStringUTF("Konteks AI gagal dibuat. Coba model lebih kecil.");
    }
    return env->NewStringUTF("MODEL_LOADED");
}

extern "C" JNIEXPORT jstring JNICALL
Java_id_gia_assistant_LocalInference_nativeGenerate(JNIEnv * env, jclass, jstring promptText, jint maxTokens) {
    if (!promptText) return env->NewStringUTF("Pertanyaan kosong.");
    const char * prompt = env->GetStringUTFChars(promptText, nullptr);
    if (!prompt) return env->NewStringUTF("Pertanyaan tidak terbaca.");
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_context) {
        env->ReleaseStringUTFChars(promptText, prompt);
        return env->NewStringUTF("Mesin AI lokal belum dimuat.");
    }

    llama_memory_clear(llama_get_memory(g_context), true);
    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    int n = llama_tokenize(vocab, prompt, (int) strlen(prompt), nullptr, 0, true, false);
    if (n <= 0 || n > 1800) {
        env->ReleaseStringUTFChars(promptText, prompt);
        return env->NewStringUTF("Pertanyaan terlalu panjang atau gagal diproses.");
    }
    std::vector<llama_token> tokens(n);
    n = llama_tokenize(vocab, prompt, (int) strlen(prompt), tokens.data(), n, true, false);
    env->ReleaseStringUTFChars(promptText, prompt);
    if (n <= 0) return env->NewStringUTF("Tokenisasi pertanyaan gagal.");

    llama_batch batch = llama_batch_get_one(tokens.data(), n);
    if (llama_decode(g_context, batch) != 0) return env->NewStringUTF("Model gagal memproses pertanyaan.");

    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    std::string output;
    const int limit = maxTokens < 1 ? 1 : (maxTokens > 256 ? 256 : maxTokens);
    for (int i = 0; i < limit; ++i) {
        llama_token token = llama_sampler_sample(sampler, g_context, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        char piece[256];
        int len = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
        if (len > 0) output.append(piece, len);
        batch = llama_batch_get_one(&token, 1);
        if (llama_decode(g_context, batch) != 0) break;
    }
    llama_sampler_free(sampler);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_id_gia_assistant_LocalInference_nativeUnloadModel(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_context) { llama_free(g_context); g_context = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
}
