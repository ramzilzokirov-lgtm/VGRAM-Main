package org.telegram.ui.VgramModules.Audio;

import android.content.Context;
import android.media.audiofx.BassBoost;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.media3.common.PlaybackParameters;
import androidx.media3.exoplayer.ExoPlayer;

/** AudioFX controller bound to the existing VGRAM ExoPlayer audio session. */
public final class VgramSoundLab {
    public enum Preset { NORMAL, BASS_BOOST, SLOWED_REVERB, NIGHTCORE, SPATIAL_8D }

    private static final String TAG = "VgramSoundLab";
    private static VgramSoundLab instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Context appContext;
    private ExoPlayer player;
    private int attachedSessionId = -1;
    private BassBoost bassBoost;
    private PresetReverb reverb;
    private Virtualizer virtualizer;
    private Preset preset = Preset.NORMAL;
    private int bassStrength = 650;
    private boolean enabled;
    private long spatialStartMs;
    private final Runnable spatialSweep = new Runnable() {
        @Override public void run() {
            if (!enabled || preset != Preset.SPATIAL_8D || player == null || !player.isPlaying()) return;
            attachEffectsIfNeeded();
            if (virtualizer != null) {
                double phase = (System.currentTimeMillis() - spatialStartMs) / 1250.0;
                int strength = (int) (350 + 350 * (0.5 + 0.5 * Math.sin(phase)));
                try { virtualizer.setStrength((short) Math.max(0, Math.min(1000, strength))); }
                catch (RuntimeException error) { Log.w(TAG, "Unable to update spatial effect", error); }
            }
            handler.postDelayed(this, 60L);
        }
    };

    private VgramSoundLab(Context context) { appContext = context.getApplicationContext(); }

    public static synchronized VgramSoundLab get(Context context) {
        if (instance == null) instance = new VgramSoundLab(context);
        return instance;
    }

    /** Connects to the player owned by VgramAudioService; it never creates another player. */
    public void connect(Context context, ExoPlayer existingPlayer) {
        runOnMain(() -> {
            if (appContext == null && context != null) appContext = context.getApplicationContext();
            if (player != existingPlayer) {
                releaseEffects();
                player = existingPlayer;
                attachedSessionId = -1;
            }
            attachEffectsIfNeeded();
            applyPresetInternal(preset);
        });
    }

    public void setBassStrength(int strength) {
        bassStrength = Math.max(0, Math.min(1000, strength));
        runOnMain(() -> {
            attachEffectsIfNeeded();
            if (bassBoost != null) {
                try { bassBoost.setStrength((short) bassStrength); }
                catch (RuntimeException error) { Log.w(TAG, "Bass strength update failed", error); }
            }
        });
    }

    public void applyPreset(Preset value) {
        if (value == null) value = Preset.NORMAL;
        final Preset requested = value;
        runOnMain(() -> applyPresetInternal(requested));
    }

    public Preset getPreset() { return preset; }

    private void applyPresetInternal(Preset value) {
        preset = value;
        enabled = value != Preset.NORMAL;
        handler.removeCallbacks(spatialSweep);
        if (player == null) return;
        attachEffectsIfNeeded();
        try {
            switch (value) {
                case BASS_BOOST:
                    player.setPlaybackParameters(PlaybackParameters.DEFAULT);
                    setEnabled(bassBoost, true);
                    setEnabled(reverb, false);
                    setEnabled(virtualizer, false);
                    if (bassBoost != null) bassBoost.setStrength((short) bassStrength);
                    break;
                case SLOWED_REVERB:
                    player.setPlaybackParameters(new PlaybackParameters(0.85f, 1.0f));
                    setEnabled(bassBoost, false);
                    setEnabled(reverb, true);
                    setEnabled(virtualizer, false);
                    if (reverb != null) reverb.setPreset(PresetReverb.PRESET_LARGEHALL);
                    break;
                case NIGHTCORE:
                    player.setPlaybackParameters(new PlaybackParameters(1.25f, 1.25f));
                    setEnabled(bassBoost, false);
                    setEnabled(reverb, false);
                    setEnabled(virtualizer, false);
                    break;
                case SPATIAL_8D:
                    player.setPlaybackParameters(PlaybackParameters.DEFAULT);
                    setEnabled(bassBoost, false);
                    setEnabled(reverb, false);
                    setEnabled(virtualizer, true);
                    spatialStartMs = System.currentTimeMillis();
                    handler.post(spatialSweep);
                    break;
                case NORMAL:
                default:
                    player.setPlaybackParameters(PlaybackParameters.DEFAULT);
                    setEnabled(bassBoost, false);
                    setEnabled(reverb, false);
                    setEnabled(virtualizer, false);
                    break;
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to apply " + value + " preset", error);
        }
    }

    private void attachEffectsIfNeeded() {
        if (player == null) return;
        int sessionId;
        try { sessionId = player.getAudioSessionId(); }
        catch (RuntimeException error) { Log.w(TAG, "Cannot read audio session id", error); return; }
        if (sessionId <= 0 || sessionId == attachedSessionId) return;
        releaseEffects();
        attachedSessionId = sessionId;
        try { bassBoost = new BassBoost(0, sessionId); } catch (Exception error) { Log.w(TAG, "BassBoost unavailable", error); }
        try { reverb = new PresetReverb(0, sessionId); } catch (Exception error) { Log.w(TAG, "PresetReverb unavailable", error); }
        try { virtualizer = new Virtualizer(0, sessionId); } catch (Exception error) { Log.w(TAG, "Virtualizer unavailable", error); }
        if (bassBoost != null) try { bassBoost.setStrength((short) bassStrength); } catch (RuntimeException ignored) { }
    }

    private static void setEnabled(android.media.audiofx.AudioEffect effect, boolean value) {
        if (effect == null) return;
        try { effect.setEnabled(value); } catch (RuntimeException error) { Log.w(TAG, "Audio effect enable failed", error); }
    }

    private void releaseEffects() {
        handler.removeCallbacks(spatialSweep);
        release(bassBoost); bassBoost = null;
        release(reverb); reverb = null;
        release(virtualizer); virtualizer = null;
        attachedSessionId = -1;
    }

    private static void release(android.media.audiofx.AudioEffect effect) {
        if (effect == null) return;
        try { effect.setEnabled(false); } catch (RuntimeException ignored) { }
        try { effect.release(); } catch (RuntimeException ignored) { }
    }

    /** Release effect handles on track/session changes or owner shutdown. */
    public void releaseForTrackChange() {
        runOnMain(() -> {
            releaseEffects();
            if (player != null) attachEffectsIfNeeded();
            applyPresetInternal(preset);
        });
    }

    public void release() {
        runOnMain(() -> {
            handler.removeCallbacks(spatialSweep);
            releaseEffects();
            player = null;
            enabled = false;
        });
    }

    private static void runOnMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else new Handler(Looper.getMainLooper()).post(action);
    }
}
