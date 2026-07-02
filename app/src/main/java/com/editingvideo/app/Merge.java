package com.editingvideo.app;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Locale;

public class Merge {
    public void execute(List<String> selectedPaths, String privateDir, String outDir, String baseName, FFmpegHelper.ProcessCallback callback) {
        callback.onProgress("🎬 Menggabungkan Video (Re-encode Mode / Anti-Corrupt)...");
        
        new Thread(() -> {
            try {
                File listFile = new File(privateDir, "list.txt");
                FileWriter writer = new FileWriter(listFile);
                for (String path : selectedPaths) { writer.write("file '" + path + "'\n"); }
                writer.flush(); writer.close();

                String finalOutName = "merged_" + baseName + "_" + System.currentTimeMillis() + ".mp4";
                String safeOutPath = privateDir + "/out_merge_" + System.currentTimeMillis() + ".mp4";

                // DIUBAH: Hilangkan '-c copy' dan ganti dengan standar re-encode mpeg4 agar tidak macet
                String cmd = String.format(Locale.US, "-y -f concat -safe 0 -i \"%s\" -c:v mpeg4 -q:v 3 -c:a aac -b:a 128k -pix_fmt yuv420p \"%s\"", listFile.getAbsolutePath(), safeOutPath);
                
                FFmpegHelper.executeAndMove(cmd, safeOutPath, outDir, finalOutName, callback);
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }
}
