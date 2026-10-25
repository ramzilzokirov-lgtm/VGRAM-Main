package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** VGRAM-only Premium state. Telegram's official premium flag remains independent. */
public final class VgramPremiumManager {
    private static final String API = "https://vgram-31xt.onrender.com";
    private static final String PREFS = "vgram_premium_cache";
    private static final long TTL_MS = 5 * 60 * 1000L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VGRAM-PremiumSync");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private VgramPremiumManager() {}

    /** Fast synchronous check used by UserConfig; stale values refresh asynchronously. */
    public static boolean isPremium(Context context, long telegramId, boolean officialPremium) {
        if (officialPremium) return true;
        if (context == null || telegramId <= 0) return false;
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = Long.toString(telegramId);
        boolean cached = prefs.getBoolean("premium_" + id, false);
        long refreshed = prefs.getLong("time_" + id, 0L);
        if (System.currentTimeMillis() - refreshed >= TTL_MS) refresh(context, telegramId);
        return cached;
    }

    public static void refresh(Context context, long telegramId) {
        if (context == null || telegramId <= 0) return;
        Context app = context.getApplicationContext();
        String id = Long.toString(telegramId);
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String inFlightKey = "inflight_" + id;
        synchronized (VgramPremiumManager.class) {
            if (IN_FLIGHT.containsKey(id)) return;
            IN_FLIGHT.put(id, Boolean.TRUE);
        }
        IO.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(API + "/users/telegram/" + id + "/profile").openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "VGRAM-Android");
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder raw = new StringBuilder();
                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) raw.append(line);
                    }
                }
                if (code < 200 || code >= 300) throw new IllegalStateException("VGRAM Premium API HTTP " + code);
                JSONObject json = new JSONObject(raw.toString());
                boolean premium = json.optBoolean("premium", false);
                prefs.edit().putBoolean("premium_" + id, premium)
                        .putLong("time_" + id, System.currentTimeMillis()).apply();
                // VGRAM Premium is a local client entitlement; refresh UI-facing config observers.
                MAIN.post(() -> NotificationCenter.getGlobalInstance().postNotificationName(
                        NotificationCenter.vgramPremiumStatusChanged, telegramId, premium));
            } catch (Exception ignored) {
                // Keep the last known value; transient network failure must not strip Premium.
                prefs.edit().putLong("time_" + id, System.currentTimeMillis() - (TTL_MS - 30000L)).apply();
            } finally {
                synchronized (VgramPremiumManager.class) { IN_FLIGHT.remove(id); }
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static final java.util.HashSet<String> IN_FLIGHT = new java.util.HashSet<>();
}
