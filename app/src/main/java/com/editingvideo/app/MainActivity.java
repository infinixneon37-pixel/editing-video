package com.editingvideo.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.arthenica.ffmpegkit.*;
import java.io.*;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private TextView tvStatus, tvSelectedFile;
    private EditText etTrimSegment, etLoopDuration;
    private Button btnSelectVideo, btnTrimWithAudio, btnTrimNoAudio, btnLoop, btnMerge;
    private ProgressBar progressBar;
    private LinearLayout listContainer;
    private List<String> selectedPaths = new ArrayList<>();
    private String privateDir, publicDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind Views
        tvStatus = findViewById(R.id.tvStatus);
        tvSelectedFile = findViewById(R.id.tvSelectedFile);
        etTrimSegment = findViewById(R.id.etTrimSegment);
        etLoopDuration = findViewById(R.id.etLoopDuration);
        btnSelectVideo = findViewById(R.id.btnSelectVideo);
        btnTrimWithAudio = findViewById(R.id.btnTrimWithAudio);
        btnTrimNoAudio = findViewById(R.id.btnTrimNoAudio);
        btnLoop = findViewById(R.id.btnLoop);
        btnMerge = findViewById(R.id.btnMerge);
        progressBar = findViewById(R.id.progressBar);
        listContainer = findViewById(R.id.listContainer);

        privateDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES).getAbsolutePath();
        publicDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Movies/EditingVideo";

        btnSelectVideo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("video/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(intent, 101);
        });

        btnMerge.setOnClickListener(v -> processMerge());
        // Tambahkan logic lainnya seperti contoh sebelumnya...
    }

    private void processMerge() {
        if (selectedPaths.isEmpty()) return;
        setLoading(true);
        new Thread(() -> {
            try {
                File listFile = new File(privateDir, "list.txt");
                FileWriter writer = new FileWriter(listFile);
                for (String p : selectedPaths) writer.write("file '" + p + "'\n");
                writer.close();

                String outPath = publicDir + "/MERGED_" + System.currentTimeMillis() + ".mp4";
                String cmd = String.format("-y -f concat -safe 0 -i \"%s\" -c copy \"%s\"", listFile.getAbsolutePath(), outPath);
                
                if (ReturnCode.isSuccess(FFmpegKit.execute(cmd).getReturnCode())) {
                    for (String p : selectedPaths) new File(p).delete(); // Hapus file asli
                    selectedPaths.clear();
                    finishTask("Berhasil Digabung!");
                }
            } catch (Exception e) { finishError(e.getMessage()); }
        }).start();
    }
    
    // Tambahkan method setLoading, finishTask, dll sesuai kebutuhan
    private void setLoading(boolean b) { runOnUiThread(() -> progressBar.setVisibility(b?View.VISIBLE:View.GONE)); }
    private void finishTask(String m) { runOnUiThread(() -> { setLoading(false); tvStatus.setText(m); }); }
    private void finishError(String m) { runOnUiThread(() -> { setLoading(false); tvStatus.setText("Error"); }); }
}
