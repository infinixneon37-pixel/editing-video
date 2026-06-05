package com.editingvideo.app;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class BrowserFragment extends Fragment {
    @SuppressLint("SetJavaScriptEnabled")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_browser, container, false);
        EditText editWebUrl = v.findViewById(R.id.editWebUrl);
        Button btnGo = v.findViewById(R.id.btnGo);
        WebView webView = v.findViewById(R.id.webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        btnGo.setOnClickListener(view -> {
            String url = editWebUrl.getText().toString().trim();
            if (!url.startsWith("http")) url = "https://" + url;
            webView.loadUrl(url);
        });

        return v;
    }
}
