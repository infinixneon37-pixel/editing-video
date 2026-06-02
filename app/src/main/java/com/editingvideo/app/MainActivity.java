package com.editingvideo.app;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView tvSelectedFile, tvStatus, tvConsoleLog;
    private EditText etTrimSegment, etLoopDuration;
    private Button btnSelectVideo, btnTrimWithAudio, btnTrimNoAudio, btnLoop;
    private ProgressBar progressBar;
    private LinearLayout listContainer;

    private String selectedVideoPath = null;
    private String privateDir;
    private String publicDir;

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
        btnTrimWithAudio = findViewById(R.id.btnTrimWithAudio);
        btnTrimNoAudio = findViewById(R.id.btnTrimNoAudio);
        btnLoop = findViewById(R.id.btnLoop);
        progressBar = findViewById(R.id.progressBar);
        listContainer = findViewById(R.id.listContainer);

        privateDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES).getAbsolutePath();
        publicDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Movies/EditingVideo";

        File pubFolder = new File(publicDir);
        if (!pubFolder.exists()) pubFolder.mkdirs();

        btnSelectVideo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("video/*");
            startActivityForResult(intent, 101);
        });

        // Tombol Trim DENGAN Audio
        btnTrimWithAudio.setOnClickListener(v -> {
            if (checkInput(etTrimSegment)) processTrim(true);
        });

        // Tombol Trim TANPA Audio
        btnTrimNoAudio.setOnClickListener(v -> {
            if (checkInput(etTrimSegment)) processTrim(false);
        });

        // Tombol Butter Loop
        btnLoop.setOnClickListener(v -> {
            if (checkInput(etLoopDuration)) {
                int targetDur = Integer.parseInt(etLoopDuration.getText().toString());
                processButterLoop(targetDur);
            }
        });
    }

    private boolean checkInput(EditText et) {
        if (selectedVideoPath == null) {
            showToast("Pilih video dulu, Bang!");
            return false;
        }
        if (et.getText().toString().isEmpty()) {
            showToast("Isi durasi dulu!");
            return false;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
            setLoading(true);
            updateStatus("Menyalin video ke folder engine...");
            new Thread(() -> {
                Uri uri = data.getData();
                selectedVideoPath = copyUriToPrivate(uri);
                runOnUiThread(() -> {
                    setLoading(false);
                    tvSelectedFile.setText("File Siap: " + new File(selectedVideoPath).getName());
                    tvSelectedFile.setTextColor(Color.parseColor("#10B981")); // Hijau Emerald
                    updateStatus("Standby. Video siap diproses!");
                });
            }).start();
        }
    }

    // ==========================================
    // 1. TRIM PROCESS
    // ==========================================
    private void processTrim(boolean keepAudio) {
        double d_segment = Double.parseDouble(etTrimSegment.getText().toString());
        setLoading(true);
        updateStatus("Menganalisa durasi asli...");
        listContainer.removeAllViews(); 

        new Thread(() -> {
            try {
                double totalDur = getVideoDuration(selectedVideoPath);
                if (totalDur == 0) throw new Exception("Video rusak atau durasi tidak terbaca.");

                double currentStart = 0.0;
                int part = 1;
                String fileName = new File(selectedVideoPath).getName().replaceAll("[.][^.]+$", "");

                while (currentStart < totalDur) {
                    final int currentPart = part;
                    runOnUiThread(() -> updateStatus("✂️ Mengekstrak Part " + currentPart + "..."));

                    String outName = fileName + "_part" + String.format(Locale.US, "%03d", part) + ".mp4";
                    String outPrivatePath = privateDir + "/" + outName;

                    String startStr = String.format(Locale.US, "%.3f", currentStart);
                    String durStr = String.format(Locale.US, "%.3f", d_segment);

                    String audioCmd = keepAudio ? "-c:a aac -b:a 128k" : "-an";
                    
                    String cmd = String.format(Locale.US, 
                            "-y -ss %s -t %s -i \"%s\" -c:v mpeg4 -q:v 3 %s \"%s\"",
                            startStr, durStr, selectedVideoPath, audioCmd, outPrivatePath);

                    FFmpegSession session = FFmpegKit.execute(cmd);

                    if (ReturnCode.isSuccess(session.getReturnCode())) {
                        moveToPublicFolder(outPrivatePath, outName);
                        runOnUiThread(() -> addToListResult("✅ " + outName));
                    } else {
                        // Menggunakan getAllLogsAsString agar dipastikan bisa dicompile
                        String realError = session.getLogsAsString(); 
                        throw new Exception("Gagal Part " + part + "\nFFmpeg Log: " + realError);
                    }

                    currentStart += d_segment;
                    part++;
                }

                final int totalFiles = part - 1;
                runOnUiThread(() -> {
                    setLoading(false);
                    updateStatus("✨ Selesai! (" + totalFiles + " file terpotong)");
                    tvConsoleLog.setText("Semua file sukses di-render.");
                    showToast("Mantap Bang! Cek folder EditingVideo");
                });

            } catch (Exception e) {
                final String errMsg = e.getMessage() != null ? e.getMessage() : "Unknown Error";
                runOnUiThread(() -> {
                    setLoading(false);
                    updateStatus("❌ Error Terjadi!");
                    tvConsoleLog.setText(errMsg);
                });
            }
        }).start();
    }

    // ==========================================
    // 2. BUTTER LOOP
    // ==========================================
    private void processButterLoop(int targetDur) {
        setLoading(true);
        updateStatus("Mempersiapkan Filter Butter Loop...");
        listContainer.removeAllViews();

        new Thread(() -> {
            try {
                double dur = getVideoDuration(selectedVideoPath);
                if (dur == 0) throw new Exception("Durasi video nol!");

                String fileName = new File(selectedVideoPath).getName();
                String tempUnit = privateDir + "/temp_unit_" + fileName;
                String finalPrivateOutput = privateDir + "/smooth_" + fileName;

                runOnUiThread(() -> updateStatus("Tahap 1: Membangun efek reverse..."));

                String filterComplex = "[0:v]setpts=PTS-STARTPTS[v1];[0:v]reverse,setpts=PTS-STARTPTS[v2];[v1][v2]concat=n=2:v=1:a=0,format=yuv420p[out]";
                
                String cmdUnit = String.format(Locale.US,
                        "-y -i \"%s\" -filter_complex \"%s\" -map [out] -an -c:v mpeg4 -q:v 3 \"%s\"",
                        selectedVideoPath, filterComplex, tempUnit);

                FFmpegSession session1 = FFmpegKit.execute(cmdUnit);
                if (!ReturnCode.isSuccess(session1.getReturnCode())) {
                    throw new Exception("Gagal Tahap 1: " + session1.getLogsAsString());
                }

                runOnUiThread(() -> updateStatus("Tahap 2: Melipatgandakan loop..."));

                double unitDur = dur * 2;
                int numLoops = (int) (targetDur / unitDur) + 1;

                String cmdFinal = String.format(Locale.US,
                        "-y -stream_loop %d -i \"%s\" -c copy -t %d \"%s\"",
                        numLoops, tempUnit, targetDur, finalPrivateOutput);

                FFmpegSession session2 = FFmpegKit.execute(cmdFinal);
                if (!ReturnCode.isSuccess(session2.getReturnCode())) {
                    throw new Exception("Gagal Tahap 2: " + session2.getLogsAsString());
                }

                new File(tempUnit).delete();
                moveToPublicFolder(finalPrivateOutput, "smooth_" + fileName);

                runOnUiThread(() -> {
                    setLoading(false);
                    addToListResult("🧈 smooth_" + fileName);
                    updateStatus("✨ Butter Loop Selesai!");
                    tvConsoleLog.setText("Sukses di-render.");
                    showToast("Super mulus Bang!");
                });

            } catch (Exception e) {
                final String errMsg = e.getMessage() != null ? e.getMessage() : "Unknown Error";
                runOnUiThread(() -> {
                    setLoading(false);
                    updateStatus("❌ Error Looping!");
                    tvConsoleLog.setText(errMsg);
                });
            }
        }).start();
    }

    // ==========================================
    // UTILITIES
    // ==========================================
    private void addToListResult(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#10B981")); // Hijau terang
        tv.setTextSize(13f);
        tv.setPadding(0, 4, 0, 4);
        listContainer.addView(tv);
    }

    private double getVideoDuration(String path) {
        MediaInformationSession session = FFprobeKit.getMediaInformation(path);
        if (session.getMediaInformation() != null && session.getMediaInformation().getDuration() != null) {
            return Double.parseDouble(session.getMediaInformation().getDuration());
        }
        return 0;
    }

    private void moveToPublicFolder(String sourcePath, String outputName) {
        File source = new File(sourcePath);
        File dest = new File(publicDir, outputName);
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[2048];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            source.delete();
        } catch (Exception e) {
            Log.e("MoveFile", "Gagal copy: " + e.getMessage());
        }
    }

    private String copyUriToPrivate(Uri uri) {
        File tempFile = new File(privateDir, "input_temp_" + System.currentTimeMillis() + ".mp4");
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(tempFile)) {
            byte[] buf = new byte[2048];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return tempFile.getAbsolutePath();
    }

    private void updateStatus(String msg) {
        tvStatus.setText(msg);
        tvConsoleLog.setText("");
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnTrimWithAudio.setEnabled(!isLoading);
        btnTrimNoAudio.setEnabled(!isLoading);
        btnLoop.setEnabled(!isLoading);
        btnSelectVideo.setEnabled(!isLoading);
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
