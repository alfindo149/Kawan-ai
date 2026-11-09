# GIA Local AI Foundation

This branch is an isolated preparation step for on-device text inference. It does not change the production Pages deployment, Supabase functions, API secrets, or the existing APK behavior.

## Device target

- Device: Vivo Y17
- Reported memory: 6 GB RAM
- Reported free storage: 107 GB
- Android: 9
- CPU inference is the safe baseline; do not assume a supported mobile GPU delegate.

## Runtime choice

Use the official `llama.cpp` Android binding / inference engine with GGUF models. Keep the model file in app-private storage and load it from a local file path. Start with a short context (1024–2048 tokens) and one inference thread configuration tuned on-device; monitor Android memory and thermal behavior.

Official Android guide: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md

## Candidate model

Initial candidate for evaluation: `Qwen2.5-1.5B-Instruct-GGUF`, `Q4_K_M` quantization (about 1.12 GB as listed by the model repository). The 1.5B model is a candidate, not a performance guarantee on the Vivo Y17. If it is too slow or memory-heavy, fall back to a 0.5B model.

Model repository: https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF

## Integration stages

1. Add an Android-native model manager: download with resumable/progress reporting, verify file size/hash where available, store in app-private files, and allow deletion.
2. Integrate the llama.cpp Android binding and expose a narrow native inference interface to the app.
3. Add a GIA chat provider selector: Local (on-device) or Cloud (existing Supabase route). Local must never silently fall back to Gemini.
4. Test airplane-mode chat, Indonesian prompts, memory pressure, response latency, battery and thermal behavior on the actual Vivo Y17.
5. Only after successful tests, optionally migrate research/creative features. Web research and image/video generation still need separate online or local models/services.

## Safety and compatibility rules

- Do not delete or rotate Gemini/Supabase secrets in this foundation stage.
- Do not remove `kawan-chat` or `kawan-research`.
- Keep cloud chat available as an explicit fallback until local inference is proven.
- Do not label GIA as fully offline until airplane-mode inference succeeds on the target phone.
- Do not bundle the GGUF model inside the APK; download it after the user opts in. This keeps the APK smaller and lets the model be updated independently.

## Current status

Planning/foundation only. No local inference engine is bundled yet, no model has been downloaded, and the current APK still loads the hosted GIA web app through Android WebView.
