package com.editingvideo.app;

import android.os.Bundle;
import android.os.Environment;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private EditText editUrl;
    private EditText editHeaders;
    private EditText editBody;
    private Spinner spinnerMethod;
    private Button btnSend;
    private Button btnDownload;
    private TextView txtResponse;

    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editUrl = findViewById(R.id.editUrl);
        editHeaders = findViewById(R.id.editHeaders);
        editBody = findViewById(R.id.editBody);
        spinnerMethod = findViewById(R.id.spinnerMethod);
        btnSend = findViewById(R.id.btnSend);
        btnDownload = findViewById(R.id.btnDownload);
        txtResponse = findViewById(R.id.txtResponse);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"GET", "POST"}
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerMethod.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendRequest());

        btnDownload.setOnClickListener(v -> downloadFile());
    }

    private void sendRequest() {

        String url = editUrl.getText().toString().trim();

        if (url.isEmpty()) {
            Toast.makeText(this, "URL kosong", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {

                String method =
                        spinnerMethod.getSelectedItem().toString();

                Request.Builder builder =
                        new Request.Builder().url(url);

                String headersText =
                        editHeaders.getText().toString();

                if (!headersText.isEmpty()) {

                    String[] lines =
                            headersText.split("\n");

                    for (String line : lines) {

                        int index = line.indexOf(":");

                        if (index > 0) {

                            String key =
                                    line.substring(0, index).trim();

                            String value =
                                    line.substring(index + 1).trim();

                            builder.addHeader(key, value);
                        }
                    }
                }

                if ("POST".equals(method)) {

                    String bodyText =
                            editBody.getText().toString();

                    RequestBody body =
                            RequestBody.create(
                                    bodyText,
                                    MediaType.parse(
                                            "application/json; charset=utf-8"
                                    )
                            );

                    builder.post(body);
                }

                Request request = builder.build();

                try (Response response =
                             client.newCall(request).execute()) {

                    String result =
                            response.body() != null
                                    ? response.body().string()
                                    : "No Response";

                    runOnUiThread(() ->
                            txtResponse.setText(result)
                    );
                }

            } catch (Exception e) {

                runOnUiThread(() ->
                        txtResponse.setText(
                                "ERROR:\n" + e.getMessage()
                        )
                );
            }
        }).start();
    }

    private void downloadFile() {

        String url =
                editUrl.getText().toString().trim();

        if (url.isEmpty()) {
            Toast.makeText(this, "URL kosong", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {

            try {

                Request request =
                        new Request.Builder()
                                .url(url)
                                .build();

                try (Response response =
                             client.newCall(request).execute()) {

                    if (response.body() == null) {
                        throw new IOException("Empty body");
                    }

                    String fileName =
                            "download_" +
                            System.currentTimeMillis();

                    File dir =
                            Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_DOWNLOADS
                            );

                    if (!dir.exists()) {
                        dir.mkdirs();
                    }

                    File outputFile =
                            new File(dir, fileName);

                    InputStream input =
                            response.body().byteStream();

                    FileOutputStream output =
                            new FileOutputStream(outputFile);

                    byte[] buffer =
                            new byte[8192];

                    int len;

                    while ((len = input.read(buffer)) != -1) {
                        output.write(buffer, 0, len);
                    }

                    output.flush();
                    output.close();
                    input.close();

                    runOnUiThread(() ->
                            Toast.makeText(
                                    MainActivity.this,
                                    "Saved:\n" +
                                            outputFile.getAbsolutePath(),
                                    Toast.LENGTH_LONG
                            ).show()
                    );
                }

            } catch (Exception e) {

                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
            }

        }).start();
    }
}
