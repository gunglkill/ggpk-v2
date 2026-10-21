package ru.ggpk.schedule;

import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Простая сеть для публикации: JSON, байты, UA. */
final class Http {

    static final String UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/124.0 Mobile Safari/537.36 GGPKApp/1.2";

    private Http() {
    }

    static String text(String url) throws Exception {
        return new String(bytes(url), StandardCharsets.UTF_8);
    }

    static byte[] bytes(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", UA);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    /** Метод с телом (POST/PUT) — ответ как строка. */
    static String body(String url, String method, String json, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        if (json != null) {
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(data.length);
            c.getOutputStream().write(data);
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = "";
        if (in != null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            text = out.toString("UTF-8");
        }
        c.disconnect();
        if (code >= 400) {
            String mes = text;
            if (mes.isEmpty()) mes = "HTTP " + code;
            try {
                JSONObject j = new JSONObject(text);
                mes = j.optString("message", mes);
            } catch (Exception ignored) {
            }
            throw new IllegalStateException(method + " " + code + ": " + mes);
        }
        return text;
    }
}
