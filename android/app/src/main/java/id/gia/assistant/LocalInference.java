package id.gia.assistant;

/**
 * Thin JNI lifecycle bridge for the on-device llama.cpp runtime.
 * Prompt generation and token streaming are intentionally a later stage.
 */
public final class LocalInference {
    static {
        System.loadLibrary("gia_native");
    }

    private LocalInference() {}

    public static native String nativeLoadModel(String absoluteModelPath);
    public static native void nativeUnloadModel();
}
