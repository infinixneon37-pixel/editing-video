package com.editingvideo.app;

import java.util.ArrayList;
import java.util.List;

public class M3u8Parser {
    public static String extractMediaLinks(String m3u8Content, String baseUrl) {
        if (m3u8Content == null || m3u8Content.isEmpty()) return "Empty content";
        
        String[] lines = m3u8Content.split("\n");
        StringBuilder result = new StringBuilder();
        int count = 0;

        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                if (!line.startsWith("http")) {
                    // Handle relative URLs
                    line = baseUrl.substring(0, baseUrl.lastIndexOf("/") + 1) + line;
                }
                result.append("Stream ").append(++count).append(":\n").append(line).append("\n\n");
            }
        }
        return count > 0 ? result.toString() : "No media segments found.\n\nRaw:\n" + m3u8Content;
    }
}
