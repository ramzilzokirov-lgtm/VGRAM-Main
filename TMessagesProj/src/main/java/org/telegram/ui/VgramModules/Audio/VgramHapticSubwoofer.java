package org.telegram.ui.VgramModules.Audio;

import android.content.Context;
import android.media.audiofx.Visualizer;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.media3.exoplayer.ExoPlayer;

/** Low-bin FFT bass detector with rate-limited, short haptic pulses. */
public final class VgramHapticSubwoofer {
    private static final String TAG = "VgramHapticBass";
    private final Context appContext;
    private Visualizer visualizer;
    private Vibrator vibrator;
    private boolean enabled;
    private long lastPulseMs;
    private int threshold = 92;

    public VgramHapticSubwoofer(Context context) {
        appContext = context.getApplicationContext();
        vibrator = (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public synchronized boolean start(ExoPlayer player) {
        stop();
        if (!enabled || player == null || !player.isPlaying()) return false;
        int session;
        try { session = player.getAudioSessionId(); }
        catch (RuntimeException error) { return false; }
        if (session <= 0 || vibrator == null || !vibrator.hasVibrator()) return false;
        try {
            visualizer = new Visualizer(session);
            int[] range = Visualizer.getCaptureSizeRange();
            int size = Math.min(range[1], 1024);
            visualizer.setCaptureSize(size);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(Visualizer effect, byte[] waveform, int samplingRate) { }
                @Override public void onFftDataCapture(Visualizer effect, byte[] fft, int samplingRate) {
                    if (!enabled || fft == null || fft.length < 8) return;
                    int bass = 0;
                    for (int i = 2; i < Math.min(fft.length, 12); i += 2) {
                        int real = fft[i];
                        int imaginary = fft[i + 1];
                        bass = Math.max(bass, Math.min(255, (int) Math.hypot(real, imaginary)));
                    }
                    long now = System.currentTimeMillis();
                    if (bass >= threshold && now - lastPulseMs >= 85L) {
                        lastPulseMs = now;
                        pulse();
                    }
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true);
            visualizer.setEnabled(true);
            return true;
        } catch (SecurityException | IllegalStateException | RuntimeException error) {
            Log.w(TAG, "Visualizer permission or audio session unavailable", error);
            stop();
            return false;
        }
    }

    public synchronized void setEnabled(boolean value) {
        enabled = value;
        if (!value) stop();
    }

    public synchronized boolean isEnabled() { return enabled; }

    public synchronized void setThreshold(int value) {
        threshold = Math.max(24, Math.min(220, value));
    }

    public synchronized void stop() {
        if (visualizer != null) {
            try { visualizer.setEnabled(false); } catch (RuntimeException ignored) { }
            try { visualizer.release(); } catch (RuntimeException ignored) { }
            visualizer = null;
        }
        if (vibrator != null) {
            try { vibrator.cancel(); } catch (RuntimeException ignored) { }
        }
    }

    public synchronized void release() {
        enabled = false;
        stop();
        vibrator = null;
    }

    private void pulse() {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(18L, 72));
            } else {
                vibrator.vibrate(18L);
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Haptic pulse failed", error);
        }
    }
}
