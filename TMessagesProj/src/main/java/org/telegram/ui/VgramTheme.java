package org.telegram.ui;

import android.content.Context;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.Theme;

public final class VgramTheme {
    private VgramTheme() {}
    public static int accent(Context context) {
        return context.getSharedPreferences("vgram_ui", Context.MODE_PRIVATE).getInt("accent", 0xffc5b878);
    }
    public static void apply(Context context) {
        int a = accent(context);
        // Apply a muted brown/amber palette inspired by the requested YouGram references.
        Theme.setColor(Theme.key_windowBackgroundWhite, 0xff17100c, false);
        Theme.setColor(Theme.key_windowBackgroundWhiteBlackText, 0xfff5eee7, false);
        Theme.setColor(Theme.key_windowBackgroundWhiteGrayText, 0xffc8bdb2, false);
        Theme.setColor(Theme.key_windowBackgroundWhiteGrayText2, 0xffaaa096, false);
        Theme.setColor(Theme.key_windowBackgroundWhiteHintText, 0xffa99c90, false);
        Theme.setColor(Theme.key_windowBackgroundWhiteInputField, 0xff30251d, false);
        Theme.setColor(Theme.key_windowBackgroundWhiteInputFieldActivated, a, false);
        Theme.setColor(Theme.key_dialogBackground, 0xff211810, false);
        Theme.setColor(Theme.key_dialogBackgroundGray, 0xff19130f, false);
        Theme.setColor(Theme.key_windowBackgroundWhiteBlueText, a, false);
        Theme.setColor(Theme.key_windowBackgroundWhiteBlueText2, a, false);
        Theme.setColor(Theme.key_windowBackgroundWhiteBlueIcon, a, false);
        Theme.setColor(Theme.key_windowBackgroundWhiteBlueButton, a, false);
        Theme.setColor(Theme.key_actionBarDefault, 0xff151619, false);
        Theme.setColor(Theme.key_actionBarDefaultIcon, 0xffe5e4dd, false);
        Theme.setColor(Theme.key_actionBarDefaultTitle, 0xfff0f0ec, false);
        Theme.setColor(Theme.key_chats_unreadCounter, a, false);
        Theme.setColor(Theme.key_chats_nameMessage, a, false);
        Theme.setColor(Theme.key_chat_outBubble, blend(a, 0xff222326, 0.30f), false);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors);
    }
    private static int blend(int foreground, int background, float amount) {
        int r = (int) (android.graphics.Color.red(foreground) * amount + android.graphics.Color.red(background) * (1f - amount));
        int g = (int) (android.graphics.Color.green(foreground) * amount + android.graphics.Color.green(background) * (1f - amount));
        int b = (int) (android.graphics.Color.blue(foreground) * amount + android.graphics.Color.blue(background) * (1f - amount));
        return android.graphics.Color.rgb(r, g, b);
    }
}
