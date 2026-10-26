package org.telegram.ui.VgramModules.Privacy;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Encrypted, device-local storage for hidden dialog IDs and vault PIN settings. */
public final class VgramVaultManager {
    private static final String PREFS = "vgram_vault";
    private static final String KEY_ALIAS = "vgram_vault_aes_v1";
    private static final String HIDDEN = "hidden_dialogs";
    private static final String PIN_SALT = "pin_salt";
    private static final String PIN_HASH = "pin_hash";
    private static final String DECOY_HASH = "decoy_hash";
    private static final String UNLOCKED = "unlocked_session";

    public enum PinResult { PRIMARY, DECOY, REJECTED }

    private VgramVaultManager() { }

    public static synchronized void hideDialog(Context context, long dialogId) {
        Set<Long> ids = getHiddenDialogIds(context);
        ids.add(dialogId);
        saveIds(context, ids);
    }

    public static synchronized void unhideDialog(Context context, long dialogId) {
        Set<Long> ids = getHiddenDialogIds(context);
        ids.remove(dialogId);
        saveIds(context, ids);
    }

    public static synchronized boolean isHidden(Context context, long dialogId) {
        return getHiddenDialogIds(context).contains(dialogId);
    }

    public static synchronized Set<Long> getHiddenDialogIds(Context context) {
        if (isDecoySession(context)) return new HashSet<>();
        String encrypted = prefs(context).getString(HIDDEN, "");
        HashSet<Long> ids = new HashSet<>();
        if (encrypted.isEmpty()) return ids;
        try {
            String plain = decrypt(encrypted);
            JSONArray array = new JSONArray(plain);
            for (int i = 0; i < array.length(); i++) ids.add(array.getLong(i));
        } catch (Exception ignored) {
            // Fail closed: unreadable vault data is not exposed to the main list.
        }
        return ids;
    }

    public static synchronized void configurePins(Context context, char[] primaryPin, char[] decoyPin) throws Exception {
        if (!validPin(primaryPin) || !validPin(decoyPin) || constantTimeEquals(primaryPin, decoyPin)) {
            throw new IllegalArgumentException("PIN-коды должны содержать 4–12 цифр и отличаться друг от друга.");
        }
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] decoySalt = new byte[16];
        new SecureRandom().nextBytes(decoySalt);
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putString(PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP));
        editor.putString(PIN_HASH, Base64.encodeToString(hashPin(primaryPin, salt), Base64.NO_WRAP));
        editor.putString("decoy_salt", Base64.encodeToString(decoySalt, Base64.NO_WRAP));
        editor.putString(DECOY_HASH, Base64.encodeToString(hashPin(decoyPin, decoySalt), Base64.NO_WRAP));
        editor.apply();
    }

    public static synchronized PinResult verifyPin(Context context, char[] pin) {
        SharedPreferences p = prefs(context);
        try {
            if (matches(p, pin, PIN_SALT, PIN_HASH)) {
                p.edit().putBoolean(UNLOCKED, true).putBoolean("decoy_session", false).apply();
                return PinResult.PRIMARY;
            }
            if (matches(p, pin, "decoy_salt", DECOY_HASH)) {
                p.edit().putBoolean(UNLOCKED, true).putBoolean("decoy_session", true).apply();
                return PinResult.DECOY;
            }
        } catch (Exception ignored) { }
        return PinResult.REJECTED;
    }

    public static boolean isUnlocked(Context context) {
        return prefs(context).getBoolean(UNLOCKED, false);
    }

    public static boolean isDecoySession(Context context) {
        return isUnlocked(context) && prefs(context).getBoolean("decoy_session", false);
    }

    public static void lock(Context context) {
        prefs(context).edit().putBoolean(UNLOCKED, false).putBoolean("decoy_session", false).apply();
    }

    private static void saveIds(Context context, Set<Long> ids) {
        JSONArray array = new JSONArray();
        for (Long id : ids) array.put(id);
        try {
            prefs(context).edit().putString(HIDDEN, encrypt(array.toString())).apply();
        } catch (Exception error) {
            throw new IllegalStateException("Не удалось зашифровать сейф VGRAM", error);
        }
    }

    private static String encrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getKey());
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }

    private static String decrypt(String value) throws Exception {
        byte[] combined = Base64.decode(value, Base64.NO_WRAP);
        if (combined.length < 13) throw new IllegalArgumentException("Invalid vault payload");
        byte[] iv = new byte[12];
        byte[] encrypted = new byte[combined.length - iv.length];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static SecretKey getKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        java.security.Key key = store.getKey(KEY_ALIAS, null);
        if (key instanceof SecretKey) return (SecretKey) key;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private static boolean matches(SharedPreferences prefs, char[] pin, String saltKey, String hashKey) throws Exception {
        String saltRaw = prefs.getString(saltKey, "");
        String hashRaw = prefs.getString(hashKey, "");
        if (saltRaw.isEmpty() || hashRaw.isEmpty()) return false;
        byte[] expected = Base64.decode(hashRaw, Base64.NO_WRAP);
        byte[] actual = hashPin(pin, Base64.decode(saltRaw, Base64.NO_WRAP));
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] hashPin(char[] pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin, salt, 120000, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static boolean validPin(char[] pin) {
        if (pin == null || pin.length < 4 || pin.length > 12) return false;
        for (char c : pin) if (c < '0' || c > '9') return false;
        return true;
    }

    private static boolean constantTimeEquals(char[] first, char[] second) {
        if (first == null || second == null || first.length != second.length) return false;
        int result = 0;
        for (int i = 0; i < first.length; i++) result |= first[i] ^ second[i];
        return result == 0;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
