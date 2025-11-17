package org.example.chatft.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import org.json.JSONObject;

public class APIService {
    private static final String API_BASE = "http://localhost:5001";

    public String translate(String text, String srcLang, String tgtLang) throws Exception {
        String urlStr = API_BASE + "/translate?text=" + URLEncoder.encode(text, "UTF-8") +
                "&src=" + srcLang + "&tgt=" + tgtLang;

        String response = makeRequest(urlStr);
        JSONObject json = new JSONObject(response);
        return json.getString("translation");
    }

    public String summarize(String text) throws Exception {
        String urlStr = API_BASE + "/summarize?text=" + URLEncoder.encode(text, "UTF-8");

        String response = makeRequest(urlStr);
        JSONObject json = new JSONObject(response);
        return json.getString("summary");
    }

    public String[] translateAndSummarize(String text, String srcLang, String tgtLang) throws Exception {
        String urlStr = API_BASE + "/translate-summarize?text=" + URLEncoder.encode(text, "UTF-8") +
                "&src=" + srcLang + "&tgt=" + tgtLang;

        String response = makeRequest(urlStr);
        JSONObject json = new JSONObject(response);
        return new String[]{
                json.getString("translation"),
                json.getString("summary")
        };
    }

    private String makeRequest(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            response.append(line);
        }
        in.close();

        return response.toString();
    }
}