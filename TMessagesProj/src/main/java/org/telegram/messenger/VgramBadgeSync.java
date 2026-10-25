package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;
import android.view.View;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** VGRAM badge cache and styled name renderer. All network work runs off the UI thread. */
public final class VgramBadgeSync {
    private static final String API = "https://vgram-31xt.onrender.com";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ConcurrentHashMap<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final ThreadPoolExecutor REQUESTS = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(256),
            runnable -> { Thread thread = new Thread(runnable, "VGRAM-BadgeSync"); thread.setDaemon(true); return thread; },
            new ThreadPoolExecutor.AbortPolicy());
    private static final String PREFS = "vgram_badge_cache";
    private static final long CACHE_TTL = 300000L;

    private VgramBadgeSync() {}

    /** Returns a styled name using only the local cache; never performs network I/O. */
    public static SpannableStringBuilder withBadges(Context context, long telegramId, CharSequence name) {
        SpannableStringBuilder result = new SpannableStringBuilder(name == null ? "" : name);
        if (context == null || telegramId <= 0) return result;
        SharedPreferences cache = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String cached = cache.getString("badges_" + telegramId, "");
        for (String label : decodeLabels(cached)) {
            if (result.length() > 0) result.append("  ");
            int start = result.length();
            result.append(label);
            result.setSpan(new BadgePillSpan(label), start, result.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return result;
    }

    /** Applies cached badges immediately and refreshes them asynchronously. */
    public static void apply(Context context, long telegramId, SimpleTextView title) {
        if (context == null || title == null || telegramId <= 0) return;
        CharSequence base = title.getText();
        title.setText(withBadges(context, telegramId, base));
        refresh(context, telegramId, title, base);
    }

    private static void refresh(Context context, long telegramId, SimpleTextView title, CharSequence baseName) {
        refreshInternal(context, telegramId, title, baseName, null);
    }
    public static void requestRefresh(Context context, long telegramId, Runnable onUpdated) {
        if (context != null && telegramId > 0) refreshInternal(context, telegramId, null, null, onUpdated);
    }
    public static boolean isVerified(Context context, long telegramId) {
        if (context == null || telegramId <= 0) return false;
        SharedPreferences cache = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (String label : decodeLabels(cache.getString("badges_" + telegramId, ""))) {
            String value = label.toUpperCase(java.util.Locale.ROOT);
            if (value.startsWith("✓ ") || value.contains("VGRAM VERIFIED") || value.contains("РАЗРАБОТЧИК VGRAM") || value.contains("КАНАЛ ВЕРИФИЦИРОВАН VGRAM")) return true;
        }
        return false;
    }
    public static String getVerificationLabel(Context context, long telegramId) {
        if (context == null || telegramId <= 0) return "";
        SharedPreferences cache = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (String label : decodeLabels(cache.getString("badges_" + telegramId, ""))) {
            String value = label.toUpperCase(java.util.Locale.ROOT);
            if (value.startsWith("✓ ")) return label.substring(2).trim();
            if (value.contains("VGRAM VERIFIED")) return "VGRAM VERIFIED";
            if (value.contains("РАЗРАБОТЧИК VGRAM") || value.contains("КАНАЛ ВЕРИФИЦИРОВАН VGRAM")) return label;
        }
        return "";
    }
    private static void refreshInternal(Context context, long telegramId, SimpleTextView title, CharSequence baseName, Runnable onUpdated) {
        final Context app = context.getApplicationContext();
        final String id = Long.toString(telegramId);
        SharedPreferences cache = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long savedAt = cache.getLong("time_" + id, 0L);
        if (savedAt > 0 && System.currentTimeMillis() - savedAt < CACHE_TTL) return;
        if (IN_FLIGHT.putIfAbsent(id, Boolean.TRUE) != null) return;
        try {
            REQUESTS.execute(() -> {
                HttpURLConnection connection = null;
                boolean updated = false;
                try {
                    connection = (HttpURLConnection) new URL(API + "/users/telegram/" + id + "/profile").openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(12000);
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
                    JSONArray labels = new JSONArray();
                    String verificationLabel = fetchVerificationLabel(id);
                    if (!verificationLabel.isEmpty()) labels.put("✓ " + verificationLabel);
                    else {
                        boolean verified = response.optBoolean("verified", false) || response.optBoolean("is_verified", false)
                                || response.optBoolean("verified_channel", false) || response.optBoolean("vgram_verified", false);
                        String role = response.optString("role", "").toLowerCase(java.util.Locale.ROOT);
                        if (verified || role.contains("verified") || role.contains("developer") || role.contains("team")) labels.put("✓ VGRAM VERIFIED");
                    }
                    if (response.optBoolean("premium", false)) labels.put("✦ VGRAM PREMIUM");
                    if (badges != null) for (int i=0;i<badges.length();i++) {
                        JSONObject item=badges.optJSONObject(i); if(item==null) continue;
                        String label=firstNonEmpty(item,"label","badge_code","code","name","badge","title");
                        if(!label.isEmpty() && !"VGRAM PREMIUM".equalsIgnoreCase(label)) labels.put(label);
                    }
                    cache.edit().putString("badges_" + id, labels.toString()).putLong("time_" + id, System.currentTimeMillis()).apply();
                    updated = true;
                } catch (Exception error) {
                    cache.edit().putLong("time_" + id, System.currentTimeMillis() - CACHE_TTL + 60000L).apply();
                } finally {
                    IN_FLIGHT.remove(id);
                    if(connection!=null) connection.disconnect();
                }
                if(updated) MAIN.post(() -> {
                    if(title!=null && title.getParent()!=null) title.setText(withBadges(app,telegramId,baseName));
                    if(onUpdated!=null) onUpdated.run();
                });
            });
        } catch (RejectedExecutionException full) { IN_FLIGHT.remove(id); }
    }

    private static String fetchVerificationLabel(String telegramId) {
        HttpURLConnection verificationConnection = null;
        try {
            verificationConnection = (HttpURLConnection) new URL(API + "/verification/user/" + telegramId).openConnection();
            verificationConnection.setRequestMethod("GET");
            verificationConnection.setConnectTimeout(8000);
            verificationConnection.setReadTimeout(8000);
            verificationConnection.setRequestProperty("Accept", "application/json");
            verificationConnection.setRequestProperty("User-Agent", "VGRAM-Android");
            int code = verificationConnection.getResponseCode();
            if (code < 200 || code >= 300) return "";
            String raw;
            try (InputStream in = verificationConnection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                raw = out.toString("UTF-8");
            }
            JSONObject status = new JSONObject(raw);
            if (!status.optBoolean("verified", false)) return "";
            String label = status.optString("label", "").trim();
            if (!label.isEmpty() && !"null".equalsIgnoreCase(label)) return label;
            String role = status.optString("role_code", "").trim();
            if ("vgram_developer".equals(role)) return "Разработчик VGRAM";
            if ("vgram_team".equals(role)) return "Один из разработчиков VGRAM";
            if ("verified_channel".equals(role)) return "Канал верифицирован VGRAM";
        } catch (Exception ignored) {
        } finally {
            if (verificationConnection != null) verificationConnection.disconnect();
        }
        return "";
    }

    private static String firstNonEmpty(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "").trim();
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
        }
        return "";
    }

    private static List<String> decodeLabels(String stored) {
        ArrayList<String> labels = new ArrayList<>();
        if (stored == null || stored.isEmpty()) return labels;
        try {
            JSONArray array = new JSONArray(stored);
            for (int i = 0; i < array.length(); i++) {
                String label = array.optString(i, "").trim();
                if (!label.isEmpty()) labels.add(label);
            }
        } catch (Exception legacyFormat) {
            // Accept old cache entries like "◆ TESTER  ◆ OG" once, without requiring a cache reset.
            String[] parts = stored.split("\\s*◆\\s*");
            for (String part : parts) {
                String label = part.trim();
                if (label.startsWith("✦ VGRAM PREMIUM")) label = "VGRAM PREMIUM";
                if (!label.isEmpty()) labels.add(label);
            }
        }
        return labels;
    }

    private static final class BadgePillSpan extends ReplacementSpan {
        private final String label;
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textDrawPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        BadgePillSpan(String label) {
            this.label = label;
            textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            textPaint.setTextSize(android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_SP, 10,
                    android.content.res.Resources.getSystem().getDisplayMetrics()));
            textDrawPaint.setColor(0xFF35352E);
            textDrawPaint.setTypeface(textPaint.getTypeface());
            textDrawPaint.setTextSize(textPaint.getTextSize());
            backgroundPaint.setColor(label.toUpperCase(java.util.Locale.ROOT).contains("VERIFIED") ? 0xFF2F80ED : 0xFFD4C78F);
            if (label.toUpperCase(java.util.Locale.ROOT).contains("VERIFIED")) textDrawPaint.setColor(0xFFFFFFFF);
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (fm != null) {
                Paint.FontMetricsInt metrics = paint.getFontMetricsInt();
                fm.ascent = metrics.ascent; fm.descent = metrics.descent;
                fm.top = metrics.top; fm.bottom = metrics.bottom;
            }
            return (int) Math.ceil(textPaint.measureText(label)) + dp(12);
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            float textWidth = textPaint.measureText(label);
            float width = textWidth + dp(12);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float height = Math.max(dp(16), fm.descent - fm.ascent + dp(4));
            float centerY = (top + bottom) / 2f;
            RectF rect = new RectF(x, centerY - height / 2f, x + width, centerY + height / 2f);
            canvas.drawRoundRect(rect, dp(6), dp(6), backgroundPaint);
            canvas.drawText(label, x + dp(6), centerY - (fm.ascent + fm.descent) / 2f, textDrawPaint);
        }

        private static int dp(float value) {
            return (int) (value * android.content.res.Resources.getSystem().getDisplayMetrics().density + 0.5f);
        }
    }
}
