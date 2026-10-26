package org.telegram.ui.VgramModules.Privacy;

import org.telegram.tgnet.TLRPC;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.tgnet.TLObject;

/**
 * Central policy for VGRAM privacy modes.
 *
 * Call shouldSuppressOutgoingRequest(request) immediately before an outgoing
 * request is handed to ConnectionsManager. This class intentionally does not
 * modify Telegram's networking stack by reflection.
 */
public final class VgramGhostController {
    private static final String PREFS = "vgram_privacy";
    public static final String PHANTOM_GHOST = "phantom_ghost";
    public static final String GHOST_TYPING = "ghost_typing";
    public static final String INCOGNITO_MEDIA = "incognito_media";
    public static final String FREEZE_LAST_SEEN = "freeze_last_seen";

    private VgramGhostController() { }

    public static boolean isEnabled(Context context, String key) {
        return preferences(context).getBoolean(key, false);
    }

    public static void setEnabled(Context context, String key, boolean enabled) {
        preferences(context).edit().putBoolean(key, enabled).apply();
    }

    public static boolean shouldSuppressOutgoingRequest(Context context, TLObject request) {
        if (request == null) return false;
        if (isEnabled(context, PHANTOM_GHOST)
                && (request instanceof TLRPC.TL_messages_readHistory
                || request instanceof TLRPC.TL_channels_readHistory)) {
            return true;
        }
        if (isEnabled(context, GHOST_TYPING)
                && request instanceof TLRPC.TL_messages_setTyping) {
            return true;
        }
        if (isEnabled(context, INCOGNITO_MEDIA)
                && request instanceof TLRPC.TL_messages_readMessageContents) {
            return true;
        }
        if (isEnabled(context, FREEZE_LAST_SEEN)
                && "TL_account_updateStatus".equals(request.getClass().getSimpleName())) {
            try {
                java.lang.reflect.Field offline = request.getClass().getField("offline");
                return !offline.getBoolean(request);
            } catch (ReflectiveOperationException ignored) {
                // Unknown schema shape: do not block an unverified request.
            }
        }
        return false;
    }

    public static boolean isGhostTypingEnabled(Context context) {
        return isEnabled(context, GHOST_TYPING);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
