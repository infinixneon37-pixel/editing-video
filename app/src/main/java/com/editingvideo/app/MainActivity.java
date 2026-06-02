package com.editingvideo.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.arthenica.ffmpegkit.*;
import java.io.*;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private Button btnSelect, btnTrim, btnLoop, btnMerge;
    private TextView tvStatus, tvSelected;
    private List<String> selectedPaths = new ArrayList<>();
    private String privateDir, publicDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnSelect = findViewById(R.id.btnSelect);
        btnTrim = findViewById(R.id.btnTrim);
        btnLoop = findViewById(R.id.btnLoop);
        btnMerge = findViewById(R.id.btnMerge);
        tvStatus = findViewById(R.id.tvStatus);
        tvSelected = findViewById(R.id.tvSelected);

        privateDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES).getAbsolutePath();
        publicDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Movies/EditingVideo";
        new File(publicDir).mkdirs();

        btnSelect.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("video/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(intent, 101);
        });

        btnMerge.setOnClickListener(v -> processMerge());
        // Tambahkan logic Trim dan Loop seperti versi sebelumnya...
    }

    private void processMerge() {
        if (selectedPaths.isEmpty()) return;
        new Thread(() -> {
            try {
                File listFile = new File(privateDir, "list.txt");
                FileWriter writer = new FileWriter(listFile);
                for (String p : selectedPaths) writer.write("file '" + p + "'\n");
                writer.close();

                String outPath = publicDir + "/MERGED_" + System.currentTimeMillis() + ".mp4";
                String cmd = String.format("-y -f concat -safe 0 -i \"%s\" -c copy \"%s\"", listFile.getAbsolutePath(), outPath);
                
                if (ReturnCode.isSuccess(FFmpegKit.execute(cmd).getReturnCode())) {
                    for (String p : selectedPaths) new File(p).delete();
                    selectedPaths.clear();
                    runOnUiThread(() -> tvStatus.setText("Merge Berhasil & Video Asli Dihapus!"));
                }
            } catch (Exception e) { runOnUiThread(() -> tvStatus.setText("Error: " + e.getMessage())); }
        }).start();
    }
}
