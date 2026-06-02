package com.editingvideo.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.arthenica.ffmpegkit.FFmpegKit;
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
    private Button btnSelectVideo, btnTrim, btnLoop;
    private ProgressBar progressBar;

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
        btnTrim = findViewById(R.id.btnTrim);
        btnLoop = findViewById(R.id.btnLoop);
        progressBar = findViewById(R.id.progressBar);

        privateDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES).getAbsolutePath();
        publicDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Movies/EditingVideo";

        File pubFolder = new File(publicDir);
        if (!pubFolder.exists()) pubFolder.mkdirs();

        btnSelectVideo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("video/*");
            startActivityForResult(intent, 101);
        });

        btnTrim.setOnClickListener(v -> {
            if (selectedVideoPath == null) {
                showToast("Pilih video dulu, Bang!");
                return;
            }
            double segment = Double.parseDouble(etTrimSegment.getText().toString().isEmpty() ? "0.5" : etTrimSegment.getText().toString());
            processTrim(selectedVideoPath, segment);
        });

        btnLoop.setOnClickListener(v -> {
            if (selectedVideoPath == null) {
                showToast("Pilih video dulu, Bang!");
                return;
            }
            int targetDur = Integer.parseInt(etLoopDuration.getText().toString().isEmpty() ? "15" : etLoopDuration.getText().toString());
            processButterLoop(selectedVideoPath, targetDur);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
            setLoading(true);
            updateStatus("Menyalin video ke folder aman...");
            new Thread(() -> {
                Uri uri = data.getData();
                selectedVideoPath = copyUriToPrivate(uri);
                runOnUiThread(() -> {
                    setLoading(false);
                    tvSelectedFile.setText("File Siap: " + new File(selectedVideoPath).getName());
                    tvSelectedFile.setTextColor(0xFF4CAF50); // Hijau
                    updateStatus("Video siap diproses!");
                });
            }).start();
        }
    }

    // ==========================================
    // 1. TRIM PROCESS PRO-LEVEL
    // ==========================================
    private void processTrim(String inputPath, double d_segment) {
        setLoading(true);
        updateStatus("Memulai Trim (menganalisa durasi)...");

        new Thread(() -> {
            try {
                double totalDur = getVideoDuration(inputPath);
                if (totalDur == 0) throw new Exception("Video tidak valid/rusak.");

                double currentStart = 0.0;
                int part = 1;
                String fileName = new File(inputPath).getName().replaceAll("[.][^.]+$", ""); // Hapus ekstensi

                while (currentStart < totalDur) {
                    final int currentPart = part; // Variabel final agar bisa dibaca lambda
                    runOnUiThread(() -> updateStatus("Mengekstrak Part " + currentPart + "..."));

                    String outName = fileName + "_part" + String.format("%03d", part) + ".mp4";
                    String outPrivatePath = privateDir + "/" + outName;

                    String startStr = String.format(Locale.US, "%.3f", currentStart);
                    String durStr = String.format(Locale.US, "%.3f", d_segment);

                    String[] cmdArray = new String[]{
                            "-y", "-ss", startStr, "-t", durStr,
                            "-i", inputPath,
                            "-c:v", "libx264", "-preset", "ultrafast", "-crf", "23", "-an",
                            outPrivatePath
                    };

                    com.arthenica.ffmpegkit.FFmpegSession session = FFmpegKit.executeWithArguments(cmdArray);

                    if (ReturnCode.isSuccess(session.getReturnCode())) {
                        moveToPublicFolder(outPrivatePath, outName);
                    } else {
                        throw new Exception("FFmpeg Gagal Part " + part + "\nLog: " + session.getFailStackTrace());
                    }

                    currentStart += d_segment;
                    part++;
                }

                final int totalFiles = part - 1; // Variabel final untuk menampilkan total
                runOnUiThread(() -> {
                    setLoading(false);
                    updateStatus("✅ Trim Sukses! (" + totalFiles + " file)");
                    tvConsoleLog.setText("Tersimpan di: Movies/EditingVideo");
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    updateStatus("❌ Error Terjadi!");
                    tvConsoleLog.setText(e.getMessage());
                });
            }
        }).start();
    }

    // ==========================================
    // 2. BUTTER LOOP PRO-LEVEL
    // ==========================================
    private void processButterLoop(String inputPath, int targetDur) {
        setLoading(true);
        updateStatus("Mempersiapkan Filter Butter Loop...");

        new Thread(() -> {
            try {
                double dur = getVideoDuration(inputPath);
                if (dur == 0) throw new Exception("Durasi video nol!");

                String fileName = new File(inputPath).getName();
                String tempUnit = privateDir + "/temp_unit_" + fileName;
                String finalPrivateOutput = privateDir + "/smooth_" + fileName;

                runOnUiThread(() -> updateStatus("Tahap 1: Membangun efek maju-mundur (Reverse)..."));

                String filterComplex = "[0:v]setpts=PTS-STARTPTS[v1];[0:v]reverse,setpts=PTS-STARTPTS[v2];[v1][v2]concat=n=2:v=1:a=0,format=yuv420p[out]";

                String[] cmdUnit = new String[]{
                        "-y", "-i", inputPath,
                        "-filter_complex", filterComplex,
                        "-map", "[out]", "-an", "-c:v", "libx264", "-preset", "ultrafast", "-crf", "18",
                        tempUnit
                };

                com.arthenica.ffmpegkit.FFmpegSession session1 = FFmpegKit.executeWithArguments(cmdUnit);
                if (!ReturnCode.isSuccess(session1.getReturnCode())) {
                    throw new Exception("Gagal di Tahap 1: " + session1.getFailStackTrace());
                }

                runOnUiThread(() -> updateStatus("Tahap 2: Looping sampai durasi " + targetDur + " detik..."));

                double unitDur = dur * 2;
                int numLoops = (int) (targetDur / unitDur) + 1;

                String[] cmdFinal = new String[]{
                        "-y", "-stream_loop", String.valueOf(numLoops),
                        "-i", tempUnit,
                        "-c", "copy", "-t", String.valueOf(targetDur),
                        finalPrivateOutput
                };

                com.arthenica.ffmpegkit.FFmpegSession session2 = FFmpegKit.executeWithArguments(cmdFinal);
                if (!ReturnCode.isSuccess(session2.getReturnCode())) {
                    throw new Exception("Gagal di Tahap 2: " + session2.getFailStackTrace());
                }

                new File(tempUnit).delete();
                moveToPublicFolder(finalPrivateOutput, "smooth_" + fileName);

                runOnUiThread(() -> {
                    setLoading(false);
                    updateStatus("✅ Butter Loop Sukses!");
                    tvConsoleLog.setText("Tersimpan di: Movies/EditingVideo");
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    updateStatus("❌ Error Looping!");
                    tvConsoleLog.setText(e.getMessage());
                });
            }
        }).start();
    }

    // ==========================================
    // UTILITIES
    // ==========================================
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
            byte[] buf = new byte[1024];
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
            byte[] buf = new byte[1024];
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
        btnTrim.setEnabled(!isLoading);
        btnLoop.setEnabled(!isLoading);
        btnSelectVideo.setEnabled(!isLoading);
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
