package com.editingvideo.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.util.HashMap;
import java.util.Map;

public class RequestFragment extends Fragment {
    private EditText editUrl, editHeaders, editBody;
    private Spinner spinnerMethod;
    private TextView txtResponse;
    private HistoryDatabase db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_request, container, false);

        editUrl = v.findViewById(R.id.editUrl);
        editHeaders = v.findViewById(R.id.editHeaders);
        editBody = v.findViewById(R.id.editBody);
        spinnerMethod = v.findViewById(R.id.spinnerMethod);
        txtResponse = v.findViewById(R.id.txtResponse);
        Button btnSend = v.findViewById(R.id.btnSend);
        db = new HistoryDatabase(getContext());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new String[]{"GET", "POST"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMethod.setAdapter(adapter);

        btnSend.setOnClickListener(view -> executeRequest());
        return v;
    }

    private void executeRequest() {
        String url = editUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(getContext(), "URL Kosong", Toast.LENGTH_SHORT).show();
            return;
        }

        String method = spinnerMethod.getSelectedItem().toString();
        String headerStr = editHeaders.getText().toString();
        String bodyStr = editBody.getText().toString();

        Map<String, String> headers = new HashMap<>();
        if (!headerStr.isEmpty()) {
            for (String line : headerStr.split("\n")) {
                int idx = line.indexOf(":");
                if (idx > 0) headers.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
            }
        }

        db.addHistory(url, method);
        txtResponse.setText("Loading...");

        new Thread(() -> {
            try {
                String result = method.equals("POST") ? HttpManager.post(url, headers, bodyStr) : HttpManager.get(url, headers);
                final String formatted = JsonFormatter.format(result);
                requireActivity().runOnUiThread(() -> txtResponse.setText(formatted));
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> txtResponse.setText("ERROR: " + e.getMessage()));
            }
        }).start();
    }
}
