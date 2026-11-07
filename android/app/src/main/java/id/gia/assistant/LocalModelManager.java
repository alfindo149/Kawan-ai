package id.gia.assistant;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads an optional GGUF model into GIA's private app storage.
 * This class deliberately does not start inference; the llama.cpp runtime
 * will consume the completed local file in a later integration step.
 */
public final class LocalModelManager {
    public interface Listener {
        void onProgress(long downloadedBytes, long totalBytes);
        void onComplete(File modelFile);
        void onError(String message);
    }

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean cancelled;
    private volatile HttpURLConnection activeConnection;

    public LocalModelManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public synchronized void download(
            String modelUrl,
            String fileName,
            String expectedSha256,
            Listener listener) {
        cancelled = false;
        executor.execute(() -> performDownload(modelUrl, fileName, expectedSha256, listener));
    }

    public void cancel() {
        cancelled = true;
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
    }

    public void shutdown() {
        cancel();
        executor.shutdownNow();
    }

    private void performDownload(
            String modelUrl,
            String fileName,
            String expectedSha256,
            Listener listener) {
        File partialFile = null;
        try {
            if (modelUrl == null || !modelUrl.startsWith("https://")) {
                throw new IllegalArgumentException("Model harus diunduh melalui HTTPS.");
            }
            if (fileName == null || !fileName.matches("[A-Za-z0-9._-]{1,120}")) {
                throw new IllegalArgumentException("Nama file model tidak valid.");
            }

            File modelDirectory = new File(appContext.getFilesDir(), "models");
            if (!modelDirectory.exists() && !modelDirectory.mkdirs()) {
                throw new IllegalStateException("Folder penyimpanan model tidak dapat dibuat.");
            }

            File targetFile = new File(modelDirectory, fileName);
            partialFile = new File(modelDirectory, fileName + ".part");
            URL url = new URL(modelUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            activeConnection = connection;
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "GIA-Android");
            connection.connect();

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Server model memberi respons HTTP " + status + ".");
            }

            long totalBytes = connection.getContentLengthLong();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long downloadedBytes = 0L;
            byte[] buffer = new byte[64 * 1024];

            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(partialFile, false)) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (cancelled || Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("Unduhan dibatalkan.");
                    }
                    output.write(buffer, 0, count);
                    digest.update(buffer, 0, count);
                    downloadedBytes += count;
                    final long progress = downloadedBytes;
                    mainHandler.post(() -> listener.onProgress(progress, totalBytes));
                }
                output.flush();
            } finally {
                connection.disconnect();
                activeConnection = null;
            }

            if (expectedSha256 != null && !expectedSha256.trim().isEmpty()) {
                String actualSha256 = toHex(digest.digest());
                if (!actualSha256.equalsIgnoreCase(expectedSha256.trim())) {
                    throw new IllegalStateException("Pemeriksaan SHA-256 gagal. File model tidak digunakan.");
                }
            }

            if (cancelled) throw new InterruptedException("Unduhan dibatalkan.");
            if (targetFile.exists() && !targetFile.delete()) {
                throw new IllegalStateException("File model lama tidak dapat diganti.");
            }
            if (!partialFile.renameTo(targetFile)) {
                throw new IllegalStateException("File model selesai diunduh tetapi gagal disimpan.");
            }

            File completedFile = targetFile;
            mainHandler.post(() -> listener.onComplete(completedFile));
        } catch (Exception error) {
            if (partialFile != null && partialFile.exists()) partialFile.delete();
            String message = error.getMessage() == null ? "Unduhan model gagal." : error.getMessage();
            mainHandler.post(() -> listener.onError(message));
        } finally {
            activeConnection = null;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return result.toString();
    }
}
