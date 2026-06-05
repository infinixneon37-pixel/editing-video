package com.editingvideo.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class DownloadFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_download, container, false);
        
        EditText editDlUrl = v.findViewById(R.id.editDlUrl);
        EditText editFileName = v.findViewById(R.id.editFileName);
        Button btnStartDownload = v.findViewById(R.id.btnStartDownload);

        btnStartDownload.setOnClickListener(view -> {
            String url = editDlUrl.getText().toString().trim();
            String file = editFileName.getText().toString().trim();
            
            if (url.isEmpty()) {
                editDlUrl.setError("Required");
                return;
            }
            if (file.isEmpty()) file = "download_" + System.currentTimeMillis() + ".mp4";

            DownloadManager.startDownload(requireContext(), url, file);
        });

        return v;
    }
}
