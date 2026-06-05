package com.editingvideo.app;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor; // Tambahkan ini

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HttpManager {
    
    // Konfigurasi Interceptor untuk mencatat Request & Response
    private static final HttpLoggingInterceptor logging = 
            new HttpLoggingInterceptor(message -> {
                // Di sini Anda bisa mengirim log ke Database atau Logcat
                System.out.println("SNIFFER: " + message);
            }).setLevel(HttpLoggingInterceptor.Level.BODY);

    // Tambahkan interceptor ke dalam OkHttpClient
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(logging) // Pasang pencegat di sini
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    // ... (metode get() dan post() tetap sama) ...
}
