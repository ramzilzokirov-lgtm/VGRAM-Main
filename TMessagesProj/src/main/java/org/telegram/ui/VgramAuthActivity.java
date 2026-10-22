package org.telegram.ui;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.VgramApiClient;

/**
 * Initial independent VGRAM account screen. It is intentionally not made the
 * launcher until an HTTPS backend has been deployed and configured.
 */
public final class VgramAuthActivity extends Activity {
    private static final String PREFS = "vgram_account";
    private EditText serverInput, usernameInput, displayNameInput, passwordInput;
    private TextView status;
    private Button registerButton, loginButton;
    private VgramApiClient client;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(24, 24, 22));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(24));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("VGRAM");
        title.setTextColor(Color.rgb(225, 203, 91));
        title.setTextSize(34);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Независимый аккаунт VGRAM");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, matchWrap());

        serverInput = field("HTTPS-адрес API сервера", false);
        serverInput.setText(prefs.getString("server_url", ""));
        root.addView(serverInput, matchWrap());
        usernameInput = field("Имя пользователя", false);
        root.addView(usernameInput, matchWrap());
        displayNameInput = field("Отображаемое имя (для регистрации)", false);
        root.addView(displayNameInput, matchWrap());
        passwordInput = field("Пароль", true);
        root.addView(passwordInput, matchWrap());

        registerButton = button("Создать аккаунт");
        loginButton = button("Войти");
        root.addView(registerButton, matchWrap());
        root.addView(loginButton, matchWrap());

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(14);
        status.setPadding(0, dp(18), 0, 0);
        status.setText("Укажите HTTPS-адрес работающего сервера VGRAM.");
        root.addView(status, matchWrap());
        registerButton.setOnClickListener(v -> authenticate(true));
        loginButton.setOnClickListener(v -> authenticate(false));
        setContentView(scroll);
    }

    private EditText field(String hint, boolean password) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setTextColor(Color.WHITE);
        edit.setHintTextColor(Color.GRAY);
        edit.setHint(hint);
        edit.setTextSize(16);
        edit.setPadding(dp(12), dp(8), dp(12), dp(8));
        if (password) edit.setInputType(129);
        else if (hint.startsWith("HTTPS")) edit.setInputType(UriInputType.TEXT_URI);
        else edit.setInputType(1);
        return edit;
    }

    private static final class UriInputType {
        static final int TEXT_URI = 17;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.rgb(28, 27, 21));
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(225, 203, 91)));
        return b;
    }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(12);
        return p;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void authenticate(boolean register) {
        String server = serverInput.getText().toString().trim();
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String displayName = displayNameInput.getText().toString().trim();
        if (server.isEmpty() || username.isEmpty() || password.isEmpty()) {
            status.setText("Заполните адрес сервера, имя пользователя и пароль.");
            return;
        }
        try {
            client = new VgramApiClient(server);
        } catch (IllegalArgumentException e) {
            status.setText("Нужен действующий HTTPS-адрес API сервера.");
            return;
        }
        prefs.edit().putString("server_url", server).apply();
        client.setAccessToken(prefs.getString("access_token", null));
        setBusy(true, register ? "Создаём аккаунт…" : "Выполняем вход…");
        VgramApiClient.Callback callback = new VgramApiClient.Callback() {
            @Override public void onSuccess(JSONObject response) {
                runOnUiThread(() -> {
                    setBusy(false, "Успешно. Проверяю профиль…");
                    String token = client.getAccessToken();
                    if (token != null) prefs.edit().putString("access_token", token).apply();
                    client.getCurrentUser(new VgramApiClient.Callback() {
                        @Override public void onSuccess(JSONObject user) {
                            runOnUiThread(() -> {
                                String number = user.optString("internal_number", "не назначен");
                                String name = user.optString("display_name", username);
                                status.setText("Вы вошли как " + name + "\nНомер VGRAM: " + number
                                        + "\nНа старте доступен аккаунт и API. Интерфейс чатов ещё подключается.");
                            });
                        }
                        @Override public void onError(String message) {
                            runOnUiThread(() -> status.setText("Вход выполнен, но профиль не загрузился: " + message));
                        }
                    });
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> setBusy(false, "Ошибка: " + message));
            }
        };
        if (register) {
            if (displayName.isEmpty()) {
                setBusy(false, "Введите отображаемое имя для регистрации.");
                return;
            }
            client.register(username, password, displayName, callback);
        } else {
            client.login(username, password, callback);
        }
    }

    private void setBusy(boolean busy, String message) {
        registerButton.setEnabled(!busy);
        loginButton.setEnabled(!busy);
        status.setText(message);
    }
}
