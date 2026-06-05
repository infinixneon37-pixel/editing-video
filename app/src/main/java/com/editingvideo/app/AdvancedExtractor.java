package com.editingvideo.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

public class AdvancedExtractor {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>]+?\\.(?:mp4|m3u8|webm)");
    private static final Pattern JSON_STATE_PATTERN = Pattern.compile("window\\._sharedData\\s*=\\s*(\\{.*?\\});");

    public static String analyzeAndExtract(String rawHtmlOrJson) {
        StringBuilder result = new StringBuilder();
        Matcher urlMatcher = URL_PATTERN.matcher(rawHtmlOrJson);
        result.append("=== DIRECT MEDIA LINKS ===\n");
        int count = 0;
        while (urlMatcher.find()) {
            result.append(++count).append(". ").append(urlMatcher.group()).append("\n");
        }

        Matcher jsonMatcher = JSON_STATE_PATTERN.matcher(rawHtmlOrJson);
        if (jsonMatcher.find()) {
            result.append("\n=== HIDDEN JSON API FOUND ===\n");
            try {
                String jsonStr = jsonMatcher.group(1);
                JSONObject root = new JSONObject(jsonStr);
                result.append("Parsed JSON Size: ").append(jsonStr.length()).append(" chars.\n");
            } catch (Exception e) {
                result.append("JSON Parse Error: ").append(e.getMessage());
            }
        }

        if (count == 0 && !jsonMatcher.find()) {
            result.append("No obvious links found. Further analysis needed.");
        }

        return result.toString();
    }
}
