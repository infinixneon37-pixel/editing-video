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

public class ExtractorFragment extends Fragment {
    private EditText editM3u8Url;
    private Button btnExtract;
    private TextView txtExtracted;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_extractor, container, false);
        editM3u8Url = v.findViewById(R.id.editM3u8Url);
        btnExtract = v.findViewById(R.id.btnExtract);
        txtExtracted = v.findViewById(R.id.txtExtracted);

        btnExtract.setOnClickListener(view -> {
            String url = editM3u8Url.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(getContext(), "URL Required", Toast.LENGTH_SHORT).show();
                return;
            }

            btnExtract.setEnabled(false);
            txtExtracted.setText("Fetching and deep extracting...");

            new Thread(() -> {
                try {
                    String response = HttpManager.get(url, null);
                    String m3u8Result = M3u8Parser.extractMediaLinks(response, url);
                    String deepExtractResult = AdvancedExtractor.analyzeAndExtract(response);

                    String finalOutput = "=== DEEP EXTRACTION ENGINE ===\n" + deepExtractResult + "\n\n=== M3U8 LINKS ===\n" + m3u8Result;

                    if (isAdded() && getActivity() != null) {
                        requireActivity().runOnUiThread(() -> {
                            btnExtract.setEnabled(true);
                            txtExtracted.setText(finalOutput);
                        });
                    }
                } catch (Exception e) {
                    if (isAdded() && getActivity() != null) {
                        requireActivity().runOnUiThread(() -> {
                            btnExtract.setEnabled(true);
                            txtExtracted.setText("Error: " + e.getMessage());
                        });
                    }
                }
            }).start();
        });

        return v;
    }
}
