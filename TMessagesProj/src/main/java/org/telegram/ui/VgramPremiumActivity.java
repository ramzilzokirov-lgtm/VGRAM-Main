package org.telegram.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.VgramConfig;

/** Local-only VGRAM Premium controls. No HTTP endpoints are called from this screen. */
public class VgramPremiumActivity extends VgramBaseActivity {
    private int account;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        account = UserConfig.selectedAccount;
        VgramConfig.activateIfCreator(getApplicationContext(), account);
        if (UserConfig.getInstance(account).getClientUserId() != VgramConfig.CREATOR_ID) {
            finish();
            return;
        }
        buildPremiumScreen();
    }

    private void buildPremiumScreen() {
        setupScreen("VGRAM PREMIUM", "VGRAM Premium", "Локальные визуальные функции. Настройки сохраняются на этом устройстве.");
        LinearLayout status = card("✦  VGRAM Premium активен", "Создатель VGRAM · локальная активация · без серверных запросов");
        TextView badge = label("  ◆ DEV     ✓ VERIFIED  ", 13, 0xffd9c7ff, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(12), dp(10), dp(12), dp(10));
        badge.setBackground(panel(0x267c4dff, 0x557c4dff, 18));
        status.addView(badge, margin(0, dp(12), 0, 0));

        LinearLayout controls = card("Визуальные функции", "Переключатели применяются и сохраняются сразу.");
        addLocalSwitch(controls, "Кастомная галочка верификации", "Локальная отметка VGRAM рядом с именем.", "custom_verified", VgramConfig.isVerified);
        addLocalSwitch(controls, "Бейдж Создателя (DEV) в профиле и чатах", "Локальный бейдж создателя.", "show_dev_badge", VgramConfig.showDevBadge);
        addLocalSwitch(controls, "Liquid Glass эффекты для профиля", "Стеклянные поверхности профиля.", "glass_profile", VgramConfig.liquidGlassProfile);
        addLocalSwitch(controls, "Приоритетная загрузка медиа", "Локальный флаг предпочтения загрузки медиа.", "priority_media", VgramConfig.priorityMediaLoading);
        addButton(controls, "Назад к настройкам", this::finish);
    }

    private void addLocalSwitch(LinearLayout parent, String title, String description, String key, boolean initial) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(8), dp(10));
        row.setBackground(panel(0x18ffffff, 0x30ffffff, 18));
        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        TextView heading = label(title, 14, 0xfff4f0ff, true);
        TextView detail = label(description, 12, 0xffb7b2c8, false);
        detail.setPadding(0, dp(4), 0, 0);
        textColumn.addView(heading);
        textColumn.addView(detail);
        row.addView(textColumn, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setChecked(VgramConfig.preferences(this, account).getBoolean(key, initial));
        row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
        View.OnClickListener click = v -> toggle.setChecked(!toggle.isChecked());
        heading.setOnClickListener(click);
        detail.setOnClickListener(click);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            VgramConfig.setFeature(getApplicationContext(), account, key, checked);
            android.widget.Toast.makeText(this, "Сохранено локально", android.widget.Toast.LENGTH_SHORT).show();
        });
        parent.addView(row, margin(0, dp(8), 0, 0));
    }
}
