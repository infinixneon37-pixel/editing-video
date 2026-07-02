package com.editingvideo.app;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.MediaInformationSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class FFmpegHelper {

    public interface ProcessCallback {
        void onProgress(String message);
        void onSuccess(String finalName);
        void onError(String errorMessage);
    }

    public static void executeAndMove(String cmd, String tempOutPath, String finalDir, String finalName, ProcessCallback callback) {
        new Thread(() -> {
            try {
                FFmpegSession session = FFmpegKit.execute(cmd);
                if (ReturnCode.isSuccess(session.getReturnCode())) {
                    moveToPublicFolder(tempOutPath, finalDir, finalName);
                    callback.onSuccess(finalName);
                } else {
                    String log = session.getLogsAsString();
                    if (log == null || log.isEmpty()) log = "ReturnCode: " + session.getReturnCode();
                    callback.onError("FFmpeg Error: " + log);
                }
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    public static double getVideoDuration(String path) throws Exception {
        MediaInformationSession s = FFprobeKit.getMediaInformation(path);
        if (s.getMediaInformation() == null) throw new Exception("Gagal memindai durasi file.");
        return Double.parseDouble(s.getMediaInformation().getDuration());
    }

    private static void moveToPublicFolder(String srcPath, String destDir, String name) throws Exception {
        File s = new File(srcPath);
        File dir = new File(destDir);
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Gagal membuat direktori publik.");

        File d = new File(destDir, name);
        try (InputStream in = new FileInputStream(s); OutputStream out = new FileOutputStream(d)) {
            byte[] b = new byte[4096];
            int l;
            while ((l = in.read(b)) > 0) out.write(b, 0, l);
            s.delete(); // Hapus file temp setelah dipindah
        } catch (Exception e) {
            throw new Exception("Transfer I/O diblokir Android: " + e.getMessage());
        }
    }
}
