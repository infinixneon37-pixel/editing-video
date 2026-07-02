package com.editingvideo.app;

import java.io.File;
import java.util.Locale;

public class Loop {
    public void execute(String targetVideo, String privateDir, String outDir, String baseName,
                        int modeId, int targetDur, FFmpegHelper.ProcessCallback callback) {
        new Thread(() -> {
            try {
                callback.onProgress("🔁 Proses Loop Filter...");
                double dur = FFmpegHelper.getVideoDuration(targetVideo);
                String finalOutName = "loop_" + baseName + "_" + System.currentTimeMillis() + ".mp4";
                String safeOutPath = privateDir + "/out_loop_" + System.currentTimeMillis() + ".mp4";

                if (modeId == R.id.rbLoopNormal) {
                    int numLoops = (int) (targetDur / dur) + 1;
                    String cmd = String.format(Locale.US, "-y -stream_loop %d -i \"%s\" -c copy -t %d \"%s\"", numLoops, targetVideo, targetDur, safeOutPath);
                    FFmpegHelper.executeAndMove(cmd, safeOutPath, outDir, finalOutName, callback);
                } else {
                    String tempUnit = privateDir + "/temp_unit_" + System.currentTimeMillis() + ".mp4";
                    String filter = (modeId == R.id.rbLoopTwerk) ?
                            "[0:v]split=2[v1][v_rev];[v1]setpts=PTS-STARTPTS[v_fwd];[v_rev]reverse,setpts=PTS-STARTPTS[v2];[v_fwd][v2]concat=n=2:v=1:a=0[outv];[0:a]asplit=2[a1][a_rev];[a1]asetpts=PTS-STARTPTS[a_fwd];[a_rev]areverse,asetpts=PTS-STARTPTS[a2];[a_fwd][a2]concat=n=2:v=0:a=1[outa]" :
                            "[0:v]split=2[v1][v_rev];[v1]setpts=PTS-STARTPTS[v_fwd];[v_rev]reverse,setpts=PTS-STARTPTS[v2];[v_fwd][v2]concat=n=2:v=1:a=0[outv];[0:a]asplit=2[a1][a2];[a1]asetpts=PTS-STARTPTS[a_fwd];[a2]asetpts=PTS-STARTPTS[a_dup];[a_fwd][a_dup]concat=n=2:v=0:a=1[outa]";

                    String cmdFilter = String.format(Locale.US, "-y -i \"%s\" -filter_complex \"%s\" -map [outv] -map [outa] -c:v mpeg4 -q:v 3 -c:a aac -pix_fmt yuv420p \"%s\"", targetVideo, filter, tempUnit);
                    
                    FFmpegHelper.executeAndMove(cmdFilter, tempUnit, privateDir, "temp.mp4", new FFmpegHelper.ProcessCallback() {
                        @Override public void onProgress(String message) {}
                        @Override public void onError(String errorMessage) { callback.onError(errorMessage); }
                        @Override
                        public void onSuccess(String finalName) {
                            try {
                                double unitDur = dur * 2;
                                int numLoops = (int) (targetDur / unitDur) + 1;
                                String cmdLoop = String.format(Locale.US, "-y -stream_loop %d -i \"%s\" -c copy -t %d \"%s\"", numLoops, tempUnit, targetDur, safeOutPath);
                                FFmpegHelper.executeAndMove(cmdLoop, safeOutPath, outDir, finalOutName, callback);
                            } catch (Exception e) { callback.onError(e.getMessage()); }
                        }
                    });
                }
            } catch (Exception e) { callback.onError(e.getMessage()); }
        }).start();
    }
}
