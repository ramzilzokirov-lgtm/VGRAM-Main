package org.telegram.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;

public class VgramBadgesActivity extends VgramBaseActivity {
    private static final String PREFS = "vgram_badges_server";
    private static final String API = "https://vgram-31xt.onrender.com";
    private static final String[] LABELS = {"TESTER", "OG/OLD", "FRIENDS", "TEAM", "DEVELOPER", "FOUNDER"};
    private static final String[] CODES = {"TESTER", "OG", "FRIENDS", "TEAM", "DEVELOPER", "FOUNDER"};
    private LinearLayout list;
    private android.content.SharedPreferences prefs;
    private String adminKey = "";
    private int failedAttempts = 0;
    private static final int PICK_SERVER_BANNER = 742;
    private String pendingBannerTelegramId = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setTitle("Бейджи VGRAM — синхронизация");
        showKeyGate();
    }

    private void showKeyGate() {
        LinearLayout root = column();
        root.addView(text("Серверные бейджи. Ключ администратора проверяется сервером и не сохраняется в настройках приложения."));
        EditText key = new EditText(this);
        key.setSingleLine(true);
        key.setHint("Введи ключ администратора");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        styleInput(key);
        key.setEnabled(true);
        key.setFocusable(true);
        key.setFocusableInTouchMode(true);
        key.setCursorVisible(true);
        key.setTextIsSelectable(false);
        key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        root.addView(key);
        addButton(root, "Подключиться", v -> connectWithKey(key));
        key.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        key.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER &&
                 event.getAction() == android.view.KeyEvent.ACTION_UP)) {
                connectWithKey(key);
                return true;
            }
            return false;
        });
        addButton(root, "Назад", v -> finish());
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff17100c);
        scroll.addView(root);
        setContentView(scroll);
        key.post(() -> {
            key.requestFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(key, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void connectWithKey(EditText key) {
        String candidate = key.getText().toString().trim();
        if (candidate.isEmpty()) { key.setError("Введи ключ администратора"); key.requestFocus(); return; }
        adminKey = candidate;
        key.setText("");
        key.clearFocus();
        verifyKey();
    }

    private void verifyKey() {
        android.app.ProgressDialog progress = new android.app.ProgressDialog(this);
        progress.setMessage("Проверяю ключ на сервере…");
        progress.setCancelable(false);
        progress.show();
        new Thread(() -> {
            try {
                try {
                    request("GET", "/admin/nfts", null, adminKey);
                } catch (java.net.SocketTimeoutException firstTimeout) {
                    // Render free instances can need time to wake up. Retry once with a fresh connection.
                    request("GET", "/admin/nfts", null, adminKey);
                }
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    progress.dismiss();
                    android.widget.Toast.makeText(this, "Ключ принят", android.widget.Toast.LENGTH_SHORT).show();
                    openAdminPanel();
                });
            } catch (Exception e) {
                String reason;
                if (e instanceof java.net.SocketTimeoutException) {
                    reason = "Истекло время ожидания ответа сервера. Бесплатный Render может запускаться до минуты; повтори запрос.";
                } else if (e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException) {
                    reason = "Не удалось установить соединение. Проверь сеть и доступность сервера VGRAM.";
                } else if (e.getMessage() != null && e.getMessage().contains("HTTP 401")) {
                    reason = e.getMessage() + "\nПроверь введённый ключ и переменную VGRAM_ADMIN_KEY на сервере. Ключ не хранится в APK.";
                } else if (e.getMessage() != null && e.getMessage().contains("HTTP 403")) {
                    reason = e.getMessage();
                } else if (e.getMessage() != null && e.getMessage().contains("HTTP 503")) {
                    reason = e.getMessage() + "\nПроверь, что VGRAM_ADMIN_KEY настроен в окружении сервера.";
                } else {
                    reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                }
                final String displayReason = reason;
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    progress.dismiss();
                    adminKey = "";
                    new AlertDialog.Builder(this).setTitle("Не удалось подключиться")
                        .setMessage(displayReason)
                        .setPositiveButton("Повторить", (d, w) -> showKeyGate())
                        .setNegativeButton("Закрыть", (d, w) -> finish()).show();
                });
            }
        }).start();
    }

    private void openAdminPanel() {
        setTitle("Бейджи VGRAM");
        LinearLayout root = column();
        root.addView(text("Выдача и отзыв выполняются на сервере. Введи числовой Telegram ID: username нельзя надёжно сопоставить с ID через Telegram API. Бейджи доступны через API профиля VGRAM."));
        addButton(root, "Выдать серверный бейдж", this::grantDialog);
        addButton(root, "Отозвать серверный бейдж", this::revokeDialog);
        addButton(root, "Включить VGRAM Premium", () -> setPremiumDialog(true));
        addButton(root, "Отключить VGRAM Premium", () -> setPremiumDialog(false));
        addButton(root, "Загрузить серверный баннер профиля", this::bannerDialog);
        addButton(root, "Обновить список", this::refresh);
        list = column();
        root.addView(list);
        setContentView(root);
        refresh();
    }

    private void setPremiumDialog(boolean enabled) {
        EditText id = idInput();
        new AlertDialog.Builder(this).setTitle("Telegram ID для VGRAM Premium")
            .setMessage(enabled ? "Включить отдельный статус VGRAM Premium? Это не меняет Telegram Premium." :
                "Отключить статус VGRAM Premium?")
            .setView(id).setNegativeButton("Отмена", null)
            .setPositiveButton(enabled ? "Включить" : "Отключить", (d, w) -> {
                String telegramId = normalizeId(id.getText().toString());
                if (telegramId.isEmpty()) { id.setError("Нужен числовой Telegram ID"); return; }
                new Thread(() -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put("enabled", enabled);
                        JSONObject result = request("PUT", "/admin/telegram/" + telegramId + "/premium", body, adminKey);
                        if (!result.optBoolean("ok", false)) throw new Exception("Сервер не подтвердил изменение");
                        runOnUiThread(() -> new AlertDialog.Builder(this)
                            .setMessage(enabled ? "VGRAM Premium включён." : "VGRAM Premium отключён.")
                            .setPositiveButton("OK", null).show());
                    } catch (Exception error) {
                        String message = error.getMessage() == null ? "Не удалось изменить VGRAM Premium." : error.getMessage();
                        runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Ошибка сервера")
                            .setMessage(message).setPositiveButton("OK", null).show());
                    }
                }).start();
            }).show();
    }

    private void bannerDialog() {
        EditText id = idInput();
        new AlertDialog.Builder(this).setTitle("Telegram ID для баннера")
            .setMessage("Баннер будет доступен на профиле пользователя через сервер VGRAM.")
            .setView(id).setNegativeButton("Отмена", null)
            .setPositiveButton("Выбрать изображение", (d, w) -> {
                String telegramId = normalizeId(id.getText().toString());
                if (telegramId.isEmpty()) { id.setError("Нужен числовой Telegram ID"); return; }
                pendingBannerTelegramId = telegramId;
                android.content.Intent pick = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                pick.setType("image/*");
                pick.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try { startActivityForResult(pick, PICK_SERVER_BANNER); }
                catch (Exception error) {
                    android.widget.Toast.makeText(this, "Не удалось открыть выбор изображения", android.widget.Toast.LENGTH_LONG).show();
                }
            }).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_SERVER_BANNER || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final android.net.Uri uri = data.getData();
        final String telegramId = pendingBannerTelegramId;
        pendingBannerTelegramId = "";
        new Thread(() -> {
            try {
                String mime = getContentResolver().getType(uri);
                if (mime == null || !(mime.equals("image/png") || mime.equals("image/jpeg") || mime.equals("image/webp"))) {
                    throw new Exception("Поддерживаются PNG, JPEG и WebP.");
                }
                byte[] raw;
                try (InputStream in = getContentResolver().openInputStream(uri);
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    if (in == null) throw new Exception("Не удалось прочитать изображение.");
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        if (out.size() + count > 2 * 1024 * 1024) throw new Exception("Размер баннера не должен превышать 2 MiB.");
                        out.write(buffer, 0, count);
                    }
                    raw = out.toByteArray();
                }
                if (raw.length == 0) throw new Exception("Выбран пустой файл.");
                JSONObject body = new JSONObject();
                body.put("content_type", mime);
                body.put("data_base64", android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP));
                JSONObject result = request("PUT", "/admin/telegram/" + telegramId + "/banner", body, adminKey);
                if (!result.optBoolean("ok", false)) throw new Exception("Сервер не подтвердил загрузку баннера.");
                runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle("Баннер загружен")
                    .setMessage("Баннер сохранён на сервере VGRAM. Он появится в профиле после обновления данных.")
                    .setPositiveButton("OK", null).show());
            } catch (Exception error) {
                String message = error.getMessage() == null ? "Не удалось загрузить баннер." : error.getMessage();
                runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Ошибка загрузки")
                    .setMessage(message).setPositiveButton("OK", null).show());
            }
        }).start();
    }

    private void grantDialog() {
        EditText id = idInput();
        new AlertDialog.Builder(this).setTitle("Telegram ID пользователя")
            .setView(id).setNegativeButton("Отмена", null)
            .setPositiveButton("Далее", (d, w) -> {
                String telegramId = normalizeId(id.getText().toString());
                if (telegramId.isEmpty()) { id.setError("Нужен числовой Telegram ID"); return; }
                new AlertDialog.Builder(this).setTitle("Выбери бейдж")
                    .setItems(LABELS, (dialog, which) -> {
                        EditText label = new EditText(this);
                        label.setSingleLine(true);
                        styleInput(label);
                        label.setText(LABELS[which]);
                        new AlertDialog.Builder(this).setTitle("Подпись бейджа")
                            .setView(label).setNegativeButton("Отмена", null)
                            .setPositiveButton("Синхронизировать", (d2, w2) ->
                                serverBadge("PUT", telegramId, CODES[which], label.getText().toString().trim()))
                            .show();
                    }).show();
            }).show();
    }

    private void revokeDialog() {
        EditText id = idInput();
        new AlertDialog.Builder(this).setTitle("Telegram ID пользователя")
            .setView(id).setNegativeButton("Отмена", null)
            .setPositiveButton("Далее", (d, w) -> {
                String telegramId = normalizeId(id.getText().toString());
                if (telegramId.isEmpty()) { id.setError("Нужен числовой Telegram ID"); return; }
                new AlertDialog.Builder(this).setTitle("Какой бейдж отозвать?")
                    .setItems(LABELS, (dialog, which) -> serverBadge("DELETE", telegramId, CODES[which], ""))
                    .show();
            }).show();
    }

    private void serverBadge(String method, String telegramId, String code, String label) {
        new Thread(() -> {
            try {
                JSONObject body = null;
                if ("PUT".equals(method)) {
                    body = new JSONObject();
                    body.put("label", label.isEmpty() ? code : label);
                }
                JSONObject result = request(method, "/admin/telegram/" + telegramId + "/badges/" + code, body, adminKey);
                if (!result.optBoolean("ok", false)) throw new Exception("Server did not confirm the change");
                JSONArray tracked = new JSONArray(prefs.getString("tracked_ids", "[]"));
                boolean exists = false;
                for (int i = 0; i < tracked.length(); i++) if (telegramId.equals(tracked.optString(i))) exists = true;
                if (!exists) tracked.put(telegramId);
                prefs.edit().putString("tracked_ids", tracked.toString()).apply();
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(this, "Бейдж синхронизирован с сервером", android.widget.Toast.LENGTH_LONG).show();
                    refresh();
                });
            } catch (Exception e) {
                runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Ошибка синхронизации")
                    .setMessage("Сервер не принял изменение. Проверь доступ к серверу, ключ и Telegram ID.")
                    .setPositiveButton("OK", null).show());
            }
        }).start();
    }

    private void refresh() {
        if (list == null) return;
        list.removeAllViews();
        list.addView(text("Загружаю серверные бейджи…"));
        new Thread(() -> {
            ArrayList<String> rows = new ArrayList<>();
            try {
                JSONArray tracked = new JSONArray(prefs.getString("tracked_ids", "[]"));
                for (int i = 0; i < tracked.length(); i++) {
                    String id = tracked.optString(i);
                    JSONObject data = request("GET", "/users/telegram/" + id + "/badges", null, null);
                    JSONArray badges = data.optJSONArray("badges");
                    StringBuilder row = new StringBuilder("ID ").append(id).append("  ·  ");
                    if (badges == null || badges.length() == 0) row.append("нет бейджей");
                    else for (int b = 0; b < badges.length(); b++) {
                        if (b > 0) row.append("  ");
                        JSONObject badge = badges.optJSONObject(b);
                        row.append("◆ ").append(badge == null ? "" : badge.optString("label"));
                    }
                    rows.add(row.toString());
                }
            } catch (Exception e) {
                rows.add("Сервер недоступен. Нажми «Обновить список» позже.");
            }
            runOnUiThread(() -> {
                if (list == null) return;
                list.removeAllViews();
                if (rows.isEmpty()) list.addView(text("Пока нет отслеживаемых назначений. Выдай первый бейдж."));
                else for (String row : rows) list.addView(text(row));
            });
        }).start();
    }

    private JSONObject request(String method, String path, JSONObject body, String key) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "VGRAM-Android");
        if (key != null && !key.isEmpty()) c.setRequestProperty("X-VGRAM-ADMIN-KEY", key);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream out = c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        }
        try {
            int status = c.getResponseCode();
            InputStream in = status >= 200 && status < 300 ? c.getInputStream() : c.getErrorStream();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (in != null) try (InputStream stream = in) {
                byte[] buffer = new byte[2048]; int n;
                while ((n = stream.read(buffer)) != -1) bytes.write(buffer, 0, n);
            }
            if (status == 401 || status == 403 || status == 503) {
                String detail = safeServerError(bytes.toByteArray(), key);
                String fallback = status == 401 ? "сервер отклонил ключ администратора."
                        : status == 403 ? "сервер запретил операцию."
                        : "сервер не готов обработать запрос.";
                throw new Exception("HTTP " + status + ": " + (detail.isEmpty() ? fallback : detail));
            }
            if (status >= 300 && status < 400) throw new Exception("HTTP " + status + ": сервер перенаправил запрос; проверь HTTPS и адрес API.");
            if (status < 200 || status >= 300) throw new Exception("HTTP " + status + (bytes.size() > 0 ? ": " + safeServerError(bytes.toByteArray(), key) : ""));
            String raw = bytes.toString("UTF-8").trim();
            if (raw.isEmpty()) return new JSONObject();
            if (raw.startsWith("{")) return new JSONObject(raw);
            if (raw.startsWith("[")) {
                JSONObject wrapped = new JSONObject();
                wrapped.put("data", new JSONArray(raw));
                return wrapped;
            }
            JSONObject wrapped = new JSONObject();
            wrapped.put("message", raw.length() > 300 ? raw.substring(0, 300) : raw);
            return wrapped;
        } finally { c.disconnect(); }
    }

    private String safeServerError(byte[] payload, String candidateKey) {
        if (payload == null || payload.length == 0) return "";
        String raw = new String(payload, StandardCharsets.UTF_8).trim();
        String message = "";
        if (raw.startsWith("{")) {
            try {
                JSONObject response = new JSONObject(raw);
                String[] fields = {"detail", "message", "error", "reason"};
                for (String field : fields) {
                    Object value = response.opt(field);
                    if (value instanceof String && !((String) value).trim().isEmpty()) {
                        message = ((String) value).trim();
                        break;
                    }
                }
            } catch (Exception ignored) {
                // Do not display malformed server payloads verbatim.
            }
        } else if (!raw.startsWith("<") && !raw.toLowerCase(Locale.US).contains("<html")
                && !raw.toLowerCase(Locale.US).contains("<!doctype")) {
            message = raw;
        }
        if (candidateKey != null && !candidateKey.isEmpty()) {
            message = message.replace(candidateKey, "[скрыто]");
        }
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < message.length() && clean.length() < 180; i++) {
            char ch = message.charAt(i);
            if (Character.isISOControl(ch)) {
                if (ch == '\n' || ch == '\t' || ch == '\r') clean.append(' ');
            } else {
                clean.append(ch);
            }
        }
        return clean.toString().trim();
    }

    private EditText idInput() {
        EditText id = new EditText(this);
        id.setSingleLine(true);
        id.setInputType(InputType.TYPE_CLASS_NUMBER);
        id.setHint("Например, 123456789");
        styleInput(id);
        return id;
    }
    private void styleInput(EditText input) {
        input.setTextColor(0xfff5eee7);
        input.setHintTextColor(0xffb7a99c);
        input.setLinkTextColor(0xffc5b878);
        input.setTextSize(16);
        input.setPadding(18, 16, 18, 16);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xff30251d);
        bg.setCornerRadius(18);
        bg.setStroke(2, 0xffc5b878);
        input.setBackground(bg);
        input.setSelectAllOnFocus(false);
    }
    private String normalizeId(String raw) {
        String value = raw == null ? "" : raw.trim();
        return value.matches("[0-9]{3,20}") ? value : "";
    }
    private LinearLayout column() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(20, 18, 20, 24);
        return v;
    }
    private TextView text(String value) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(0xffeeeeea);
        t.setTextSize(15);
        t.setPadding(12, 12, 12, 12);
        return t;
    }
    private void addButton(LinearLayout parent, String label, View.OnClickListener click) {
        android.widget.Button b = new android.widget.Button(this);
        b.setText(label);
        b.setTextColor(0xff171713);
        b.setAllCaps(false);
        b.setEnabled(true);
        b.setClickable(true);
        b.setFocusable(true);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffc5b878));
        }
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (12 * getResources().getDisplayMetrics().density);
        parent.addView(b, lp);
    }
}
