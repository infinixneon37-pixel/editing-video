package com.editingvideo.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class JsonFormatter {
    public static String format(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) return "Empty Response";
        try {
            rawJson = rawJson.trim();
            if (rawJson.startsWith("{")) return new JSONObject(rawJson).toString(4);
            else if (rawJson.startsWith("[")) return new JSONArray(rawJson).toString(4);
        } catch (JSONException e) {
            return rawJson; 
        }
        return rawJson;
    }
}
