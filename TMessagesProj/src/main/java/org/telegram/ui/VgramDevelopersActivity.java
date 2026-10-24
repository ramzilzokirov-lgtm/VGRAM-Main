package org.telegram.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.VgramApiDiagnostics;

public class VgramDevelopersActivity extends VgramBaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setupScreen("PROJECT INFORMATION", "Команда VGRAM", "Информация о проекте и людях, которые над ним работают.");
        LinearLayout owner = card("ramzilzokirov-lgtm", "АВТОР И ВЛАДЕЛЕЦ\n\nСоздатель проекта VGRAM. Отвечает за направление проекта и его развитие.");
        addButton(owner, "Профиль автора на GitHub", () -> open("https://github.com/ramzilzokirov-lgtm"));
        card("ChatGPT", "ИИ-ПОМОЩНИК\n\nПомощь с проектированием интерфейса, поиском ошибок, документацией и реализацией функций.");
        LinearLayout project = card("О проекте", "VGRAM — независимый Android-клиент, использующий инфраструктуру Telegram. Это не официальный продукт Telegram.");
        addButton(project, "Исходный код VGRAM", () -> open("https://github.com/ramzilzokirov-lgtm/VGRAM-Main"));
        String version = "неизвестна";
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        card("Установленная версия", "VGRAM " + version + "\n\nПроверка и загрузка обновлений доступны внутри приложения.");
        String hashStatus = "не задан";
        try {
            if (BuildVars.APP_HASH != null && !BuildVars.APP_HASH.trim().isEmpty()) {
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                byte[] bytes = digest.digest(BuildVars.APP_HASH.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder fingerprint = new StringBuilder();
                for (byte b : bytes) fingerprint.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
                hashStatus = fingerprint.toString().equals(VgramApiDiagnostics.UPSTREAM_APP_HASH_SHA256) ? "совпадает с исходником" : "отличается от исходника";
            }
        } catch (Exception ignored) { hashStatus = "не удалось проверить"; }
        String idStatus = BuildVars.APP_ID == VgramApiDiagnostics.UPSTREAM_APP_ID ? "совпадает с исходником" : "отличается от исходника";
        LinearLayout api = card("Параметры Telegram API",
            "Значения ниже — именно те, которые использует эта сборка VGRAM. Нажми кнопку, чтобы скопировать значение.\n\nAPI ID: " + BuildVars.APP_ID +
            "\nAPI Hash: " + (BuildVars.APP_HASH == null ? "не задан" : BuildVars.APP_HASH) +
            "\n\nAPI ID в исходнике: " + VgramApiDiagnostics.UPSTREAM_APP_ID + " (" + idStatus + ")" +
            "\nSHA-256 отпечаток исходного API Hash: " + VgramApiDiagnostics.UPSTREAM_APP_HASH_SHA256 +
            "\nIP-адреса дата-центров Telegram клиент получает динамически; фиксированный IP в коде не используется.");
        addButton(api, "Копировать API ID", () -> copyApiValue(String.valueOf(BuildVars.APP_ID)));
        addButton(api, "Копировать API Hash", () -> copyApiValue(BuildVars.APP_HASH == null ? "" : BuildVars.APP_HASH));
    }

    private void copyApiValue(String value) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("VGRAM Telegram API", value));
            android.widget.Toast.makeText(this, "Скопировано", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void open(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) { }
    }
}
