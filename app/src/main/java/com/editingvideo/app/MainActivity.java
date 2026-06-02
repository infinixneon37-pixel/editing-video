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

        btnTrimWithAudio.setOnClickListener(v -> { if (checkInput(etTrimSegment)) processTrim(true); });
        btnTrimNoAudio.setOnClickListener(v -> { if (checkInput(etTrimSegment)) processTrim(false); });
        btnLoop.setOnClickListener(v -> {
            if (checkInput(etLoopDuration)) {
                processButterLoop(Integer.parseInt(etLoopDuration.getText().toString()));
            }
        });
    }

    private boolean checkInput(EditText et) {
        if (selectedVideoPath == null) { showToast("Pilih video dulu, Bang!"); return false; }
        if (et.getText().toString().isEmpty()) { showToast("Isi durasi dulu!"); return false; }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
            setLoading(true);
            new Thread(() -> {
                selectedVideoPath = copyUriToPrivate(data.getData());
                runOnUiThread(() -> {
                    setLoading(false);
                    tvSelectedFile.setText("File Siap: " + new File(selectedVideoPath).getName());
                    tvSelectedFile.setTextColor(Color.parseColor("#10B981"));
                    updateStatus("Standby. Video siap diproses!");
                });
            }).start();
        }
    }

    private void processTrim(boolean keepAudio) {
        double d_segment = Double.parseDouble(etTrimSegment.getText().toString());
        setLoading(true);
        updateStatus("Menganalisa durasi...");
        listContainer.removeAllViews();

        new Thread(() -> {
            try {
                double totalDur = getVideoDuration(selectedVideoPath);
                double currentStart = 0.0;
                int part = 1;
                String fileName = new File(selectedVideoPath).getName().replaceAll("[.][^.]+$", "");

                while (currentStart < totalDur) {
                    final int currentPart = part;
                    runOnUiThread(() -> updateStatus("✂️ Mengekstrak Part " + currentPart + "..."));
                    String outName = fileName + "_part" + String.format(Locale.US, "%03d", part) + ".mp4";
                    String outPath = privateDir + "/" + outName;
                    
                    String cmd = String.format(Locale.US, "-y -ss %.3f -t %.3f -i \"%s\" -c:v mpeg4 -q:v 3 %s \"%s\"", 
                                 currentStart, d_segment, selectedVideoPath, keepAudio ? "-c:a aac -b:a 128k" : "-an", outPath);

                    FFmpegSession session = FFmpegKit.execute(cmd);
                    if (ReturnCode.isSuccess(session.getReturnCode())) {
                        moveToPublicFolder(outPath, outName);
                        runOnUiThread(() -> addToListResult("✅ " + outName));
                    } else { throw new Exception(session.getLogsAsString()); }
                    currentStart += d_segment; part++;
                }
                finishTask("✨ Trim Selesai!");
            } catch (Exception e) { finishError(e.getMessage()); }
        }).start();
    }

    private void processButterLoop(int targetDur) {
        setLoading(true);
        updateStatus("Mempersiapkan Filter...");
        listContainer.removeAllViews();

        new Thread(() -> {
            try {
                double dur = getVideoDuration(selectedVideoPath);
                String tempUnit = privateDir + "/temp_unit.mp4";
                String finalOut = privateDir + "/smooth_" + new File(selectedVideoPath).getName();

                String filterComplex = "[0:v]setpts=PTS-STARTPTS[v1];[0:v]reverse,setpts=PTS-STARTPTS[v2];[v1][v2]concat=n=2:v=1:a=0,format=yuv420p[out]";
                String cmd1 = String.format(Locale.US, "-y -i \"%s\" -filter_complex \"%s\" -map [out] -an -c:v mpeg4 -q:v 3 \"%s\"", selectedVideoPath, filterComplex, tempUnit);
                
                if (!ReturnCode.isSuccess(FFmpegKit.execute(cmd1).getReturnCode())) throw new Exception("Gagal Reverse");

                double unitDur = dur * 2;
                int numLoops = (int) (targetDur / unitDur) + 1;
                String cmd2 = String.format(Locale.US, "-y -stream_loop %d -i \"%s\" -c copy -t %d \"%s\"", numLoops, tempUnit, targetDur, finalOut);
                
                if (!ReturnCode.isSuccess(FFmpegKit.execute(cmd2).getReturnCode())) throw new Exception("Gagal Loop");

                new File(tempUnit).delete();
                moveToPublicFolder(finalOut, "smooth_" + new File(selectedVideoPath).getName());
                finishTask("✨ Butter Loop Selesai!");
            } catch (Exception e) { finishError(e.getMessage()); }
        }).start();
    }

    // UTILITIES
    private void addToListResult(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#10B981"));
        listContainer.addView(tv);
    }
    private double getVideoDuration(String path) {
        MediaInformationSession s = FFprobeKit.getMediaInformation(path);
        return (s.getMediaInformation() != null) ? Double.parseDouble(s.getMediaInformation().getDuration()) : 0;
    }
    private void moveToPublicFolder(String sPath, String name) {
        File s = new File(sPath); File d = new File(publicDir, name);
        try (InputStream in = new FileInputStream(s); OutputStream out = new FileOutputStream(d)) {
            byte[] b = new byte[2048]; int l; while ((l = in.read(b)) > 0) out.write(b, 0, l); s.delete();
        } catch (Exception e) { Log.e("Move", e.getMessage()); }
    }
    private String copyUriToPrivate(Uri u) {
        File f = new File(privateDir, "tmp_" + System.currentTimeMillis() + ".mp4");
        try (InputStream in = getContentResolver().openInputStream(u); OutputStream out = new FileOutputStream(f)) {
            byte[] b = new byte[2048]; int l; while ((l = in.read(b)) > 0) out.write(b, 0, l);
        } catch (Exception e) { e.printStackTrace(); }
        return f.getAbsolutePath();
    }
    private void updateStatus(String msg) { tvStatus.setText(msg); tvConsoleLog.setText(""); }
    private void finishTask(String msg) { runOnUiThread(() -> { setLoading(false); updateStatus(msg); }); }
    private void finishError(String m) { runOnUiThread(() -> { setLoading(false); updateStatus("❌ Error"); tvConsoleLog.setText(m); }); }
    private void setLoading(boolean b) { progressBar.setVisibility(b?View.VISIBLE:View.GONE); btnTrimWithAudio.setEnabled(!b); btnTrimNoAudio.setEnabled(!b); btnLoop.setEnabled(!b); }
    private void showToast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
