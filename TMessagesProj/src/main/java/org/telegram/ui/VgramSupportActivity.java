package org.telegram.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class VgramSupportActivity extends VgramBaseActivity {
    private static final String ISSUES_URL = "https://github.com/ramzilzokirov-lgtm/VGRAM-Main/issues";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setupScreen("VGRAM SUPPORT", "Поддержка VGRAM",
                "Сообщить об ошибке или предложить улучшение проекта.");
        card("Связаться с разработчиком",
                "Официальная страница обращений проекта VGRAM — GitHub Issues. Опишите проблему, шаги воспроизведения и модель устройства. Не прикладывайте коды входа, пароли или личные переписки.");
        addButton(body, "Написать в поддержку", () -> openUrl(ISSUES_URL));
        card("Репозиторий",
                "Исходный код VGRAM-Main и история изменений доступны на GitHub: ramzilzokirov-lgtm/VGRAM-Main.");
        addButton(body, "Открыть исходный код", () -> openUrl("https://github.com/ramzilzokirov-lgtm/VGRAM-Main"));
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
            android.widget.Toast.makeText(this, "Не удалось открыть GitHub. Проверьте подключение к интернету.", android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
