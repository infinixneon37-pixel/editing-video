package com.editingvideo.app;

import android.os.Environment;
import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SegmentedDownloader {
    private static final String DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final OkHttpClient client = new OkHttpClient();
    private static final int THREAD_COUNT = 4; 

    public interface DownloadCallback {
        void onProgress(int progress);
        void onComplete(String filePath);
        void onError(String error);
    }

    public static void startDownload(String url, String fileName, DownloadCallback callback) {
        new Thread(() -> {
            try {
                Request request = new Request.Builder().url(url).header("User-Agent", DEFAULT_UA).head().build();
                long fileSize;
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) throw new Exception("Server rejected connection");
                    String lengthHeader = response.header("Content-Length");
                    if (lengthHeader == null) throw new Exception("Server does not support chunking");
                    fileSize = Long.parseLong(lengthHeader);
                }

                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File outputFile = new File(dir, fileName);
                
                RandomAccessFile raf = new RandomAccessFile(outputFile, "rw");
                raf.setLength(fileSize);
                raf.close();

                long partSize = fileSize / THREAD_COUNT;
                ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

                for (int i = 0; i < THREAD_COUNT; i++) {
                    long startByte = i * partSize;
                    long endByte = (i == THREAD_COUNT - 1) ? fileSize - 1 : (startByte + partSize - 1);
                    executor.execute(new DownloadTask(url, outputFile, startByte, endByte));
                }

                executor.shutdown();
                executor.awaitTermination(2, TimeUnit.HOURS);
                callback.onComplete(outputFile.getAbsolutePath());

            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    private static class DownloadTask implements Runnable {
        private final String url;
        private final File file;
        private final long startByte, endByte;

        public DownloadTask(String url, File file, long startByte, long endByte) {
            this.url = url; this.file = file; this.startByte = startByte; this.endByte = endByte;
        }

        @Override
        public void run() {
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", DEFAULT_UA)
                        .header("Range", "bytes=" + startByte + "-" + endByte)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.body() != null) {
                        InputStream in = response.body().byteStream();
                        RandomAccessFile raf = new RandomAccessFile(file, "rw");
                        raf.seek(startByte);
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            raf.write(buffer, 0, len);
                        }
                        raf.close();
                        in.close();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace(); 
            }
        }
    }
}
