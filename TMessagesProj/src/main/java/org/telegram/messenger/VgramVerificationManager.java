package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * VGRAM's own verification service. This is independent of Telegram's official verification.
 * Public status is cached locally for offline display; mutations are always authorized by the server.
 */
public final class VgramVerificationManager {
    public static final String ROLE_DEVELOPER = "vgram_developer";
    public static final String ROLE_TEAM = "vgram_team";
    public static final String ROLE_CHANNEL = "verified_channel";
    private static final String API = "https://vgram-31xt.onrender.com";
    private static final String PREFS = "vgram_verification_cache";
    private static final String SESSION_PREFS = "vgram_session";
    private static final String SESSION_KEY = "token";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private VgramVerificationManager() {}

    public static final class Status {
        public final String subjectType, subjectId, roleCode, label;
        public final boolean verified;
        Status(String type, String id, String role, String label, boolean verified) {
            subjectType = type; subjectId = id; roleCode = role; this.label = label; this.verified = verified;
        }
    }

    public interface Callback { void onResult(Status status, Exception error); }
    public interface ActionCallback { void onResult(boolean success, String message); }

    public static String labelForRole(String role) {
        if (ROLE_DEVELOPER.equals(role)) return "Разработчик VGRAM";
        if (ROLE_TEAM.equals(role)) return "Один из разработчиков VGRAM";
        if (ROLE_CHANNEL.equals(role)) return "Канал верифицирован VGRAM";
        return "";
    }

    private static String cacheKey(String type, String id) { return type + ":" + id; }

    public static Status getCached(Context context, String type, String id) {
        String raw = prefs(context).getString(cacheKey(type, id), null);
        if (raw == null) return new Status(type, id, null, null, false);
        try {
            JSONObject json = new JSONObject(raw);
            String role = json.optString("role_code", null);
            if ("null".equals(role)) role = null;
            String label = json.optString("label", labelForRole(role));
            if ("null".equals(label)) label = labelForRole(role);
            return new Status(type, id, role, label, json.optBoolean("verified", false));
        } catch (Exception ignored) {
            return new Status(type, id, null, null, false);
        }
    }

    public static void refresh(Context context, String type, String id, Callback callback) {
        IO.execute(() -> {
            Status status = null;
            Exception failure = null;
            try {
                JSONObject json = request("GET", "/verification/" + type + "/" + id, null, false);
                status = fromJson(type, id, json);
                prefs(context).edit().putString(cacheKey(type, id), json.toString()).apply();
            } catch (Exception e) { failure = e; }
            Status result = status;
            Exception error = failure;
            MAIN.post(() -> callback.onResult(result != null ? result : getCached(context, type, id), error));
        });
    }

    public static void grant(Context context, String type, String id, String role, ActionCallback callback) {
        if (!("user".equals(type) || "channel".equals(type)) ||
                !(ROLE_DEVELOPER.equals(role) || ROLE_TEAM.equals(role) || ROLE_CHANNEL.equals(role)) ||
                ("channel".equals(type) != ROLE_CHANNEL.equals(role))) {
            callback.onResult(false, "Недопустимый тип субъекта или роль");
            return;
        }
        IO.execute(() -> {
            boolean success = false;
            String message;
            try {
                JSONObject body = new JSONObject();
                body.put("subject_type", type);
                body.put("subject_id", id);
                body.put("role_code", role);
                JSONObject response = request("PUT", "/owner/verification", body, true);
                prefs(context).edit().putString(cacheKey(type, id), response.toString()).apply();
                success = true;
                message = "Статус назначен: " + labelForRole(role);
            } catch (Exception e) { message = e.getMessage() == null ? "Ошибка сервера" : e.getMessage(); }
            boolean ok = success;
            String result = message;
            MAIN.post(() -> callback.onResult(ok, result));
        });
    }

    public static void revoke(Context context, String type, String id, ActionCallback callback) {
        IO.execute(() -> {
            boolean success = false;
            String message;
            try {
                JSONObject body = new JSONObject();
                body.put("subject_type", type);
                body.put("subject_id", id);
                request("DELETE", "/owner/verification", body, true);
                JSONObject cleared = new JSONObject();
                cleared.put("subject_type", type);
                cleared.put("subject_id", id);
                cleared.put("verified", false);
                prefs(context).edit().putString(cacheKey(type, id), cleared.toString()).apply();
                success = true;
                message = "Статус снят";
            } catch (Exception e) { message = e.getMessage() == null ? "Ошибка сервера" : e.getMessage(); }
            boolean ok = success;
            String result = message;
            MAIN.post(() -> callback.onResult(ok, result));
        });
    }

    private static Status fromJson(String type, String id, JSONObject json) {
        String role = json.optString("role_code", null);
        if ("null".equals(role)) role = null;
        String label = json.optString("label", labelForRole(role));
        if ("null".equals(label) || label.isEmpty()) label = labelForRole(role);
        return new Status(type, id, role, label, json.optBoolean("verified", false));
    }

    private static JSONObject request(String method, String path, JSONObject body, boolean authenticated) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(API + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/json");
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        if (authenticated) {
            // Token is a VGRAM account session, not a Telegram login token.
            SharedPreferences session = ApplicationLoader.applicationContext.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE);
            String token = session.getString(SESSION_KEY, "");
            if (token.isEmpty()) {
                connection.disconnect();
                throw new SecurityException("Нет активной сессии VGRAM. Войдите в аккаунт VGRAM.");
            }
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder text = new StringBuilder();
        if (stream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) text.append(line);
            }
        }
        connection.disconnect();
        JSONObject response;
        try { response = new JSONObject(text.toString()); } catch (Exception ignored) { response = new JSONObject(); }
        if (code < 200 || code >= 300) {
            String detail = response.optString("detail", response.optString("message", "HTTP " + code));
            throw new Exception("VGRAM API: " + detail + " (HTTP " + code + ")");
        }
        return response;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Compact blue check drawable for VGRAM-owned verification labels. */
    public static Drawable newCheckDrawable(Context context) {
        return new VgramCheckDrawable(context);
    }

    private static final class VgramCheckDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path check = new Path();
        VgramCheckDrawable(Context context) {
            float density = context.getResources().getDisplayMetrics().density;
            setBounds(0, 0, Math.round(16 * density), Math.round(16 * density));
            paint.setStyle(Paint.Style.FILL);
        }
        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            canvas.save();
            canvas.translate(b.left, b.top);
            float scale = Math.min(b.width(), b.height()) / 16f;
            canvas.scale(scale, scale);
            paint.setColor(Color.rgb(34, 151, 242));
            canvas.drawCircle(8, 8, 7.5f, paint);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.8f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            check.reset();
            check.moveTo(4.4f, 8.1f);
            check.lineTo(6.8f, 10.3f);
            check.lineTo(11.7f, 5.7f);
            canvas.drawPath(check, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
