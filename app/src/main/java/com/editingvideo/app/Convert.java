package com.editingvideo.app;

import java.util.Locale;

public class Convert {
    public void execute(String targetVideo, String privateDir, String outDir, String baseName, FFmpegHelper.ProcessCallback callback) {
        callback.onProgress("🔄 Konversi ke Standar MP4...");
        
        String finalOutName = "converted_" + baseName + "_" + System.currentTimeMillis() + ".mp4";
        String safeOutPath = privateDir + "/out_convert_" + System.currentTimeMillis() + ".mp4";

        String cmd = String.format(Locale.US, "-y -i \"%s\" -c:v mpeg4 -q:v 3 -c:a aac -b:a 128k -pix_fmt yuv420p \"%s\"", targetVideo, safeOutPath);
        
        FFmpegHelper.executeAndMove(cmd, safeOutPath, outDir, finalOutName, callback);
    }
}
