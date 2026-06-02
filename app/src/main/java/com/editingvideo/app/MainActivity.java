package com.editingvideo.app;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.arthenica.ffmpegkit.*;
import java.io.*;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private Button btnSelect, btnTrim, btnLoop, btnMerge;
    private TextView tvStatus, tvSelected;
    private EditText etTrim;
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
        etTrim = findViewById(R.id.etTrim);

        privateDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES).getAbsolutePath();
        publicDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Movies/EditingVideo";
        new File(publicDir).mkdirs();

        btnSelect.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("video/*");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(i, 101);
        });

        btnMerge.setOnClickListener(v -> processMerge());
        btnTrim.setOnClickListener(v -> processTrim());
        btnLoop.setOnClickListener(v -> processLoop());
    }

    private void processTrim() {
        if (selectedPaths.isEmpty() || etTrim.getText().toString().isEmpty()) return;
        setLoading(true);
        new Thread(() -> {
            String path = selectedPaths.get(0);
            File outDir = new File(publicDir, "TRIM_" + System.currentTimeMillis());
            outDir.mkdirs();
            String cmd = String.format("-y -i \"%s\" -c copy -map 0 \"%s/part_%%03d.mp4\"", path, outDir.getAbsolutePath());
            FFmpegKit.execute(cmd);
            finishTask("Trim Selesai!");
        }).start();
    }

    private void processLoop() {
        if (selectedPaths.isEmpty()) return;
        setLoading(true);
        new Thread(() -> {
            String path = selectedPaths.get(0);
            String out = publicDir + "/LOOP_" + System.currentTimeMillis() + ".mp4";
            String filter = "[0:v]reverse[r];[0:v][r]concat=n=2:v=1:a=1[out]";
            String cmd = String.format("-y -i \"%s\" -filter_complex \"%s\" -map [out] -c copy \"%s\"", path, filter, out);
            FFmpegKit.execute(cmd);
            finishTask("Loop Selesai!");
        }).start();
    }

    private void processMerge() {
        if (selectedPaths.size() < 2) return;
        setLoading(true);
        new Thread(() -> {
            try {
                File list = new File(privateDir, "list.txt");
                FileWriter w = new FileWriter(list);
                for (String p : selectedPaths) w.write("file '" + p + "'\n");
                w.close();
                String out = publicDir + "/MERGED_" + System.currentTimeMillis() + ".mp4";
                String cmd = String.format("-y -f concat -safe 0 -i \"%s\" -c copy \"%s\"", list.getAbsolutePath(), out);
                if (ReturnCode.isSuccess(FFmpegKit.execute(cmd).getReturnCode())) {
                    for (String p : selectedPaths) new File(p).delete();
                    selectedPaths.clear();
                    finishTask("Merge Berhasil!");
                }
            } catch (Exception e) { finishTask("Error: " + e.getMessage()); }
        }).start();
    }

    @Override
    protected void onActivityResult(int r, int c, Intent data) {
        super.onActivityResult(r, c, data);
        if (r == 101 && c == RESULT_OK && data != null) {
            selectedPaths.clear();
            if (data.getClipData() != null) {
                ClipData cd = data.getClipData();
                for (int i = 0; i < cd.getItemCount(); i++) selectedPaths.add(copyUri(cd.getItemAt(i).getUri()));
            } else if (data.getData() != null) selectedPaths.add(copyUri(data.getData()));
            tvSelected.setText("Dipilih: " + selectedPaths.size() + " video");
        }
    }

    private String copyUri(Uri u) {
        File f = new File(privateDir, "tmp_" + System.currentTimeMillis() + ".mp4");
        try (InputStream in = getContentResolver().openInputStream(u); OutputStream out = new FileOutputStream(f)) {
            byte[] b = new byte[4096]; int l; while ((l = in.read(b)) > 0) out.write(b, 0, l);
        } catch (Exception e) { e.printStackTrace(); }
        return f.getAbsolutePath();
    }
    
    private void setLoading(boolean b) { runOnUiThread(() -> findViewById(R.id.progressBar).setVisibility(b ? View.VISIBLE : View.GONE)); }
    private void finishTask(String m) { runOnUiThread(() -> { setLoading(false); tvStatus.setText(m); }); }
}
