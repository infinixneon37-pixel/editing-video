package com.editingvideo.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.MediaInformationSession;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private TextView tvSelectedFile, tvStatus;
    private EditText etTrimSegment, etLoopDuration;
    private Button btnSelectVideo, btnTrim, btnLoop;
    
    private String selectedVideoPath = null;
    private String privateDir;
    private String publicDir;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvSelectedFile = findViewById(R.id.tvSelectedFile);
        tvStatus = findViewById(R.id.tvStatus);
        etTrimSegment = findViewById(R.id.etTrimSegment);
        etLoopDuration = findViewById(R.id.etLoopDuration);
        btnSelectVideo = findViewById(R.id.btnSelectVideo);
        btnTrim = findViewById(R.id.btnTrim);
        btnLoop = findViewById(R.id.btnLoop);

        // Path Private & Public
        privateDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES).getAbsolutePath();
        publicDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Movies/EditingVideo";

        // Buat folder publik jika belum ada
        File pubFolder = new File(publicDir);
        if (!pubFolder.exists()) pubFolder.mkdirs();

        btnSelectVideo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("video/*");
            startActivityForResult(intent, 101);
        });

        btnTrim.setOnClickListener(v -> {
            if (selectedVideoPath == null) return;
            double segment = Double.parseDouble(etTrimSegment.getText().toString().isEmpty() ? "0.5" : etTrimSegment.getText().toString());
            processTrim(selectedVideoPath, segment);
        });

        btnLoop.setOnClickListener(v -> {
            if (selectedVideoPath == null) return;
            int targetDur = Integer.parseInt(etLoopDuration.getText().toString().isEmpty() ? "15" : etLoopDuration.getText().toString());
            processButterLoop(selectedVideoPath, targetDur);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            selectedVideoPath = copyUriToPrivate(uri);
            tvSelectedFile.setText("Terpilih: " + new File(selectedVideoPath).getName());
            tvSelectedFile.setTextColor(0xFF00AA00);
        }
    }

    private void processTrim(String inputPath, double d_segment) {
        updateStatus("Memulai Trim...");
        executor.execute(() -> {
            try {
                double totalDur = getVideoDuration(inputPath);
                double currentStart = 0.0;
                int part = 1;
                String fileName = new File(inputPath).getName().replace(".mp4", "").replace(".mov", "");

                while (currentStart < totalDur) {
                    final int currentPart = part;
                    runOnUiThread(() -> tvStatus.setText("Processing Part " + currentPart + "..."));

                    String outName = fileName + "_part" + String.format("%03d", part) + ".mp4";
                    String outPrivatePath = privateDir + "/" + outName;

                    String cmd = String.format("-y -ss %.3f -t %.3f -i \"%s\" -c:v libx264 -preset ultrafast -crf 23 -an \"%s\"",
                            currentStart, d_segment, inputPath, outPrivatePath);

                    FFmpegKit.execute(cmd);
                    moveToPublicFolder(outPrivatePath, outName);

                    currentStart += d_segment;
                    part++;
                }
                updateStatus("✅ Trim Selesai! Cek folder Movies/EditingVideo");
            } catch (Exception e) {
                updateStatus("❌ Error: " + e.getMessage());
            }
        });
    }

    private void processButterLoop(String inputPath, int targetDur) {
        updateStatus("Memulai Butter Loop...");
        executor.execute(() -> {
            try {
                double dur = getVideoDuration(inputPath);
                if (dur == 0) throw new Exception("Durasi video tidak valid");

                String fileName = new File(inputPath).getName();
                String tempUnit = privateDir + "/temp_unit_" + fileName;
                String finalPrivateOutput = privateDir + "/smooth_" + fileName;

                runOnUiThread(() -> tvStatus.setText("1/2: Menyatukan frame reverse..."));

                String filterComplex = "[0:v]setpts=PTS-STARTPTS[v1];[0:v]reverse,setpts=PTS-STARTPTS[v2];[v1][v2]concat=n=2:v=1:a=0,format=yuv420p[out]";
                String cmdUnit = String.format("-y -i \"%s\" -filter_complex \"%s\" -map [out] -an -c:v libx264 -preset ultrafast -crf 18 \"%s\"",
                        inputPath, filterComplex, tempUnit);
                FFmpegKit.execute(cmdUnit);

                runOnUiThread(() -> tvStatus.setText("2/2: Looping sampai " + targetDur + " detik..."));

                double unitDur = dur * 2;
                int numLoops = (int) (targetDur / unitDur) + 1;

                String cmdFinal = String.format("-y -stream_loop %d -i \"%s\" -c copy -t %d \"%s\"",
                        numLoops, tempUnit, targetDur, finalPrivateOutput);
                FFmpegKit.execute(cmdFinal);

                new File(tempUnit).delete();
                moveToPublicFolder(finalPrivateOutput, "smooth_" + fileName);

                updateStatus("✅ Loop Selesai! Cek folder Movies/EditingVideo");
            } catch (Exception e) {
                updateStatus("❌ Error: " + e.getMessage());
            }
        });
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
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            source.delete();
        } catch (Exception e) {
            Log.e("MoveFile", "Gagal mindahin file: " + e.getMessage());
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
        runOnUiThread(() -> tvStatus.setText(msg));
    }
}
