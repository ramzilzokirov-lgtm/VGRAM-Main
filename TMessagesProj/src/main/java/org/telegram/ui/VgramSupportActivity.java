package org.telegram.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class VgramSupportActivity extends VgramBaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setupScreen("VGRAM SUPPORT", "Поддержка VGRAM", "Сообщить об ошибке или предложить улучшение проекта.");
        card("GitHub проекта", "Создайте обращение в репозитории VGRAM-Main. Укажите версию приложения, модель устройства и шаги воспроизведения. Не публикуйте коды входа, пароли и личные переписки.");
        addButton(body, "Написать в поддержку", () -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ramzilzokirov-lgtm/VGRAM-Main/issues"));
            startActivity(intent);
        });
    }
}
