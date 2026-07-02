package com.editingvideo.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus, tvActiveFile, tvBatchCount, tvEmptyPreview;
    private ProgressBar progressBar;
    private LinearLayout listContainer, layoutTrimCustom;
    private EditText etTrimSegment, etTrimStart, etTrimEnd, etLoopDuration;
    private Button btnTrimWithAudio, btnTrimNoAudio, btnLoop, btnMerge, btnRemux, btnSelectVideo, btnSelectMulti;
    private RadioGroup rgTrimMode, rgLoopMode;
    
    // Preview Komponen
    private VideoView videoPreview;
    private SeekBar seekBarPreview;
    private TextView btnPlayPause, tvPreviewTime;
    private Handler timeHandler = new Handler();
    private Runnable updateTimeRunnable;
    private boolean isUserSeeking = false;

    // Managers dari Modular
    private Trim trimManager;
    private Loop loopManager;
    private Merge mergeManager;
    private Convert convertManager;

    private List<String> selectedPaths = new ArrayList<>();
    private List<String> originalNames = new ArrayList<>();
    private String privateDir, publicBaseDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inisialisasi Managers
        trimManager = new Trim();
        loopManager = new Loop();
        mergeManager = new Merge();
        convertManager = new Convert();

        // Inisialisasi UI
        tvStatus = findViewById(R.id.tvStatus);
        progressBar = findViewById(R.id.progressBar);
        listContainer = findViewById(R.id.listContainer);
        tvActiveFile = findViewById(R.id.tvActiveFile);
        tvBatchCount = findViewById(R.id.tvBatchCount);
        tvEmptyPreview = findViewById(R.id.tvEmptyPreview);
        
        etTrimSegment = findViewById(R.id.etTrimSegment);
        etTrimStart = findViewById(R.id.etTrimStart);
        etTrimEnd = findViewById(R.id.etTrimEnd);
        etLoopDuration = findViewById(R.id.etLoopDuration);
        
        btnTrimWithAudio = findViewById(R.id.btnTrimWithAudio);
        btnTrimNoAudio = findViewById(R.id.btnTrimNoAudio);
        btnLoop = findViewById(R.id.btnLoop);
        btnMerge = findViewById(R.id.btnMerge);
        btnRemux = findViewById(R.id.btnRemux);
        btnSelectVideo = findViewById(R.id.btnSelectVideo);
        btnSelectMulti = findViewById(R.id.btnSelectMultiVideo);
        
        rgTrimMode = findViewById(R.id.rgTrimMode);
        rgLoopMode = findViewById(R.id.rgLoopMode);
        layoutTrimCustom = findViewById(R.id.layoutTrimCustom);
        
        videoPreview = findViewById(R.id.videoPreview);
        seekBarPreview = findViewById(R.id.seekBarPreview);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        tvPreviewTime = findViewById(R.id.tvPreviewTime);

        // Pengaturan Direktori
        privateDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES).getAbsolutePath();
        publicBaseDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Movies/EditingVideo";
        for (String sub : new String[]{"/Trim", "/Loop", "/Merge", "/Convert"}) {
            File d = new File(publicBaseDir + sub);
            if (!d.exists()) d.mkdirs();
        }

        setupTabNavigation();
        setupVideoPreviewTicker();

        findViewById(R.id.btnViewFiles).setOnClickListener(v -> showSelectedFilesDialog());
        btnSelectVideo.setOnClickListener(v -> openGallery(false, 101));
        btnSelectMulti.setOnClickListener(v -> openGallery(true, 102));

        rgTrimMode.setOnCheckedChangeListener((g, id) -> {
            etTrimSegment.setVisibility(id == R.id.rbTrimAuto ? View.VISIBLE : View.GONE);
            layoutTrimCustom.setVisibility(id == R.id.rbTrimCustom ? View.VISIBLE : View.GONE);
        });

        // --- CALLBACK EKSEKUSI FFMPEG ---
        FFmpegHelper.ProcessCallback commonCallback = new FFmpegHelper.ProcessCallback() {
            @Override public void onProgress(String msg) { runOnUiThread(() -> updateStatus(msg)); }
            @Override public void onSuccess(String name) { runOnUiThread(() -> { setLoading(false); updateStatus("✅ Selesai"); addToListResult("✅ " + name); }); }
            @Override public void onError(String err) { runOnUiThread(() -> { setLoading(false); updateStatus("❌ Error"); addToListResult("❌ " + err); }); }
        };

        btnTrimWithAudio.setOnClickListener(v -> {
            if (checkInputTrim()) {
                setLoading(true);
                // Mencegah crash jika user ngetik pakai koma (,)
                double start = rgTrimMode.getCheckedRadioButtonId() == R.id.rbTrimAuto ? 
                        Double.parseDouble(etTrimSegment.getText().toString().replace(",", ".")) : 
                        Double.parseDouble(etTrimStart.getText().toString().replace(",", "."));
                double end = rgTrimMode.getCheckedRadioButtonId() == R.id.rbTrimAuto ? 0 : 
                        Double.parseDouble(etTrimEnd.getText().toString().replace(",", "."));
                
                trimManager.execute(selectedPaths.get(0), privateDir, publicBaseDir + "/Trim", getBaseName(), rgTrimMode.getCheckedRadioButtonId() == R.id.rbTrimAuto, start, end, true, commonCallback);
            }
        });

        btnTrimNoAudio.setOnClickListener(v -> {
            if (checkInputTrim()) {
                setLoading(true);
                double start = rgTrimMode.getCheckedRadioButtonId() == R.id.rbTrimAuto ? 
                        Double.parseDouble(etTrimSegment.getText().toString().replace(",", ".")) : 
                        Double.parseDouble(etTrimStart.getText().toString().replace(",", "."));
                double end = rgTrimMode.getCheckedRadioButtonId() == R.id.rbTrimAuto ? 0 : 
                        Double.parseDouble(etTrimEnd.getText().toString().replace(",", "."));
                
                trimManager.execute(selectedPaths.get(0), privateDir, publicBaseDir + "/Trim", getBaseName(), rgTrimMode.getCheckedRadioButtonId() == R.id.rbTrimAuto, start, end, false, commonCallback);
            }
        });

        btnLoop.setOnClickListener(v -> {
            if (checkInput(etLoopDuration, false)) {
                setLoading(true);
                loopManager.execute(selectedPaths.get(0), privateDir, publicBaseDir + "/Loop", getBaseName(), rgLoopMode.getCheckedRadioButtonId(), Integer.parseInt(etLoopDuration.getText().toString().replace(",", ".")), commonCallback);
            }
        });

        btnMerge.setOnClickListener(v -> {
            if (checkInput(null, true)) {
                setLoading(true);
                mergeManager.execute(selectedPaths, privateDir, publicBaseDir + "/Merge", getBaseName(), commonCallback);
            }
        });

        btnRemux.setOnClickListener(v -> {
            if (checkInput(null, false)) {
                setLoading(true);
                for(int i = 0; i < selectedPaths.size(); i++) {
                    convertManager.execute(selectedPaths.get(i), privateDir, publicBaseDir + "/Convert", originalNames.get(i).replaceAll("[.][^.]+$", ""), commonCallback);
                }
            }
        });
    }

    // --- LOGIKA UI & NAVIGASI TAB ---
    private void setupTabNavigation() {
        View.OnClickListener listener = v -> {
            int[] tabs = {R.id.tabTrim, R.id.tabLoop, R.id.tabMerge, R.id.tabConvert};
            int[] panels = {R.id.panelTrim, R.id.panelLoop, R.id.panelMerge, R.id.panelConvert};
            for (int i=0; i < tabs.length; i++) {
                TextView t = findViewById(tabs[i]); View p = findViewById(panels[i]);
                if (v.getId() == tabs[i]) {
                    t.setTextColor(Color.parseColor("#3B82F6")); t.setBackgroundColor(Color.parseColor("#1E293B"));
                    p.setVisibility(View.VISIBLE);
                } else {
                    t.setTextColor(Color.parseColor("#64748B")); t.setBackgroundColor(Color.TRANSPARENT);
                    p.setVisibility(View.GONE);
                }
            }
        };
        findViewById(R.id.tabTrim).setOnClickListener(listener); 
        findViewById(R.id.tabLoop).setOnClickListener(listener);
        findViewById(R.id.tabMerge).setOnClickListener(listener); 
        findViewById(R.id.tabConvert).setOnClickListener(listener);
    }

    // --- LOGIKA FILE PICKER ---
    private void openGallery(boolean allowMultiple, int reqCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT); 
        intent.setType("video/*");
        if (allowMultiple) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, reqCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            setLoading(true); 
            if(videoPreview.isPlaying()) videoPreview.pause();
            timeHandler.removeCallbacks(updateTimeRunnable);

            new Thread(() -> {
                for (String path : selectedPaths) new File(path).delete();
                selectedPaths.clear(); originalNames.clear();
                
                if (data.getClipData() != null) {
                    for (int i = 0; i < data.getClipData().getItemCount(); i++) 
                        processUriInput(data.getClipData().getItemAt(i).getUri());
                } else if (data.getData() != null) {
                    processUriInput(data.getData());
                }

                runOnUiThread(() -> {
                    setLoading(false);
                    if (!originalNames.isEmpty()) {
                        tvActiveFile.setText("📹 " + originalNames.get(0)); 
                        tvEmptyPreview.setVisibility(View.GONE);
                        tvBatchCount.setText(originalNames.size() > 1 ? "📁 + " + (originalNames.size() - 1) + " file lain" : "📁 1 file dipilih");
                        setupPreviewPlayer(selectedPaths.get(0));
                    }
                    updateStatus("🟢 Standby. Siap diproses.");
                });
            }).start();
        }
    }

    private void processUriInput(Uri uri) {
        String realName = "video_" + System.currentTimeMillis() + ".mp4";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) 
                realName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
        } catch (Exception e) {}
        
        originalNames.add(realName.replace("\"", "").replace("'", ""));
        File f = new File(privateDir, "tmp_" + System.currentTimeMillis() + "_" + selectedPaths.size() + ".mp4");
        
        try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(f)) {
            byte[] b = new byte[4096]; int l; while ((l = in.read(b)) > 0) out.write(b, 0, l);
        } catch (Exception e) {}
        
        selectedPaths.add(f.getAbsolutePath());
    }

    // --- LOGIKA VIDEO PLAYER & PREVIEW (FULL) ---
    private void setupPreviewPlayer(String path) {
        videoPreview.setVisibility(View.VISIBLE);
        videoPreview.setVideoPath(path);

        videoPreview.setOnPreparedListener(mp -> {
            int duration = mp.getDuration();
            seekBarPreview.setMax(duration);
            seekBarPreview.setProgress(0);

            String totalTime = formatTimeString(duration);
            tvPreviewTime.setText("00:00.000 / " + totalTime);
            btnPlayPause.setText("▶");

            btnPlayPause.setOnClickListener(v -> {
                if (videoPreview.isPlaying()) {
                    videoPreview.pause();
                    btnPlayPause.setText("▶");
                    timeHandler.removeCallbacks(updateTimeRunnable);
                } else {
                    if (videoPreview.getCurrentPosition() >= duration - 500) {
                        videoPreview.seekTo(0);
                    }
                    videoPreview.start();
                    btnPlayPause.setText("⏸");
                    timeHandler.post(updateTimeRunnable);
                }
            });

            mp.setOnCompletionListener(mp2 -> {
                btnPlayPause.setText("▶");
                tvPreviewTime.setText(totalTime + " / " + totalTime);
                seekBarPreview.setProgress(duration);
                timeHandler.removeCallbacks(updateTimeRunnable);
            });
        });

        seekBarPreview.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvPreviewTime.setText(formatTimeString(progress) + " / " + formatTimeString(videoPreview.getDuration()));
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { isUserSeeking = true; }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                videoPreview.seekTo(seekBar.getProgress());
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
                    timeHandler.postDelayed(this, 50); // Loop cepat untuk refresh milidetik
                }
            }
        };
    }

    private String formatTimeString(int millis) {
        int ms = millis % 1000;
        int seconds = (millis / 1000) % 60;
        int minutes = (millis / (1000 * 60)) % 60;
        int hours = (millis / (1000 * 60 * 60)) % 24;

        if (hours > 0) return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, ms);
        return String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, ms);
    }

    private void showSelectedFilesDialog() {
        if (originalNames.isEmpty()) {
            Toast.makeText(this, "Belum ada file", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("File Terpilih (" + originalNames.size() + ")");
        builder.setItems(originalNames.toArray(new String[0]), null);
        builder.setPositiveButton("Tutup", null);
        builder.show();
    }

    // --- UTILITIES CHECK & STATUS ---
    private boolean checkInput(EditText et, boolean isMulti) {
        if (selectedPaths.isEmpty()) { showToast("Pilih video terlebih dahulu."); return false; }
        if (isMulti && selectedPaths.size() < 2) { showToast("Minimal 2 video diperlukan."); return false; }
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

    private String getBaseName() { return originalNames.get(0).replaceAll("[.][^.]+$", ""); }
    private void showToast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    private void updateStatus(String msg) { tvStatus.setText(msg); }
    private void addToListResult(String text) { 
        TextView tv = new TextView(this); tv.setText(text); tv.setTextColor(Color.parseColor("#38BDF8")); listContainer.addView(tv); 
    }
    
    private void setLoading(boolean b) { 
        progressBar.setVisibility(b ? View.VISIBLE : View.GONE);
        btnTrimWithAudio.setEnabled(!b); btnTrimNoAudio.setEnabled(!b);
        btnLoop.setEnabled(!b); btnMerge.setEnabled(!b); btnRemux.setEnabled(!b);
        btnSelectVideo.setEnabled(!b); btnSelectMulti.setEnabled(!b);
        if (b) {
            if(videoPreview != null && videoPreview.isPlaying()) {
                videoPreview.pause(); btnPlayPause.setText("▶");
            }
            if (btnPlayPause != null) btnPlayPause.setEnabled(false);
        } else {
            if (btnPlayPause != null) btnPlayPause.setEnabled(true);
        }
    }
}
