package com.editingvideo.app;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

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

    private TextView tvSelectedFile, tvStatus, tvConsoleLog, tvPreviewTime;
    private EditText etTrimSegment, etTrimStart, etTrimEnd, etLoopDuration;
    private Button btnSelectVideo, btnSelectMulti, btnTrimWithAudio, btnTrimNoAudio, btnLoop, btnMerge, btnRemux, btnPlayPause;
    private RadioGroup rgTrimMode, rgLoopMode;
    private LinearLayout layoutTrimAuto, layoutTrimCustom, listContainer;
    private ProgressBar progressBar;
    
    // Preview Components
    private CardView cardPreview;
    private VideoView videoPreview;
    private SeekBar seekBarPreview;
    private Handler timeHandler = new Handler();
    private Runnable updateTimeRunnable;
    private boolean isUserSeeking = false;

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
        etTrimStart = findViewById(R.id.etTrimStart);
        etTrimEnd = findViewById(R.id.etTrimEnd);
        etLoopDuration = findViewById(R.id.etLoopDuration);
        
        btnSelectVideo = findViewById(R.id.btnSelectVideo);
        btnSelectMulti = findViewById(R.id.btnSelectMultiVideo);
        btnTrimWithAudio = findViewById(R.id.btnTrimWithAudio);
        btnTrimNoAudio = findViewById(R.id.btnTrimNoAudio);
        btnLoop = findViewById(R.id.btnLoop);
        btnMerge = findViewById(R.id.btnMerge);
        btnRemux = findViewById(R.id.btnRemux);
        
        rgTrimMode = findViewById(R.id.rgTrimMode);
        rgLoopMode = findViewById(R.id.rgLoopMode);
        
        layoutTrimAuto = findViewById(R.id.layoutTrimAuto);
        layoutTrimCustom = findViewById(R.id.layoutTrimCustom);
        listContainer = findViewById(R.id.listContainer);
        progressBar = findViewById(R.id.progressBar);

        // Preview TV Init
        cardPreview = findViewById(R.id.cardPreview);
        videoPreview = findViewById(R.id.videoPreview);
        seekBarPreview = findViewById(R.id.seekBarPreview);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        tvPreviewTime = findViewById(R.id.tvPreviewTime);

        privateDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES).getAbsolutePath();
        publicBaseDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Movies/EditingVideo";

        String[] subDirs = {"/Trim", "/Loop", "/Merge", "/Convert"};
        for (String sub : subDirs) {
            File d = new File(publicBaseDir + sub);
            if (!d.exists()) d.mkdirs();
        }

        rgTrimMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbTrimAuto) {
                layoutTrimAuto.setVisibility(View.VISIBLE);
                layoutTrimCustom.setVisibility(View.GONE);
            } else {
                layoutTrimAuto.setVisibility(View.GONE);
                layoutTrimCustom.setVisibility(View.VISIBLE);
            }
        });

        btnSelectVideo.setOnClickListener(v -> openGallery(false, 101));
        btnSelectMulti.setOnClickListener(v -> openGallery(true, 102));

        btnTrimWithAudio.setOnClickListener(v -> { if (checkInputTrim()) processTrim(true); });
        btnTrimNoAudio.setOnClickListener(v -> { if (checkInputTrim()) processTrim(false); });
        btnLoop.setOnClickListener(v -> { if (checkInput(etLoopDuration, false)) processButterLoop(Integer.parseInt(etLoopDuration.getText().toString())); });
        btnMerge.setOnClickListener(v -> { if (checkInput(null, true)) processMerge(); });
        btnRemux.setOnClickListener(v -> { if (checkInput(null, false)) processRemux(); });

        setupVideoPreviewTicker();
    }

    private void openGallery(boolean allowMultiple, int reqCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        if (allowMultiple) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, reqCode);
    }

    private boolean checkInput(EditText et, boolean isMulti) {
        if (selectedPaths.isEmpty()) { showToast("Pilih video terlebih dahulu."); return false; }
        if (isMulti && selectedPaths.size() < 2) { showToast("Minimal 2 video diperlukan untuk fitur ini."); return false; }
        if (et != null && et.getText().toString().isEmpty()) { showToast("Parameter durasi belum diisi."); return false; }
        return true;
    }
    
    private boolean checkInputTrim() {
        if (selectedPaths.isEmpty()) { showToast("Pilih video terlebih dahulu."); return false; }
        if (rgTrimMode.getCheckedRadioButtonId() == R.id.rbTrimAuto) {
            if (etTrimSegment.getText().toString().isEmpty()) { showToast("Isi durasi part!"); return false; }
        } else {
            if (etTrimStart.getText().toString().isEmpty() || etTrimEnd.getText().toString().isEmpty()) { showToast("Isi Start dan End!"); return false; }
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            setLoading(true);
            
            // Hentikan dan Reset preview jika memilih video baru
            if(videoPreview.isPlaying()) videoPreview.pause();
            cardPreview.setVisibility(View.GONE);
            timeHandler.removeCallbacks(updateTimeRunnable);
            
            new Thread(() -> {
                for (String path : selectedPaths) { new File(path).delete(); }
                selectedPaths.clear(); originalNames.clear();

                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    for (int i = 0; i < count; i++) { processUriInput(data.getClipData().getItemAt(i).getUri()); }
                } else if (data.getData() != null) {
                    processUriInput(data.getData());
                }

                runOnUiThread(() -> {
                    setLoading(false);
                    tvSelectedFile.setText(selectedPaths.size() + " File Tersedia: \n" + String.join("\n", originalNames));
                    tvSelectedFile.setTextColor(Color.parseColor("#00E676"));
                    updateStatus("Standby. Siap diproses.");
                    
                    // Aktifkan Mini Player khusus jika hanya ada 1 video (Bukan Batch)
                    if (selectedPaths.size() == 1) {
                        setupPreviewPlayer(selectedPaths.get(0));
                    }
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
    
    // --- FITUR PREVIEW PLAYER PRO ---
    private void setupPreviewPlayer(String path) {
        cardPreview.setVisibility(View.VISIBLE);
        videoPreview.setVideoPath(path);
        
        videoPreview.setOnPreparedListener(mp -> {
            int duration = mp.getDuration();
            seekBarPreview.setMax(duration);
            seekBarPreview.setProgress(0);
            
            String totalTime = formatTimeString(duration);
            tvPreviewTime.setText("00:00 / " + totalTime);
            btnPlayPause.setText("▶ PLAY");
            
            btnPlayPause.setOnClickListener(v -> {
                if (videoPreview.isPlaying()) {
                    videoPreview.pause();
                    btnPlayPause.setText("▶ PLAY");
                    timeHandler.removeCallbacks(updateTimeRunnable);
                } else {
                    // Auto-restart jika di-play saat posisi sudah di ujung video
                    if (videoPreview.getCurrentPosition() >= duration - 500) {
                        videoPreview.seekTo(0);
                    }
                    videoPreview.start();
                    btnPlayPause.setText("⏸ PAUSE");
                    timeHandler.post(updateTimeRunnable);
                }
            });
            
            mp.setOnCompletionListener(mp2 -> {
                btnPlayPause.setText("▶ PLAY");
                tvPreviewTime.setText(totalTime + " / " + totalTime);
                seekBarPreview.setProgress(duration);
                timeHandler.removeCallbacks(updateTimeRunnable);
            });
        });

        // Kontrol geser (Scrubbing) SeekBar manual oleh pengguna
        seekBarPreview.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvPreviewTime.setText(formatTimeString(progress) + " / " + formatTimeString(videoPreview.getDuration()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true; // Hentikan auto-update dari video saat digeser
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                videoPreview.seekTo(seekBar.getProgress()); // Memaksa video pindah ke titik baru
            }
        });
    }

    private void setupVideoPreviewTicker() {
        updateTimeRunnable = new Runnable() {
            @Override
            public void run() {
                if (videoPreview != null && videoPreview.isPlaying()) {
                    if (!isUserSeeking) {
                        int currentPos = videoPreview.getCurrentPosition();
                        seekBarPreview.setProgress(currentPos);
                        String current = formatTimeString(currentPos);
                        String total = formatTimeString(videoPreview.getDuration());
                        tvPreviewTime.setText(current + " / " + total);
                    }
                    // Loop super responsif setiap 100ms agar bar berjalan halus
                    timeHandler.postDelayed(this, 100); 
                }
            }
        };
    }
    
    // Helper untuk Format Waktu Milidetik (Android) ke Format M:S
    private String formatTimeString(int millis) {
        int seconds = (millis / 1000) % 60;
        int minutes = (millis / (1000 * 60)) % 60;
        int hours = (millis / (1000 * 60 * 60)) % 24;
        if (hours > 0) return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    // --- FITUR TRIM ---
    private void processTrim(boolean keepAudio) {
        boolean isAutoSplit = rgTrimMode.getCheckedRadioButtonId() == R.id.rbTrimAuto;
        String outDir = publicBaseDir + "/Trim";
        setLoading(true); listContainer.removeAllViews();

        new Thread(() -> {
            try {
                for (int i = 0; i < selectedPaths.size(); i++) {
                    String targetVideo = selectedPaths.get(i);
                    String baseName = originalNames.get(i).replaceAll("[.][^.]+$", "");
                    final int fileIndex = i + 1;
                    double totalDur = getVideoDuration(targetVideo);

                    if (isAutoSplit) {
                        double d_segment = Double.parseDouble(etTrimSegment.getText().toString());
                        double currentStart = 0.0;
                        int part = 1;

                        while (currentStart < totalDur) {
                            final int currentPart = part;
                            runOnUiThread(() -> updateStatus("✂️ [File " + fileIndex + "/" + selectedPaths.size() + "] Ekstraksi Part " + currentPart + "..."));

                            String finalOutName = baseName + "_part" + String.format(Locale.US, "%03d", part) + ".mp4";
                            String safeOutPath = privateDir + "/out_trim_" + System.currentTimeMillis() + "_" + part + ".mp4";

                            String cmd = String.format(Locale.US, "-y -ss %.3f -t %.3f -i \"%s\" -c:v mpeg4 -q:v 3 %s -pix_fmt yuv420p \"%s\"",
                                    currentStart, d_segment, targetVideo, keepAudio ? "-c:a aac -b:a 128k" : "-an", safeOutPath);

                            FFmpegSession session = FFmpegKit.execute(cmd);
                            if (ReturnCode.isSuccess(session.getReturnCode())) {
                                moveToPublicFolder(safeOutPath, outDir, finalOutName);
                                runOnUiThread(() -> addToListResult("✅ " + finalOutName));
                            } else { throwFFmpegError(session); }

                            currentStart += d_segment; part++;
                        }
                    } else {
                        // CUSTOM TRIM MODE
                        double d_start = parseTimeToSeconds(etTrimStart.getText().toString());
                        double d_end = parseTimeToSeconds(etTrimEnd.getText().toString());
                        
                        if (d_start >= d_end || d_start > totalDur) throw new Exception("Waktu Start tidak valid atau melebihi durasi video.");
                        
                        double diff = d_end - d_start;
                        runOnUiThread(() -> updateStatus("✂️ [File " + fileIndex + "/" + selectedPaths.size() + "] Ekstraksi Custom Trim..."));

                        String finalOutName = baseName + "_custom.mp4";
                        String safeOutPath = privateDir + "/out_trim_custom_" + System.currentTimeMillis() + ".mp4";

                        String cmd = String.format(Locale.US, "-y -ss %.3f -t %.3f -i \"%s\" -c:v mpeg4 -q:v 3 %s -pix_fmt yuv420p \"%s\"",
                                d_start, diff, targetVideo, keepAudio ? "-c:a aac -b:a 128k" : "-an", safeOutPath);

                        FFmpegSession session = FFmpegKit.execute(cmd);
                        if (ReturnCode.isSuccess(session.getReturnCode())) {
                            moveToPublicFolder(safeOutPath, outDir, finalOutName);
                            runOnUiThread(() -> addToListResult("✅ " + finalOutName));
                        } else { throwFFmpegError(session); }
                    }
                }
                finishTask("✨ Eksekusi Batch Trim Selesai!");
            } catch (Exception e) { finishError(e.getMessage()); }
        }).start();
    }

    // --- FITUR LOOP BOOMERANG ---
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
                    runOnUiThread(() -> updateStatus("🧈 [File " + fileIndex + "/" + selectedPaths.size() + "] Proses Loop Filter..."));

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
                            filter = "[0:v]split=2[v1][v_rev];[v1]setpts=PTS-STARTPTS[v_fwd];[v_rev]reverse,setpts=PTS-STARTPTS[v2];[v_fwd][v2]concat=n=2:v=1:a=0[outv];[0:a]asplit=2[a1][a_rev];[a1]asetpts=PTS-STARTPTS[a_fwd];[a_rev]areverse,asetpts=PTS-STARTPTS[a2];[a_fwd][a2]concat=n=2:v=0:a=1[outa]";
                        } else {
                            filter = "[0:v]split=2[v1][v_rev];[v1]setpts=PTS-STARTPTS[v_fwd];[v_rev]reverse,setpts=PTS-STARTPTS[v2];[v_fwd][v2]concat=n=2:v=1:a=0[outv];[0:a]asplit=2[a1][a2];[a1]asetpts=PTS-STARTPTS[a_fwd];[a2]asetpts=PTS-STARTPTS[a_dup];[a_fwd][a_dup]concat=n=2:v=0:a=1[outa]";
                        }
                        
                        cmdFilter = String.format(Locale.US, "-y -i \"%s\" -filter_complex \"%s\" -map [outv] -map [outa] -c:v mpeg4 -q:v 3 -c:a aac -pix_fmt yuv420p \"%s\"", targetVideo, filter, tempUnit);

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

    // --- FITUR GABUNG (FAST CONCAT) ---
    private void processMerge() {
        String outDir = publicBaseDir + "/Merge";
        String baseName = originalNames.get(0).replaceAll("[.][^.]+$", "");
        String finalOutName = "merged_" + baseName + ".mp4";
        String safeOutPath = privateDir + "/out_merge_" + System.currentTimeMillis() + ".mp4";

        setLoading(true); updateStatus("🔗 Menggabungkan Video (Fast Mode)..."); listContainer.removeAllViews();

        new Thread(() -> {
            try {
                File listFile = new File(privateDir, "list.txt");
                FileWriter writer = new FileWriter(listFile);
                for (String path : selectedPaths) { writer.write("file '" + path + "'\n"); }
                writer.flush(); writer.close();

                String cmd = String.format(Locale.US, "-y -f concat -safe 0 -i \"%s\" -c copy \"%s\"", listFile.getAbsolutePath(), safeOutPath);

                FFmpegSession session = FFmpegKit.execute(cmd);
                if (ReturnCode.isSuccess(session.getReturnCode())) {
                    moveToPublicFolder(safeOutPath, outDir, finalOutName);
                    for (String path : selectedPaths) { new File(path).delete(); }
                    selectedPaths.clear(); originalNames.clear();

                    runOnUiThread(() -> {
                        addToListResult("✅ " + finalOutName);
                        tvSelectedFile.setText("[ Kosong ]");
                    });
                    finishTask("✨ Gabung Video Selesai!");
                } else { throwFFmpegError(session); }
            } catch (Exception e) { finishError(e.getMessage()); }
        }).start();
    }

    // --- FITUR CONVERT MP4 BATCH ---
    private void processRemux() {
        String outDir = publicBaseDir + "/Convert";
        setLoading(true); updateStatus("🔄 Mengonversi Video ke Format Standar..."); listContainer.removeAllViews();

        new Thread(() -> {
            try {
                for (int i = 0; i < selectedPaths.size(); i++) {
                    String targetVideo = selectedPaths.get(i);
                    String baseName = originalNames.get(i).replaceAll("[.][^.]+$", "");
                    String finalOutName = "converted_" + baseName + ".mp4";

                    final int fileIndex = i + 1;
                    runOnUiThread(() -> updateStatus("🔄 [File " + fileIndex + "/" + selectedPaths.size() + "] Convert ke MP4..."));

                    String safeOutPath = privateDir + "/out_convert_" + System.currentTimeMillis() + "_" + i + ".mp4";
                    String cmd = String.format(Locale.US, "-y -i \"%s\" -c:v mpeg4 -q:v 3 -c:a aac -b:a 128k -pix_fmt yuv420p \"%s\"", targetVideo, safeOutPath);

                    FFmpegSession session = FFmpegKit.execute(cmd);
                    if (ReturnCode.isSuccess(session.getReturnCode())) {
                        moveToPublicFolder(safeOutPath, outDir, finalOutName);
                        runOnUiThread(() -> addToListResult("✅ " + finalOutName));
                    } else { throwFFmpegError(session); }
                }
                finishTask("✨ Eksekusi Batch Convert Selesai!");
            } catch (Exception e) { finishError(e.getMessage()); }
        }).start();
    }

    // --- UTILITIES ---
    
    private double parseTimeToSeconds(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return 0;
        if (timeStr.contains(":")) {
            String[] parts = timeStr.split(":");
            return (Double.parseDouble(parts[0]) * 60) + Double.parseDouble(parts[1]);
        }
        return Double.parseDouble(timeStr);
    }
    
    private void throwFFmpegError(FFmpegSession session) throws Exception {
        String log = session.getLogsAsString();
        if (log == null || log.isEmpty()) log = "ReturnCode: " + session.getReturnCode() + ". Codec tidak didukung.";
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

    private double getVideoDuration(String path) throws Exception {
        MediaInformationSession s = FFprobeKit.getMediaInformation(path);
        if (s.getMediaInformation() == null) throw new Exception("FFprobe gagal memindai. File rusak atau format tak dikenali.");
        return Double.parseDouble(s.getMediaInformation().getDuration());
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
    
    private void finishTask(String msg) { 
        runOnUiThread(() -> { 
            setLoading(false); 
            updateStatus(msg); 
            // Matikan preview player untuk menghemat RAM
            if(videoPreview.isPlaying()) videoPreview.pause();
            cardPreview.setVisibility(View.GONE);
            timeHandler.removeCallbacks(updateTimeRunnable);
        }); 
    }
    
    private void finishError(String m) { runOnUiThread(() -> { setLoading(false); updateStatus("❌ Error"); tvConsoleLog.setText(m); }); }

    private void setLoading(boolean b) {
        progressBar.setVisibility(b ? View.VISIBLE : View.GONE);
        btnTrimWithAudio.setEnabled(!b); btnTrimNoAudio.setEnabled(!b);
        btnLoop.setEnabled(!b); btnMerge.setEnabled(!b); btnRemux.setEnabled(!b);
        btnSelectVideo.setEnabled(!b); btnSelectMulti.setEnabled(!b);
        
        // Disable tombol play saat memproses
        if (b) {
            if(videoPreview.isPlaying()) videoPreview.pause();
            btnPlayPause.setEnabled(false);
        } else {
            btnPlayPause.setEnabled(true);
        }
    }

    private void showToast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
