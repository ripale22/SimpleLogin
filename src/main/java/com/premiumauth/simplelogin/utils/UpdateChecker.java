package com.premiumauth.simplelogin.utils;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class UpdateChecker {

    private static final String GITHUB_API = "https://api.github.com/repos/ripale22/SimpleLogin/releases/latest";
    private static final OkHttpClient CLIENT = new OkHttpClient();

    public static CompletableFuture<String> check() {
        return CompletableFuture.supplyAsync(() -> {
            Request req = new Request.Builder()
                    .url(GITHUB_API)
                    .header("Accept", "application/vnd.github+json")
                    .build();
            try (Response resp = CLIENT.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return null;
                String json = resp.body().string();
                return extractTag(json);
            } catch (IOException e) {
                return null;
            }
        });
    }

    public static String stripV(String tag) {
        return tag != null && tag.startsWith("v") ? tag.substring(1) : tag;
    }

    private static String extractTag(String json) {
        return extractJsonString(json, "\"tag_name\":\"", "\"");
    }

    private static String extractJsonString(String json, String key, String endDelim) {
        int start = json.indexOf(key);
        if (start == -1) return null;
        start += key.length();
        int end = json.indexOf(endDelim, start);
        if (end == -1) return null;
        return json.substring(start, end);
    }
}
