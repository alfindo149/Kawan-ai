package id.gia.assistant;

import android.content.Context;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import org.json.JSONObject;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit JavaScript bridge. The web UI must call downloadDefaultModel() only after user consent. */
public final class GiaLocalAiBridge {
    private static final String MODEL_FILE = "qwen2.5-1.5b-instruct-q4_k_m.gguf";
    private static final String MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf";
    private final Context context;
    private final WebView webView;
    private final LocalModelManager modelManager;
    private final ExecutorService inferenceExecutor = Executors.newSingleThreadExecutor();

    public GiaLocalAiBridge(Context context, WebView webView) {
        this.context = context.getApplicationContext();
        this.webView = webView;
        this.modelManager = new LocalModelManager(context);
    }

    @JavascriptInterface
    public void downloadDefaultModel() {
        modelManager.download(MODEL_URL, MODEL_FILE, "", new LocalModelManager.Listener() {
            @Override public void onProgress(long downloaded, long total) {
                callback("onDownloadProgress", downloaded, total);
            }
            @Override public void onComplete(File file) {
                callback("onModelDownloaded", file.getAbsolutePath());
            }
            @Override public void onError(String message) {
                callback("onError", message);
            }
        });
    }

    @JavascriptInterface
    public void loadModel() {
        inferenceExecutor.execute(() -> {
            File model = new File(new File(context.getFilesDir(), "models"), MODEL_FILE);
            if (!model.isFile()) { callback("onError", "File model belum diunduh."); return; }
            String result = LocalInference.nativeLoadModel(model.getAbsolutePath());
            callback("onModelLoaded", result);
        });
    }

    @JavascriptInterface
    public void generate(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) { callback("onError", "Pertanyaan masih kosong."); return; }
        inferenceExecutor.execute(() -> {
            String answer = LocalInference.nativeGenerate(prompt, 160);
            callback("onAnswer", answer);
        });
    }

    @JavascriptInterface
    public void unloadModel() {
        inferenceExecutor.execute(LocalInference::nativeUnloadModel);
    }

    public void close() {
        modelManager.shutdown();
        inferenceExecutor.shutdownNow();
    }

    private void callback(String method, Object... args) {
        StringBuilder js = new StringBuilder("window.GIA_LOCAL_AI && window.GIA_LOCAL_AI.")
                .append(method).append("(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) js.append(",");
            if (args[i] instanceof Number) js.append(args[i]);
            else js.append(JSONObject.quote(String.valueOf(args[i])));
        }
        js.append(");");
        webView.post(() -> webView.evaluateJavascript(js.toString(), null));
    }
}
