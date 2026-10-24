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
        key.requestFocus();
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
                    reason = "Ключ администратора неверный или сервер не принял его (HTTP 401).";
                } else if (e.getMessage() != null && e.getMessage().contains("HTTP 403")) {
                    reason = "Сервер запретил операцию (HTTP 403). Проверь права ключа.";
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
        addButton(root, "Обновить список", this::refresh);
        list = column();
        root.addView(list);
        setContentView(root);
        refresh();
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
            if (status == 401) throw new Exception("HTTP 401: ключ администратора отклонён сервером.");
            if (status == 403) throw new Exception("HTTP 403: у ключа нет прав администратора.");
            if (status >= 300 && status < 400) throw new Exception("HTTP " + status + ": сервер перенаправил запрос; проверь HTTPS и адрес API.");
            if (status < 200 || status >= 300) throw new Exception("HTTP " + status + (bytes.size() > 0 ? ": " + bytes.toString("UTF-8").trim() : ""));
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
