package com.editingvideo.app;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.nav_request).setOnClickListener(v -> loadFragment(new RequestFragment()));
        findViewById(R.id.nav_download).setOnClickListener(v -> loadFragment(new DownloadFragment()));
        findViewById(R.id.nav_browser).setOnClickListener(v -> loadFragment(new BrowserFragment()));
        findViewById(R.id.nav_extractor).setOnClickListener(v -> loadFragment(new ExtractorFragment()));
        findViewById(R.id.nav_history).setOnClickListener(v -> loadFragment(new HistoryFragment()));

        if (savedInstanceState == null) {
            loadFragment(new RequestFragment());
        }
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
    }
}
