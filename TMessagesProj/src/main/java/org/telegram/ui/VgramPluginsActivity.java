package org.telegram.ui;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class VgramPluginsActivity extends VgramBaseActivity {
    private static final int PICK_PLUGIN = 512;
    private static final int API_VERSION = 1;
    private android.content.SharedPreferences prefs;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("vgram_plugins", MODE_PRIVATE);
        build();
    }

    private void build() {
        setupScreen("EXTENSION LAB", "Плагины VGRAM", "Безопасные декларативные расширения. Плагины описывают данные и настройки — загруженный код не исполняется.");
        card("Безопасность прежде всего", "Поддерживаются только JSON-манифесты Plugin API v1. Не импортируй манифесты из неизвестных источников. Плагин не получает доступ к сообщениям, контактам или аккаунту Telegram.");

        LinearLayout importCard = card("Добавить плагин", "Выбери JSON-файл с манифестом. Совместимость и поля проверяются до сохранения.");
        addButton(importCard, "Импортировать JSON-манифест", () -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            try { startActivityForResult(intent, PICK_PLUGIN); }
            catch (Exception error) { Toast.makeText(this, "Не удалось открыть выбор файла", Toast.LENGTH_LONG).show(); }
        });

        ArrayList<String> ids = new ArrayList<>(prefs.getStringSet("installed", Collections.<String>emptySet()));
        Collections.sort(ids);
        if (ids.isEmpty()) {
            card("Пока нет установленных плагинов", "Импортируй совместимый JSON-манифест. Для начала можно использовать пример из PLUGIN_API.md.");
        }
        for (String id : ids) {
            try {
                JSONObject manifest = new JSONObject(prefs.getString("manifest." + id, "{}"));
                String name = manifest.optString("name", id);
                String version = manifest.optString("version", "?");
                String description = manifest.optString("description", "Без описания");
                boolean enabled = prefs.getBoolean("enabled." + id, false);
                LinearLayout item = card(name + "  •  " + version, description + "\nID: " + id + "\nAPI: " + manifest.optInt("apiVersion", 0));
                addButton(item, enabled ? "Отключить плагин" : "Включить плагин", () -> {
                    boolean next = !prefs.getBoolean("enabled." + id, false);
                    prefs.edit().putBoolean("enabled." + id, next).apply();
                    reapplyEnabledPlugins();
                    buildAgain();
                });
                addButton(item, "Удалить плагин", () -> {
                    Set<String> current = new HashSet<>(prefs.getStringSet("installed", Collections.<String>emptySet()));
                    current.remove(id);
                    prefs.edit().putStringSet("installed", current).remove("manifest." + id).remove("enabled." + id).apply();
                    reapplyEnabledPlugins();
                    buildAgain();
                });
            } catch (Exception ignored) {
                card("Повреждённый плагин", "Манифест " + id + " не читается. Удали его и импортируй заново.");
            }
        }
    }

    private void reapplyEnabledPlugins() {
        android.content.SharedPreferences ui = getSharedPreferences("vgram_ui", MODE_PRIVATE);
        ui.edit().putInt("accent", 0xffc5b878).putBoolean("animations", true).apply();
        Set<String> ids = new HashSet<>(prefs.getStringSet("installed", Collections.<String>emptySet()));
        for (String id : ids) {
            if (!prefs.getBoolean("enabled." + id, false)) continue;
            try {
                JSONObject manifest = new JSONObject(prefs.getString("manifest." + id, "{}"));
                applyManifest(manifest);
            } catch (Exception ignored) { }
        }
        VgramTheme.apply(this);
    }

    private void buildAgain() {
        recreate();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_PLUGIN || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Uri uri = data.getData();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IllegalArgumentException("Не удалось открыть файл");
                byte[] buffer = new byte[4096];
                int count;
                int total = 0;
                while ((count = in.read(buffer)) != -1) {
                    total += count;
                    if (total > 65536) throw new IllegalArgumentException("Манифест больше 64 КБ");
                    bytes.write(buffer, 0, count);
                }
            }
            JSONObject manifest = new JSONObject(bytes.toString("UTF-8"));
            validate(manifest);
            String id = manifest.getString("id");
            Set<String> ids = new HashSet<>(prefs.getStringSet("installed", Collections.<String>emptySet()));
            ids.add(id);
            prefs.edit().putStringSet("installed", ids).putString("manifest." + id, manifest.toString()).putBoolean("enabled." + id, false).apply();
            Toast.makeText(this, "Плагин добавлен. Включи его в списке.", Toast.LENGTH_LONG).show();
            buildAgain();
        } catch (Exception error) {
            String message = error.getMessage() == null ? "Манифест плагина некорректен" : error.getMessage();
            Toast.makeText(this, "Не удалось импортировать: " + message, Toast.LENGTH_LONG).show();
        }
    }

    private void validate(JSONObject manifest) throws Exception {
        java.util.Iterator<String> keys = manifest.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!key.equals("id") && !key.equals("name") && !key.equals("version") && !key.equals("apiVersion") && !key.equals("description") && !key.equals("capabilities") && !key.equals("accentColor") && !key.equals("animationPreset")) {
                throw new IllegalArgumentException("Неизвестное поле: " + key);
            }
        }
        String id = manifest.optString("id", "");
        String name = manifest.optString("name", "");
        String version = manifest.optString("version", "");
        if (!id.matches("[A-Za-z0-9._-]{3,80}")) throw new IllegalArgumentException("ID должен содержать 3–80 символов: буквы, цифры, точку, дефис или подчёркивание");
        if (name.trim().isEmpty() || name.length() > 60) throw new IllegalArgumentException("Название обязательно и не длиннее 60 символов");
        if (version.trim().isEmpty() || version.length() > 32) throw new IllegalArgumentException("Укажи версию плагина");
        if (manifest.optInt("apiVersion", -1) != API_VERSION) throw new IllegalArgumentException("Требуется Plugin API v1");
        JSONArray capabilities = manifest.optJSONArray("capabilities");
        if (capabilities == null || capabilities.length() == 0 || capabilities.length() > 4) throw new IllegalArgumentException("Укажи разрешённые capabilities");
        for (int i = 0; i < capabilities.length(); i++) {
            String cap = capabilities.optString(i, "");
            if (!cap.equals("theme") && !cap.equals("animationPreset")) {
                throw new IllegalArgumentException("Capability запрещена: " + cap);
            }
        }
        if (manifest.has("accentColor")) {
            String color = manifest.optString("accentColor", "");
            if (!color.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("accentColor должен быть #RRGGBB");
        }
        if (manifest.has("animationPreset")) {
            String preset = manifest.optString("animationPreset", "");
            if (!preset.equals("soft") && !preset.equals("none")) throw new IllegalArgumentException("Разрешены animationPreset soft или none");
        }
        if (manifest.optString("description", "").length() > 240) throw new IllegalArgumentException("Описание не длиннее 240 символов");
    }

    private void applyManifest(JSONObject manifest) {
        try {
            JSONArray caps = manifest.optJSONArray("capabilities");
            boolean theme = false;
            if (caps != null) for (int i = 0; i < caps.length(); i++) if ("theme".equals(caps.optString(i))) theme = true;
            if (theme && manifest.has("accentColor")) {
                int color = Color.parseColor(manifest.getString("accentColor"));
                getSharedPreferences("vgram_ui", MODE_PRIVATE).edit().putInt("accent", color).apply();
            }
            JSONArray capabilities = manifest.optJSONArray("capabilities");
            boolean animationAllowed = false;
            if (capabilities != null) for (int i = 0; i < capabilities.length(); i++) if ("animationPreset".equals(capabilities.optString(i))) animationAllowed = true;
            if (animationAllowed && "none".equals(manifest.optString("animationPreset"))) {
                getSharedPreferences("vgram_ui", MODE_PRIVATE).edit().putBoolean("animations", false).apply();
            } else if (animationAllowed && "soft".equals(manifest.optString("animationPreset"))) {
                getSharedPreferences("vgram_ui", MODE_PRIVATE).edit().putBoolean("animations", true).apply();
            }
            VgramTheme.apply(this);
        } catch (Exception ignored) { }
    }
}
