package org.telegram.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;

public class VgramSettingsActivity extends VgramBaseActivity {
    private static final int PICK_PROFILE_BACKGROUND = 741;
    private final int[] colors = { 0xffc5b878, 0xffa8a0c7, 0xff7eaaa5, 0xffbd858d };
    private final String[] names = { "ЗОЛОТО", "ЛАВАНДА", "МЯТА", "РОЗОВЫЙ" };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        build();
    }

    private void build() {
        setupScreen("PERSONALIZATION", "VGRAM Studio", "Настрой интерфейс под себя. Изменения сохраняются на устройстве.");
        card("DARK LIQUID GLASS", "Графитовые поверхности, приглушённые цвета, тонкие контуры и собственные элементы управления.");

        LinearLayout palette = card("Акцент интерфейса", "Цвет выделения применяется к основным цветам Telegram и экранам VGRAM.");
        LinearLayout swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        swatches.setGravity(Gravity.CENTER_VERTICAL);
        swatches.setPadding(0, dp(15), 0, 0);
        int active = accent();
        for (int i = 0; i < colors.length; i++) {
  final int color = colors[i];
  TextView swatch = label((color == active ? "●" : "○") + "\n" + names[i], 10, color, true);
  swatch.setGravity(Gravity.CENTER);
  swatch.setLineSpacing(dp(4), 1f);
  swatch.setPadding(dp(4), dp(10), dp(4), dp(10));
  swatch.setBackground(panel(0xff22252a, color == active ? color : 0xff414348, 15));
  LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(70), 1);
  lp.setMargins(dp(3), 0, dp(3), 0);
  swatches.addView(swatch, lp);
  swatch.setOnClickListener(v -> {
      getSharedPreferences("vgram_ui", MODE_PRIVATE).edit().putInt("accent", color).apply();
      VgramTheme.apply(this);
      recreate();
  });
        }
        palette.addView(swatches);
        addToggle("Жидкое стекло", "Полупрозрачные панели и мягкие контуры.", "glass", true);
        addToggle("Мягкие анимации", "Небольшие анимации на элементах VGRAM.", "animations", true);

        LinearLayout profileBackground = card("Баннер профиля", "Выбери изображение для верхней области своего профиля. Баннер сохраняется локально на этом устройстве.");
        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(panel(0xff25272b, 0xff42464d, 18));
        String savedUri = getSharedPreferences("vgram_ui", MODE_PRIVATE).getString("profile_background_uri", "");
        if (!savedUri.isEmpty()) {
            try {
                preview.setImageURI(Uri.parse(savedUri));
                preview.setVisibility(android.view.View.VISIBLE);
            } catch (Exception ignored) { preview.setVisibility(android.view.View.GONE); }
        } else {
            preview.setVisibility(android.view.View.GONE);
        }
        profileBackground.addView(preview, new LinearLayout.LayoutParams(-1, dp(150)));
        addButton(profileBackground, "Выбрать изображение", () -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("image/*");
            pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try { startActivityForResult(pick, PICK_PROFILE_BACKGROUND); }
            catch (Exception error) { Toast.makeText(this, "Не удалось открыть галерею", Toast.LENGTH_LONG).show(); }
        });
        addButton(profileBackground, "Удалить фон", () -> {
            getSharedPreferences("vgram_ui", MODE_PRIVATE).edit().remove("profile_background_uri").apply();
            recreate();
        });

        LinearLayout reset = card("Сбросить оформление", "Вернуть приглушённый золотой акцент и эффекты по умолчанию.");
        addButton(reset, "Сбросить настройки", () -> {
  getSharedPreferences("vgram_ui", MODE_PRIVATE).edit().clear().apply();
  VgramTheme.apply(this);
  recreate();
        });
        LinearLayout plugins = card("Плагины VGRAM", "Подключай безопасные JSON-манифесты тем и пресетов. Исполняемый код плагинов не загружается.");
        addButton(plugins, "Открыть менеджер плагинов", () -> startActivity(new android.content.Intent(this, VgramPluginsActivity.class)));
        addButton(plugins, "Документация Plugin API", () -> {
  try {
      startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
          android.net.Uri.parse("https://github.com/ramzilzokirov-lgtm/VGRAM-Main/blob/main/PLUGIN_API.md")));
  } catch (Exception ignored) { }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_PROFILE_BACKGROUND || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getSharedPreferences("vgram_ui", MODE_PRIVATE).edit().putString("profile_background_uri", uri.toString()).apply();
            Toast.makeText(this, "Изображение сохранено для VGRAM", Toast.LENGTH_SHORT).show();
            recreate();
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось сохранить доступ к изображению", Toast.LENGTH_LONG).show();
        }
    }

    private void addToggle(String title, String description, String key, boolean fallback) {
        LinearLayout item = card(title, description);
        boolean enabled = getSharedPreferences("vgram_ui", MODE_PRIVATE).getBoolean(key, fallback);
        TextView state = label(enabled ? "ВКЛ  ●" : "ВЫКЛ  ○", 12, accent(), true);
        state.setGravity(Gravity.CENTER);
        state.setPadding(dp(12), dp(10), dp(12), dp(10));
        state.setBackground(panel(0xff292c31, Color.argb(100, Color.red(accent()), Color.green(accent()), Color.blue(accent())), 14));
        item.addView(state, margin(0, dp(13), 0, 0));
        item.setClickable(true);
        item.setOnClickListener(v -> {
  boolean next = !getSharedPreferences("vgram_ui", MODE_PRIVATE).getBoolean(key, fallback);
  getSharedPreferences("vgram_ui", MODE_PRIVATE).edit().putBoolean(key, next).apply();
  recreate();
        });
        state.setOnClickListener(v -> item.performClick());
    }
}
