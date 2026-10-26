package org.telegram.ui;

import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.SeekBar;

import android.widget.Toast;

public class VgramSettingsActivity extends VgramBaseActivity {
    private static final int PICK_PROFILE_BACKGROUND = 741;
    private static final int CROP_PROFILE_BACKGROUND = 742;
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

        LinearLayout custom = card("Свой цвет · HSV / HEX", "Выбери оттенок ползунком или введи любой цвет в формате HEX. Изменения применяются сразу.");
        TextView colorPreview = label("●  Пользовательский акцент", 14, accent(), true);
        colorPreview.setGravity(Gravity.CENTER_VERTICAL);
        colorPreview.setPadding(dp(10), dp(10), dp(10), dp(10));
        colorPreview.setBackground(panel(0xff22252a, accent(), 14));
        custom.addView(colorPreview, margin(0, dp(8), 0, dp(8)));
        EditText hexInput = new EditText(this);
        hexInput.setSingleLine(true);
        hexInput.setTextColor(0xfff1f0eb);
        hexInput.setHintTextColor(0xff9b9da3);
        hexInput.setHint("#RRGGBB или #AARRGGBB");
        hexInput.setText(String.format(java.util.Locale.US, "#%06X", 0xFFFFFF & accent()));
        hexInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hexInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        hexInput.setBackground(panel(0xff202328, 0x667faaa5, 12));
        custom.addView(hexInput, margin(0, 0, 0, dp(8)));
        SeekBar hue = new SeekBar(this);
        hue.setMax(360);
        float[] initialHsv = new float[3];
        Color.colorToHSV(accent(), initialHsv);
        hue.setProgress(Math.round(initialHsv[0]));
        custom.addView(hue, margin(0, 0, 0, dp(6)));
        hue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (!fromUser) return;
                float[] hsv = new float[]{value, 0.58f, 0.88f};
                int picked = Color.HSVToColor(hsv);
                hexInput.setText(String.format(java.util.Locale.US, "#%06X", 0xFFFFFF & picked));
                colorPreview.setTextColor(picked);
                colorPreview.setBackground(panel(0xff22252a, picked, 14));
                persistCustomAccent(picked);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { recreate(); }
        });
        addButton(custom, "Применить HEX", () -> {
            String raw = hexInput.getText() == null ? "" : hexInput.getText().toString().trim();
            try {
                int picked = Color.parseColor(raw.startsWith("#") ? raw : "#" + raw);
                persistCustomAccent(picked);
                recreate();
            } catch (IllegalArgumentException invalid) {
                Toast.makeText(this, "Введите корректный HEX, например #C5B878", Toast.LENGTH_LONG).show();
            }
        });

        addToggle("Жидкое стекло", "Полупрозрачные панели и мягкие контуры.", "glass", true);

        LinearLayout playerCard = card("VGRAM Player", "Поиск музыки и потоковое воспроизведение без сохранения аудиофайлов.");
        TextView openPlayer = label("Открыть плеер  →", 14, accent(), true);
        openPlayer.setGravity(Gravity.CENTER);
        openPlayer.setPadding(dp(12), dp(13), dp(12), dp(13));
        openPlayer.setBackground(panel(0x20ffffff, 0x40ffffff, 18));
        playerCard.addView(openPlayer, margin(0, dp(12), 0, 0));
        openPlayer.setOnClickListener(v -> startActivity(new Intent(this, VgramPlayer.class)));

        LinearLayout icons = card("Иконка приложения", "10 вариантов V в стиле «Дронов-убийц». Выбор применяется сразу.");
        LinearLayout iconGrid = new LinearLayout(this);
        iconGrid.setOrientation(LinearLayout.VERTICAL);
        int selectedIcon = getSharedPreferences("vgram_ui", MODE_PRIVATE).getInt("launcher_icon", 1);
        for (int row = 0; row < 5; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 2; col++) {
                final int index = row * 2 + col + 1;
                TextView tile = label((selectedIcon == index ? "✓ " : "") + "V " + index, 14, accent(), true);
                tile.setGravity(Gravity.CENTER);
                tile.setBackground(panel(0xff22252a, selectedIcon == index ? accent() : 0xff454951, 14));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(58), 1);
                p.setMargins(dp(3), dp(4), dp(3), dp(4));
                line.addView(tile, p);
                tile.setOnClickListener(v -> applyLauncherIcon(index));
            }
            iconGrid.addView(line);
        }
        icons.addView(iconGrid);
        TextView note = label("Выбор заменяет системную иконку установленного приложения. Дополнительные ярлыки не создаются.", 12, 0xffb9bbc1, false);
        note.setPadding(dp(2), dp(8), dp(2), dp(2));
        icons.addView(note);
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
  // Keep the persisted selection and Android launcher component in sync after reset.
  applyLauncherIcon(1);
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
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == PICK_PROFILE_BACKGROUND) {
            Intent crop = new Intent(this, VgramBannerCropActivity.class);
            crop.setData(uri);
            crop.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try { startActivityForResult(crop, CROP_PROFILE_BACKGROUND); }
            catch (Exception error) { Toast.makeText(this, "Не удалось открыть кадрирование баннера", Toast.LENGTH_LONG).show(); }
            return;
        }
        if (requestCode != CROP_PROFILE_BACKGROUND) return;
        try {
            if ("content".equalsIgnoreCase(uri.getScheme())) {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            getSharedPreferences("vgram_ui", MODE_PRIVATE).edit().putString("profile_background_uri", uri.toString()).apply();
            Toast.makeText(this, "Обрезанный баннер сохранён для VGRAM", Toast.LENGTH_SHORT).show();
            recreate();
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось сохранить обрезанный баннер", Toast.LENGTH_LONG).show();
        }
    }


    private void applyLauncherIcon(int index) {
        if (index < 1 || index > 10) return;
        PackageManager pm = getPackageManager();
        ComponentName fallback = new ComponentName(this, "org.telegram.messenger.DefaultIcon");
        ComponentName selected = new ComponentName(this, String.format(java.util.Locale.US, "org.telegram.messenger.LaunchActivityAlias%d", index));
        ComponentName[] aliases = new ComponentName[10];
        for (int i=1;i<=10;i++) aliases[i-1] = new ComponentName(this, String.format(java.util.Locale.US, "org.telegram.messenger.LaunchActivityAlias%d", i));
        try {
            for (ComponentName alias : aliases) pm.setComponentEnabledSetting(alias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(fallback, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            for (String old : new String[]{"VintageIcon","AquaIcon","PremiumIcon","TurboIcon","NoxIcon"})
                pm.setComponentEnabledSetting(new ComponentName(this,"org.telegram.messenger."+old),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(selected, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            getSharedPreferences("vgram_ui",MODE_PRIVATE).edit().putInt("launcher_icon",index).apply();
            Toast.makeText(this,"Иконка VGRAM изменена",Toast.LENGTH_SHORT).show();
            recreate();
        } catch (Exception error) {
            try { for(ComponentName alias:aliases) pm.setComponentEnabledSetting(alias,PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP); pm.setComponentEnabledSetting(fallback,PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP); } catch(Exception ignored) {}
            Toast.makeText(this,"Не удалось сменить иконку; восстановлена основная иконка VGRAM",Toast.LENGTH_LONG).show();
        }
    }

    private void persistCustomAccent(int color) {
        getSharedPreferences("vgram_ui", MODE_PRIVATE).edit()
                .putInt("vgram_custom_accent", color)
                .putInt("accent", color)
                .apply();
        VgramTheme.apply(this);
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
