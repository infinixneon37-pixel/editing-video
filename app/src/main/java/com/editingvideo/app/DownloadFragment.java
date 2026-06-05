package com.editingvideo.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class DownloadFragment extends Fragment {
    
    private EditText editDlUrl, editFileName;
    private Button btnStartDownload;
    private TextView txtDlStatus;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_download, container, false);
        
        editDlUrl = v.findViewById(R.id.editDlUrl);
        editFileName = v.findViewById(R.id.editFileName);
        btnStartDownload = v.findViewById(R.id.btnStartDownload);
        txtDlStatus = v.findViewById(R.id.txtDlStatus);

        btnStartDownload.setOnClickListener(view -> {
            String url = editDlUrl.getText().toString().trim();
            String rawFile = editFileName.getText().toString().trim();
            if (url.isEmpty()) {
                editDlUrl.setError("URL Required");
                return;
            }

            String file = rawFile.isEmpty() ? "dl_" + System.currentTimeMillis() + ".mp4" : System.currentTimeMillis() + "_" + rawFile;
            btnStartDownload.setEnabled(false);
            txtDlStatus.setText("Status: Connecting chunks...");

            SegmentedDownloader.startDownload(url, file, new SegmentedDownloader.DownloadCallback() {
                @Override
                public void onProgress(int progress) {}

                @Override
                public void onComplete(String filePath) {
                    if (isAdded() && getActivity() != null) {
                        requireActivity().runOnUiThread(() -> {
                            btnStartDownload.setEnabled(true);
                            txtDlStatus.setText("Status: Done!\nSaved to: " + filePath);
                        });
                    }
                }

                @Override
                public void onError(String error) {
                    if (isAdded() && getActivity() != null) {
                        requireActivity().runOnUiThread(() -> {
                            btnStartDownload.setEnabled(true);
                            txtDlStatus.setText("Status: ERROR - " + error);
                        });
                    }
                }
            });
        });

        return v;
    }
}
