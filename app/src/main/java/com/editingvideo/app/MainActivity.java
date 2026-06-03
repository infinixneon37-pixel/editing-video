package com.editingvideo.app;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.MediaInformationSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView tvSelectedFile, tvStatus, tvConsoleLog;
    private EditText etTrimSegment, etLoopDuration;
    private Button btnSelectVideo, btnSelectMulti, btnTrimWithAudio, btnTrimNoAudio, btnLoop, btnMerge;
    private RadioGroup rgLoopMode;
    private ProgressBar progressBar;
    private LinearLayout listContainer;

    private List<String> selectedPaths = new ArrayList<>();
    private List<String> originalNames = new ArrayList<>();
    private String privateDir;
    private String publicBaseDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvSelectedFile = findViewById(R.id.tvSelectedFile);
        tvStatus = findViewById(R.id.tvStatus);
        tvConsoleLog = findViewById(R.id.tvConsoleLog);
        etTrimSegment = findViewById(R.id.etTrimSegment);
        etLoopDuration = findViewById(R.id.etLoopDuration);
        btnSelectVideo = findViewById(R.id.btnSelectVideo);
        btnSelectMulti = findViewById(R.id.btnSelectMultiVideo);
        btnTrimWithAudio = findViewById(R.id.btnTrimWithAudio);
        btnTrimNoAudio = findViewById(R.id.btnTrimNoAudio);
        btnLoop = findViewById(R.id.btnLoop);
        btnMerge = findViewById(R.id.btnMerge);
        rgLoopMode = findViewById(R.id.rgLoopMode);
        progressBar = findViewById(R.id.progressBar);
        listContainer = findViewById(R.id.listContainer);

        privateDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES).getAbsolutePath();
        publicBaseDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Movies/EditingVideo";

        // Setup Main Folders
        String[] subDirs = {"/Trim", "/Loop", "/Merge"};
        for (String sub : subDirs) {
            File d = new File(publicBaseDir + sub);
            if (!d.exists()) d.mkdirs();
        }

        btnSelectVideo.setOnClickListener(v -> openGallery(false, 101));
        btnSelectMulti.setOnClickListener(v -> openGallery(true, 102));

        btnTrimWithAudio.setOnClickListener(v -> { if (checkInput(etTrimSegment, false)) processTrim(true); });
        btnTrimNoAudio.setOnClickListener(v -> { if (checkInput(etTrimSegment, false)) processTrim(false); });
        btnLoop.setOnClickListener(v -> { if (checkInput(etLoopDuration, false)) processButterLoop(Integer.parseInt(etLoopDuration.getText().toString())); });
        btnMerge.setOnClickListener(v -> { if (checkInput(null, true)) processMerge(); });
    }

    private void openGallery(boolean allowMultiple, int reqCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        if (allowMultiple) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, reqCode);
    }

    private boolean checkInput(EditText et, boolean isMulti) {
        if (selectedPaths.isEmpty()) { showToast("Pilih video terlebih dahulu."); return false; }
        if (isMulti && selectedPaths.size() < 2) { showToast("Minimal 2 video diperlukan untuk digabung."); return false; }
        if (et != null && et.getText().toString().isEmpty()) { showToast("Parameter durasi belum diisi."); return false; }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            setLoading(true);
            new Thread(() -> {
                // Clear cache dari eksekusi sebelumnya
                for (String path : selectedPaths) { new File(path).delete(); }
                selectedPaths.clear(); originalNames.clear();

                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    for (int i = 0; i < count; i++) {
                        processUriInput(data.getClipData().getItemAt(i).getUri());
                    }
                } else if (data.getData() != null) {
                    processUriInput(data.getData());
                }

                runOnUiThread(() -> {
                    setLoading(false);
                    tvSelectedFile.setText(selectedPaths.size() + " File Tersedia: \n" + String.join("\n", originalNames));
                    tvSelectedFile.setTextColor(Color.parseColor("#00E676"));
                    updateStatus("Standby. Siap diproses.");
                });
            }).start();
        }
    }

    private void processUriInput(Uri uri) {
        String realName = getFileName(uri);
        realName = realName.replace("\"", "").replace("'", "");
        originalNames.add(realName);

        String safeInternalName = "tmp_" + System.currentTimeMillis() + "_" + selectedPaths.size() + ".mp4";
        selectedPaths.add(copyUriToPrivate(uri, safeInternalName));
    }

    // --- FITUR TRIM (BATCH PROCESSING) ---
    private void processTrim(boolean keepAudio) {
        double d_segment = Double.parseDouble(etTrimSegment.getText().toString());
        String outDir = publicBaseDir + "/Trim";

        setLoading(true); listContainer.removeAllViews();

        new Thread(() -> {
            try {
                for (int i = 0; i < selectedPaths.size(); i++) {
                    String targetVideo = selectedPaths.get(i);
                    String baseName = originalNames.get(i).replaceAll("[.][^.]+$", "");

                    double totalDur = getVideoDuration(targetVideo);
                    double currentStart = 0.0;
                    int part = 1;

                    while (currentStart < totalDur) {
                        final int currentPart = part;
                        final int fileIndex = i + 1;
                        runOnUiThread(() -> updateStatus("✂️ [File " + fileIndex + "/" + selectedPaths.size() + "] Ekstraksi Part " + currentPart + "..."));

                        String finalOutName = baseName + "_part" + String.format(Locale.US, "%03d", part) + ".mp4";
                        String safeOutPath = privateDir + "/out_trim_" + System.currentTimeMillis() + "_" + part + ".mp4";

                        String cmd = String.format(Locale.US, "-y -ss %.3f -t %.3f -i \"%s\" -c:v mpeg4 -q:v 3 %s \"%s\"",
                                currentStart, d_segment, targetVideo, keepAudio ? "-c:a aac -b:a 128k" : "-an", safeOutPath);

                        FFmpegSession session = FFmpegKit.execute(cmd);
                        if (ReturnCode.isSuccess(session.getReturnCode())) {
                            moveToPublicFolder(safeOutPath, outDir, finalOutName);
                            runOnUiThread(() -> addToListResult("✅ " + finalOutName));
                        } else { throwFFmpegError(session); }

                        currentStart += d_segment; part++;
                    }
                }
                finishTask("✨ Eksekusi Batch Trim Selesai!");
            } catch (Exception e) { finishError(e.getMessage()); }
        }).start();
    }

    // --- FITUR LOOP (BATCH PROCESSING) ---
    private void processButterLoop(int targetDur) {
        String outDir = publicBaseDir + "/Loop";
        int modeId = rgLoopMode.getCheckedRadioButtonId();

        setLoading(true); listContainer.removeAllViews();

        new Thread(() -> {
            try {
                for (int i = 0; i < selectedPaths.size(); i++) {
                    String targetVideo = selectedPaths.get(i);
                    String baseName = originalNames.get(i).replaceAll("[.][^.]+$", "");
                    String finalOutName = "loop_" + baseName + ".mp4";

                    final int fileIndex = i + 1;
                    runOnUiThread(() -> updateStatus("🧈 [File " + fileIndex + "/" + selectedPaths.size() + "] Proses Loop..."));

                    double dur = getVideoDuration(targetVideo);
                    String safeOutPath = privateDir + "/out_loop_" + System.currentTimeMillis() + "_" + i + ".mp4";

                    if (modeId == R.id.rbLoopNormal) {
                        int numLoops = (int) (targetDur / dur) + 1;
                        String cmd = String.format(Locale.US, "-y -stream_loop %d -i \"%s\" -c copy -t %d \"%s\"", numLoops, targetVideo, targetDur, safeOutPath);
                        FFmpegSession session = FFmpegKit.execute(cmd);
                        if (!ReturnCode.isSuccess(session.getReturnCode())) throwFFmpegError(session);

                    } else {
                        String tempUnit = privateDir + "/temp_unit_" + i + ".mp4";
                        String filter;
                        String cmdFilter;

                        if (modeId == R.id.rbLoopTwerk) {
                            // Twerk: Video Reverse, Audio Reverse
                            filter = "[0:v]reverse,setpts=PTS-STARTPTS[v2];[0:a]areverse,asetpts=PTS-STARTPTS[a2];[0:v][0:a][v2][a2]concat=n=2:v=1:a=1[outv][outa]";
                        } else {
                            // Boomerang: Video Reverse, Audio Normal (Forward 2x)
                            filter = "[0:v]reverse,setpts=PTS-STARTPTS[v2];[0:v][v2]concat=n=2:v=1:a=0[outv];[0:a][0:a]concat=n=2:v=0:a=1[outa]";
                        }
                        
                        cmdFilter = String.format(Locale.US, "-y -i \"%s\" -filter_complex \"%s\" -map [outv] -map [outa] -c:v mpeg4 -q:v 3 -c:a aac \"%s\"", targetVideo, filter, tempUnit);

                        FFmpegSession sessionFilter = FFmpegKit.execute(cmdFilter);
                        if (!ReturnCode.isSuccess(sessionFilter.getReturnCode())) throwFFmpegError(sessionFilter);

                        double unitDur = dur * 2;
                        int numLoops = (int) (targetDur / unitDur) + 1;
                        String cmdLoop = String.format(Locale.US, "-y -stream_loop %d -i \"%s\" -c copy -t %d \"%s\"", numLoops, tempUnit, targetDur, safeOutPath);

                        FFmpegSession sessionLoop = FFmpegKit.execute(cmdLoop);
                        if (!ReturnCode.isSuccess(sessionLoop.getReturnCode())) throwFFmpegError(sessionLoop);

                        new File(tempUnit).delete();
                    }

                    moveToPublicFolder(safeOutPath, outDir, finalOutName);
                    runOnUiThread(() -> addToListResult("✅ " + finalOutName));
                }
                finishTask("✨ Eksekusi Batch Loop Selesai!");

            } catch (Exception e) { finishError(e.getMessage()); }
        }).start();
    }

    // --- FITUR GABUNG (MERGE & REMUX) ---
    private void processMerge() {
        String outDir = publicBaseDir + "/Merge";
        String baseName = originalNames.get(0).replaceAll("[.][^.]+$", "");
        String finalOutName = "merged_" + baseName + ".mp4";
        String safeOutPath = privateDir + "/out_merge_" + System.currentTimeMillis() + ".mp4";

        setLoading(true); updateStatus("🔗 Menggabungkan & Remuxing Video..."); listContainer.removeAllViews();

        new Thread(() -> {
            try {
                File listFile = new File(privateDir, "list.txt");
                FileWriter writer = new FileWriter(listFile);
                for (String path : selectedPaths) {
                    writer.write("file '" + path + "'\n");
                }
                writer.flush(); writer.close();

                String cmd = String.format(Locale.US, "-y -f concat -safe 0 -i \"%s\" -c copy \"%s\"", listFile.getAbsolutePath(), safeOutPath);

                FFmpegSession session = FFmpegKit.execute(cmd);
                if (ReturnCode.isSuccess(session.getReturnCode())) {
                    moveToPublicFolder(safeOutPath, outDir, finalOutName);

                    for (String path : selectedPaths) {
                        new File(path).delete();
                    }
                    selectedPaths.clear(); originalNames.clear();

                    runOnUiThread(() -> {
                        addToListResult("✅ " + finalOutName);
                        tvSelectedFile.setText("[ Kosong ]");
                    });
                    finishTask("✨ Merge Selesai!");
                } else {
                    throwFFmpegError(session);
                }
            } catch (Exception e) { finishError(e.getMessage()); }
        }).start();
    }

    // --- UTILITIES ---
    private void throwFFmpegError(FFmpegSession session) throws Exception {
        String log = session.getLogsAsString();
        if (log == null || log.isEmpty()) log = "FFmpeg gagal dengan ReturnCode: " + session.getReturnCode() + ". Kemungkinan codec tidak didukung.";
        throw new Exception(log);
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) result = cursor.getString(index);
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result != null ? result : "video_" + System.currentTimeMillis() + ".mp4";
    }

    private String copyUriToPrivate(Uri u, String safeName) {
        File f = new File(privateDir, safeName);
        try (InputStream in = getContentResolver().openInputStream(u); OutputStream out = new FileOutputStream(f)) {
            byte[] b = new byte[4096]; int l; while ((l = in.read(b)) > 0) out.write(b, 0, l);
        } catch (Exception e) { e.printStackTrace(); }
        return f.getAbsolutePath();
    }

    private double getVideoDuration(String path) {
        MediaInformationSession s = FFprobeKit.getMediaInformation(path);
        return (s.getMediaInformation() != null) ? Double.parseDouble(s.getMediaInformation().getDuration()) : 0;
    }

    private void moveToPublicFolder(String srcPath, String destDir, String name) throws Exception {
        File s = new File(srcPath);
        File dir = new File(destDir);
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Gagal membuat direktori rute publik: " + destDir);

        File d = new File(destDir, name);
        try (InputStream in = new FileInputStream(s); OutputStream out = new FileOutputStream(d)) {
            byte[] b = new byte[4096]; int l; while ((l = in.read(b)) > 0) out.write(b, 0, l);
            s.delete();
        } catch (Exception e) {
            throw new Exception("Transfer I/O diblokir sistem Android. Detail: " + e.getMessage());
        }
    }

    private void addToListResult(String text) {
        TextView tv = new TextView(this); tv.setText(text); tv.setTextColor(Color.parseColor("#00E676"));
        listContainer.addView(tv);
    }

    private void updateStatus(String msg) { tvStatus.setText(msg); tvConsoleLog.setText(""); }
    private void finishTask(String msg) { runOnUiThread(() -> { setLoading(false); updateStatus(msg); }); }
    private void finishError(String m) { runOnUiThread(() -> { setLoading(false); updateStatus("❌ Error"); tvConsoleLog.setText(m); }); }

    private void setLoading(boolean b) {
        progressBar.setVisibility(b ? View.VISIBLE : View.GONE);
        btnTrimWithAudio.setEnabled(!b); btnTrimNoAudio.setEnabled(!b);
        btnLoop.setEnabled(!b); btnMerge.setEnabled(!b);
        btnSelectVideo.setEnabled(!b); btnSelectMulti.setEnabled(!b);
    }

    private void showToast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
