package org.telegram.ui.VgramModules.Privacy;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bounded local journal for message deletions and text edits.
 *
 * The network update handlers must call recordDeleted/recordEdit before
 * applying the upstream delete/edit operation. This journal never uploads
 * stored message text and limits retained history to avoid unbounded growth.
 */
public final class VgramAntiDeleteManager {
    private static final String PREFS = "vgram_antidelete";
    private static final String JOURNAL = "journal_v1";
    private static final int MAX_RECORDS = 500;
    private static final int MAX_TEXT_LENGTH = 8192;

    public static final class Entry {
        public final long dialogId;
        public final int messageId;
        public final String kind;
        public final String text;
        public final long timestamp;

        private Entry(long dialogId, int messageId, String kind, String text, long timestamp) {
            this.dialogId = dialogId;
            this.messageId = messageId;
            this.kind = kind;
            this.text = text;
            this.timestamp = timestamp;
        }
    }

    private VgramAntiDeleteManager() { }

    public static synchronized void recordDeleted(Context context, long dialogId, int messageId, String lastText) {
        append(context, new Entry(dialogId, messageId, "deleted", safeText(lastText), System.currentTimeMillis()));
    }

    public static synchronized void recordEdit(Context context, long dialogId, int messageId, String previousText) {
        if (TextUtils.isEmpty(previousText)) return;
        append(context, new Entry(dialogId, messageId, "edited", safeText(previousText), System.currentTimeMillis()));
    }

    public static synchronized List<Entry> getHistory(Context context, long dialogId, int messageId) {
        JSONArray records = read(context);
        ArrayList<Entry> result = new ArrayList<>();
        for (int i = 0; i < records.length(); i++) {
            JSONObject item = records.optJSONObject(i);
            if (item == null || item.optLong("dialog") != dialogId || item.optInt("message") != messageId) continue;
            result.add(fromJson(item));
        }
        Collections.reverse(result);
        return Collections.unmodifiableList(result);
    }

    public static synchronized boolean wasDeleted(Context context, long dialogId, int messageId) {
        for (Entry entry : getHistory(context, dialogId, messageId)) {
            if ("deleted".equals(entry.kind)) return true;
        }
        return false;
    }

    private static void append(Context context, Entry entry) {
        JSONArray old = read(context);
        JSONArray next = new JSONArray();
        int start = Math.max(0, old.length() - MAX_RECORDS + 1);
        for (int i = start; i < old.length(); i++) {
            JSONObject item = old.optJSONObject(i);
            if (item != null) next.put(item);
        }
        JSONObject item = new JSONObject();
        try {
            item.put("dialog", entry.dialogId);
            item.put("message", entry.messageId);
            item.put("kind", entry.kind);
            item.put("text", entry.text);
            item.put("time", entry.timestamp);
            next.put(item);
        } catch (JSONException ignored) {
            return;
        }
        prefs(context).edit().putString(JOURNAL, next.toString()).apply();
    }

    private static JSONArray read(Context context) {
        String raw = prefs(context).getString(JOURNAL, "[]");
        try {
            return new JSONArray(raw);
        } catch (JSONException invalid) {
            return new JSONArray();
        }
    }

    private static Entry fromJson(JSONObject item) {
        return new Entry(item.optLong("dialog"), item.optInt("message"),
                item.optString("kind"), item.optString("text"), item.optLong("time"));
    }

    private static String safeText(String value) {
        if (value == null) return "";
        return value.length() <= MAX_TEXT_LENGTH ? value : value.substring(0, MAX_TEXT_LENGTH);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
