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
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_extractor, container, false);
        
        EditText editM3u8Url = v.findViewById(R.id.editM3u8Url);
        Button btnExtract = v.findViewById(R.id.btnExtract);
        TextView txtExtracted = v.findViewById(R.id.txtExtracted);

        btnExtract.setOnClickListener(view -> {
            String url = editM3u8Url.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(getContext(), "URL Required", Toast.LENGTH_SHORT).show();
                return;
            }

            txtExtracted.setText("Fetching Playlist...");
            new Thread(() -> {
                try {
                    String response = HttpManager.get(url, null);
                    String parsed = M3u8Parser.extractMediaLinks(response, url);
                    requireActivity().runOnUiThread(() -> txtExtracted.setText(parsed));
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> txtExtracted.setText("Error: " + e.getMessage()));
                }
            }).start();
        });

        return v;
    }
}
