package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

/** Device-local VGRAM visual entitlement state. This class never performs network requests. */
public final class VgramConfig {
    public static final long CREATOR_ID = 8436409687L;
    private static final String PREFS = "vgram_local_entitlements";
    public static volatile boolean isPremium;
    public static volatile boolean showDevBadge;
    public static volatile boolean isVerified;
    public static volatile boolean liquidGlassProfile = true;
    public static volatile boolean priorityMediaLoading = true;

    private VgramConfig() { }

    public static void activateIfCreator(Context context, int account) {
        if (UserConfig.getInstance(account).getClientUserId() != CREATOR_ID) return;
        SharedPreferences p = context.getApplicationContext().getSharedPreferences(PREFS + "_" + account, Context.MODE_PRIVATE);
        isPremium = true;
        showDevBadge = p.getBoolean("show_dev_badge", true);
        isVerified = p.getBoolean("custom_verified", true);
        liquidGlassProfile = p.getBoolean("glass_profile", true);
        priorityMediaLoading = p.getBoolean("priority_media", true);
        p.edit().putBoolean("premium_active", true)
                .putBoolean("creator", true)
                .putBoolean("show_dev_badge", showDevBadge)
                .putBoolean("custom_verified", isVerified)
                .putBoolean("glass_profile", liquidGlassProfile)
                .putBoolean("priority_media", priorityMediaLoading)
                .apply();
    }

    public static SharedPreferences preferences(Context context, int account) {
        return context.getApplicationContext().getSharedPreferences(PREFS + "_" + account, Context.MODE_PRIVATE);
    }

    public static void setFeature(Context context, int account, String key, boolean enabled) {
        SharedPreferences p = preferences(context, account);
        p.edit().putBoolean(key, enabled).apply();
        if ("show_dev_badge".equals(key)) showDevBadge = enabled;
        else if ("custom_verified".equals(key)) isVerified = enabled;
        else if ("glass_profile".equals(key)) liquidGlassProfile = enabled;
        else if ("priority_media".equals(key)) priorityMediaLoading = enabled;
    }
}
