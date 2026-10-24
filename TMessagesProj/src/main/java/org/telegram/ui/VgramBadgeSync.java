package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Method;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.WeakHashMap;

public final class VgramBadgeSync {
    private static final String API = "https://vgram-31xt.onrender.com";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Object, String> SUFFIXES = new WeakHashMap<>();
    private VgramBadgeSync() {}

    public static void apply(Context context, long telegramId, Object title) {
        if (context == null || title == null || telegramId <= 0) return;
        final String id = Long.toString(telegramId);
        final Context app = context.getApplicationContext();
        final SharedPreferences cache = app.getSharedPreferences("vgram_badge_cache", Context.MODE_PRIVATE);
        String cached = cache.getString("badges_" + id, "");
        long savedAt = cache.getLong("time_" + id, 0L);
        if (!cached.isEmpty()) render(title, cached);
        if (System.currentTimeMillis() - savedAt < 300000L) return;
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(API + "/users/telegram/" + id + "/badges").openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "VGRAM-Android");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) throw new IllegalStateException("HTTP " + status);
                String raw;
                try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[2048]; int count;
                    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                    raw = out.toString("UTF-8");
                }
                JSONObject response = new JSONObject(raw);
                JSONArray badges = response.optJSONArray("badges");
                StringBuilder suffix = new StringBuilder();
                if (badges != null) for (int i = 0; i < badges.length(); i++) {
                    JSONObject badge = badges.optJSONObject(i);
                    String label = "";
                    if (badge != null) {
                        label = badge.optString("label", "").trim();
                        if (label.isEmpty()) label = badge.optString("badge_code", "").trim();
                        if (label.isEmpty()) label = badge.optString("code", "").trim();
                        if (label.isEmpty()) label = badge.optString("name", "").trim();
                        if (label.isEmpty()) label = badge.optString("badge", "").trim();
                        if (label.isEmpty()) label = badge.optString("title", "").trim();
                    }
                    if (label != null && !label.trim().isEmpty()) {
                        if (suffix.length() > 0) suffix.append("  ");
                        suffix.append("◆ ").append(label.trim());
                    }
                }
                try {
                    HttpURLConnection profileConnection = (HttpURLConnection) new URL(API + "/users/telegram/" + id + "/profile").openConnection();
                    profileConnection.setRequestMethod("GET");
                    profileConnection.setConnectTimeout(15000);
                    profileConnection.setReadTimeout(20000);
                    profileConnection.setRequestProperty("Accept", "application/json");
                    profileConnection.setRequestProperty("User-Agent", "VGRAM-Android");
                    try {
                        if (profileConnection.getResponseCode() == 200) {
                            try (InputStream in = profileConnection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                                byte[] buffer = new byte[2048]; int count;
                                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                                JSONObject profile = new JSONObject(out.toString("UTF-8"));
                                if (profile.optBoolean("premium", false)) {
                                    if (suffix.length() > 0) suffix.append("  ");
                                    suffix.append("✦ VGRAM PREMIUM");
                                }
                            }
                        }
                    } finally {
                        profileConnection.disconnect();
                    }
                } catch (Exception ignoredProfileLookup) {
                    // A temporary profile endpoint failure must not hide already fetched badges.
                }
                String result = suffix.toString();
                cache.edit().putString("badges_" + id, result).putLong("time_" + id, System.currentTimeMillis()).apply();
                MAIN.post(() -> render(title, result));
            } catch (Exception ignored) {
                // Avoid hammering a sleeping/free-tier service; retry on the next profile visit after ~60 seconds.
                cache.edit().putLong("time_" + id, System.currentTimeMillis() - 240000L).apply();
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private static void render(Object title, String suffix) {
        if (title == null) return;
        try {
            Method getText = title.getClass().getMethod("getText");
            Method setText = title.getClass().getMethod("setText", CharSequence.class);
            Object value = getText.invoke(title);
            String current = value == null ? "" : value.toString();
            String oldSuffix = SUFFIXES.get(title);
            String base = oldSuffix != null && !oldSuffix.isEmpty() && current.endsWith(oldSuffix)
                ? current.substring(0, current.length() - oldSuffix.length()).trim() : current;
            String append = suffix == null || suffix.isEmpty() ? "" : "  " + suffix;
            SUFFIXES.put(title, append);
            setText.invoke(title, base + append);
        } catch (Exception ignored) {
        }
    }
}
