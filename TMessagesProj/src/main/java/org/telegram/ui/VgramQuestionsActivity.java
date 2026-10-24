package org.telegram.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

public class VgramQuestionsActivity extends VgramBaseActivity {
    private void faq(String question, String answer) {
        LinearLayout item = card(question, answer);
        View answerView = item.getChildAt(1);
        answerView.setVisibility(View.GONE);
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(v -> answerView.setVisibility(answerView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setupScreen("HELP CENTER", "Вопросы о VGRAM", "Нажмите на вопрос, чтобы раскрыть ответ.");
        faq("Как войти в аккаунт?", "Используйте номер телефона и код подтверждения Telegram. Никому не передавайте код или пароль двухэтапной проверки.");
        faq("Как работают бейджи?", "TESTER, OG, FRIENDS и TEAM — метки проекта VGRAM. Они не являются значками Telegram; наличие зависит от назначений и поддержки серверной системы.");
        faq("Как обновить приложение?", "Откройте «Обновления VGRAM». Устанавливайте APK только из доверенного источника.");
        faq("Что такое плагины?", "Раздел предназначен для поддерживаемых описаний и пресетов. Не устанавливайте файлы, исполняющие неизвестный код.");
        faq("Что такое VGRAM Premium?", "Это дополнительные функции клиента VGRAM, не связанные с Telegram Premium. Доступность зависит от реализации проекта.");
        faq("Как сообщить об ошибке?", "Создайте Issue в репозитории VGRAM-Main на GitHub. Не публикуйте коды входа, пароли и личные переписки.");
    }
}
