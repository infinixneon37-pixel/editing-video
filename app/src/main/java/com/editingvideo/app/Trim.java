package com.editingvideo.app;

import java.util.Locale;

public class Trim {
    public void execute(String targetVideo, String privateDir, String outDir, String baseName,
                        boolean isAutoSplit, double startOrSegment, double end, boolean keepAudio,
                        FFmpegHelper.ProcessCallback callback) {

        new Thread(() -> {
            try {
                double totalDur = FFmpegHelper.getVideoDuration(targetVideo);

                if (isAutoSplit) {
                    double d_segment = startOrSegment;
                    double currentStart = 0.0;
                    int part = 1;

                    while (currentStart < totalDur) {
                        final int currentPart = part;
                        callback.onProgress("✂️ Ekstraksi Part " + currentPart + "...");

                        String finalOutName = baseName + "_part" + String.format(Locale.US, "%03d", part) + ".mp4";
                        String tempOut = privateDir + "/out_trim_" + System.currentTimeMillis() + "_" + part + ".mp4";

                        String cmd = String.format(Locale.US, "-y -ss %.3f -t %.3f -i \"%s\" -c:v mpeg4 -q:v 3 %s -pix_fmt yuv420p \"%s\"",
                                currentStart, d_segment, targetVideo, keepAudio ? "-c:a aac -b:a 128k" : "-an", tempOut);

                        FFmpegHelper.executeAndMove(cmd, tempOut, outDir, finalOutName, callback);
                        currentStart += d_segment;
                        part++;
                    }
                } else {
                    double d_start = startOrSegment;
                    double d_end = end;
                    if (d_start >= d_end || d_start > totalDur) throw new Exception("Waktu Start/End tidak valid.");

                    callback.onProgress("✂️ Ekstraksi Custom Trim...");
                    String finalOutName = baseName + "_custom_" + System.currentTimeMillis() + ".mp4";
                    String tempOut = privateDir + "/out_trim_custom_" + System.currentTimeMillis() + ".mp4";
                    double diff = d_end - d_start;

                    String cmd = String.format(Locale.US, "-y -ss %.3f -t %.3f -i \"%s\" -c:v mpeg4 -q:v 3 %s -pix_fmt yuv420p \"%s\"",
                            d_start, diff, targetVideo, keepAudio ? "-c:a aac -b:a 128k" : "-an", tempOut);

                    FFmpegHelper.executeAndMove(cmd, tempOut, outDir, finalOutName, callback);
                }
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }
}
