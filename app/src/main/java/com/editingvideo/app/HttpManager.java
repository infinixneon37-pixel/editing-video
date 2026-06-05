package com.editingvideo.app;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HttpManager {
    
    private static final HttpLoggingInterceptor logging = new HttpLoggingInterceptor(message -> {
        System.out.println("SNIFFER LOG: " + message);
    }).setLevel(HttpLoggingInterceptor.Level.BODY);

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    public static String get(String url, Map<String, String> headersMap) throws IOException {
        Request.Builder builder = new Request.Builder().url(url);
        if (headersMap != null) builder.headers(Headers.of(headersMap));
        try (Response response = client.newCall(builder.build()).execute()) {
            return response.body() != null ? response.body().string() : "";
        }
    }

    public static String post(String url, Map<String, String> headersMap, String jsonBody) throws IOException {
        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json; charset=utf-8"));
        Request.Builder builder = new Request.Builder().url(url).post(body);
        if (headersMap != null) builder.headers(Headers.of(headersMap));
        try (Response response = client.newCall(builder.build()).execute()) {
            return response.body() != null ? response.body().string() : "";
        }
    }
}
