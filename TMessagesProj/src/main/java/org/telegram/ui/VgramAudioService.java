package org.telegram.ui;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import org.telegram.ui.VgramModules.Audio.VgramHapticSubwoofer;
import org.telegram.ui.VgramModules.Audio.VgramSoundLab;
import org.telegram.ui.VgramModules.Audio.VgramVisualizerView;
import org.telegram.ui.VgramModules.Audio.VgramRenderersFactory;
import org.telegram.ui.VgramModules.Audio.VgramStereoPanAudioProcessor;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Process-wide audio playback owner. The player is intentionally not owned by
 * VgramPlayer's Activity, so navigating away from the search screen won't stop audio.
 */
public final class VgramAudioService {
    private static ExoPlayer player;
    private static String title = "";
    private static String artworkUrl = "";
    private static FrameLayout miniBar;
    private static TextView titleView;
    private static TextView playView;
    private static ImageView artworkView;
    private static VgramVisualizerView miniVisualizer;
    private static VgramHapticSubwoofer hapticSubwoofer;
    private static VgramRenderersFactory renderersFactory;
    private static Context appContext;
    private static boolean lifecycleHookInstalled;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final ExecutorService artworkExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "VGRAM-Artwork");
        thread.setDaemon(true);
        return thread;
    });
    private static String loadedArtworkUrl = "";

    private VgramAudioService() {}

    public static ExoPlayer getPlayer(Context context) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return getPlayerOnUiThread(context);
        }
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final ExoPlayer[] result = new ExoPlayer[1];
        final RuntimeException[] failure = new RuntimeException[1];
        AndroidUtilities.runOnUIThread(() -> {
            try {
                result[0] = getPlayerOnUiThread(context);
            } catch (RuntimeException error) {
                failure[0] = error;
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the VGRAM player UI thread");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating the VGRAM player", interrupted);
        }
        if (failure[0] != null) throw failure[0];
        if (result[0] == null) throw new IllegalStateException("VGRAM player is unavailable");
        return result[0];
    }

    private static ExoPlayer getPlayerOnUiThread(Context context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("ExoPlayer must only be accessed on the main thread");
        }
        if (player == null) {
            Context safeContext = context != null ? context.getApplicationContext() : appContext;
            if (safeContext == null) {
                safeContext = ApplicationLoader.applicationContext;
            }
            if (safeContext == null) throw new IllegalStateException("No application context available for VGRAM player");
            appContext = safeContext;
            try {
                renderersFactory = new VgramRenderersFactory(appContext);
                player = new ExoPlayer.Builder(appContext, renderersFactory).build();
                installLaunchActivityMiniPlayerHook(appContext);
                player.addListener(new Player.Listener() {
                    @Override public void onIsPlayingChanged(boolean isPlaying) { syncAudioModules(); refreshMiniPlayerSafely(); }
                    @Override public void onMediaItemTransition(MediaItem item, int reason) {
                        VgramSoundLab.get(appContext).releaseForTrackChange();
                        syncAudioModules();
                        refreshMiniPlayerSafely();
                    }
                    @Override public void onPlaybackStateChanged(int state) { syncAudioModules(); refreshMiniPlayerSafely(); }
                    @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
                        android.util.Log.w("VgramAudioService", "Playback failed; keeping the app alive", error);
                        refreshMiniPlayerSafely();
                    }
                });
            } catch (RuntimeException error) {
                player = null;
                android.util.Log.e("VgramAudioService", "Unable to initialize ExoPlayer", error);
                throw error;
            }
        }
        return player;
    }

    public static VgramStereoPanAudioProcessor getStereoPanAudioProcessor() {
        return renderersFactory == null ? null : renderersFactory.getStereoPanProcessor();
    }

    public static void setHapticBassEnabled(Context context, boolean enabled) {
        AndroidUtilities.runOnUIThread(() -> {
            if (context != null) {
                hapticSubwoofer = VgramHapticSubwoofer.get(context.getApplicationContext());
                hapticSubwoofer.setEnabled(enabled);
            }
            if (player != null) syncAudioModules();
        });
    }

    private static void syncAudioModules() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            AndroidUtilities.runOnUIThread(VgramAudioService::syncAudioModules);
            return;
        }
        if (player == null || appContext == null) return;
        try {
            VgramSoundLab.get(appContext).connect(appContext, player);
            VgramSoundLab.get(appContext).setStereoPanAudioProcessor(getStereoPanAudioProcessor());
            boolean hapticEnabled = appContext.getSharedPreferences("vgram_ui", Context.MODE_PRIVATE)
                    .getBoolean("haptic_bass", false);
            if (hapticSubwoofer == null && hapticEnabled) hapticSubwoofer = VgramHapticSubwoofer.get(appContext);
            if (hapticSubwoofer != null) {
                hapticSubwoofer.setEnabled(hapticEnabled);
                if (hapticEnabled && player.isPlaying()) hapticSubwoofer.start(player);
                else hapticSubwoofer.stop();
            }
        } catch (RuntimeException error) {
            android.util.Log.w("VgramAudioService", "Audio module synchronization failed", error);
        }
    }

    private static void refreshMiniPlayerSafely() {
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) refreshMiniPlayer();
            else AndroidUtilities.runOnUIThread(VgramAudioService::refreshMiniPlayer);
        } catch (RuntimeException error) {
            android.util.Log.w("VgramAudioService", "Unable to refresh mini-player UI", error);
        }
    }
    private static void installLaunchActivityMiniPlayerHook(Context context) {
        if (lifecycleHookInstalled || !(context instanceof Application)) return;
        lifecycleHookInstalled = true;
        ((Application) context).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) { }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityResumed(Activity activity) {
                if (activity instanceof LaunchActivity) {
                    android.view.View content = activity.findViewById(android.R.id.content);
                    if (content instanceof FrameLayout) attachMiniPlayer(activity, (FrameLayout) content);
                }
            }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
            @Override public void onActivityDestroyed(Activity activity) {
                if (activity instanceof LaunchActivity && miniBar != null && miniBar.getParent() instanceof android.view.ViewGroup) {
                    ((android.view.ViewGroup) miniBar.getParent()).removeView(miniBar);
                    miniBar = null; titleView = null; playView = null; artworkView = null; miniVisualizer = null;
                }
            }
        });
    }

    public static void setTrack(Context context, String trackTitle, String stream, String coverUrl) {
        final Context safeContext = context == null ? appContext : context.getApplicationContext();
        final String safeStream = stream == null ? "" : stream.trim();
        if (safeStream.isEmpty()) {
            android.util.Log.w("VgramAudioService", "Ignoring track with an empty stream URL");
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                ExoPlayer p = getPlayerOnUiThread(safeContext);
                MediaItem item = MediaItem.fromUri(safeStream);
                p.setMediaItem(item);
                title = trackTitle == null ? "" : trackTitle;
                artworkUrl = coverUrl == null ? "" : coverUrl;
                p.prepare();
                p.play();
                refreshMiniPlayerSafely();
            } catch (RuntimeException error) {
                android.util.Log.e("VgramAudioService", "Unable to start or switch track", error);
                refreshMiniPlayerSafely();
            }
        });
    }

    public static void toggle(Context context) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                ExoPlayer p = getPlayerOnUiThread(context);
                if (p.getMediaItemCount() == 0) return;
                if (p.isPlaying()) p.pause(); else p.play();
                refreshMiniPlayerSafely();
            } catch (RuntimeException error) {
                android.util.Log.e("VgramAudioService", "Unable to toggle playback", error);
            }
        });
    }

    public static void stop(Context context) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                ExoPlayer p = getPlayerOnUiThread(context);
                p.stop();
                p.clearMediaItems();
                title = "";
                artworkUrl = "";
                refreshMiniPlayerSafely();
            } catch (RuntimeException error) {
                android.util.Log.w("VgramAudioService", "Unable to stop playback cleanly", error);
            }
        });
    }

    public static boolean hasTrack() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try { return player != null && player.getMediaItemCount() > 0; }
            catch (RuntimeException error) { return false; }
        }
        final boolean[] result = new boolean[1];
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        AndroidUtilities.runOnUIThread(() -> {
            try { result[0] = player != null && player.getMediaItemCount() > 0; }
            catch (RuntimeException ignored) { result[0] = false; }
            finally { latch.countDown(); }
        });
        try { return latch.await(2, java.util.concurrent.TimeUnit.SECONDS) && result[0]; }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
    }

    public static synchronized String getTitle() { return title; }
    public static synchronized String getArtworkUrl() { return artworkUrl; }

    /** Adds a lightweight persistent mini-player to the activity's root overlay. */
    public static synchronized void attachMiniPlayer(Activity activity, FrameLayout root) {
        // VGRAM Player and its persistent mini-player are disabled.
    }

    private static GradientDrawable background() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xf126292e);
        drawable.setCornerRadius(AndroidUtilities.dp(14));
        drawable.setStroke(AndroidUtilities.dp(1), 0x55d6b65d);
        return drawable;
    }

    private static synchronized void refreshMiniPlayer() {
        if (miniBar == null) return;
        boolean active = player != null && player.getMediaItemCount() > 0;
        if (active && miniBar.getVisibility() != View.VISIBLE) {
            miniBar.setAlpha(0f);
            miniBar.setTranslationY(AndroidUtilities.dp(12));
            miniBar.setVisibility(View.VISIBLE);
            miniBar.animate().alpha(1f).translationY(0f).setDuration(240).start();
        } else if (!active) {
            miniBar.animate().alpha(0f).translationY(AndroidUtilities.dp(12)).setDuration(160).withEndAction(() -> {
                if (miniBar != null && (player == null || player.getMediaItemCount() == 0)) miniBar.setVisibility(View.GONE);
            }).start();
        }
        if (titleView != null) titleView.setText(active && !title.isEmpty() ? title : "VGRAM Player");
        if (playView != null) playView.setText(player != null && player.isPlaying() ? "Ⅱ" : "▶");
        if (miniVisualizer != null) miniVisualizer.setPlaying(active && player != null && player.isPlaying());
        if (artworkView != null) {
            String requestedArtwork = active ? artworkUrl : "";
            artworkView.setContentDescription(active ? "Обложка: " + title : "VGRAM Player");
            if (requestedArtwork.isEmpty()) {
                loadedArtworkUrl = "";
                artworkView.setImageResource(android.R.drawable.ic_media_play);
            } else if (!requestedArtwork.equals(loadedArtworkUrl)) {
                loadedArtworkUrl = requestedArtwork;
                artworkView.setImageResource(android.R.drawable.ic_media_play);
                final ImageView target = artworkView;
                final String expectedUrl = requestedArtwork;
                artworkExecutor.execute(() -> {
                    Bitmap bitmap = null;
                    HttpURLConnection connection = null;
                    try {
                        connection = (HttpURLConnection) new URL(expectedUrl).openConnection();
                        connection.setConnectTimeout(5000);
                        connection.setReadTimeout(7000);
                        connection.setInstanceFollowRedirects(true);
                        try (InputStream input = connection.getInputStream()) {
                            bitmap = BitmapFactory.decodeStream(input);
                        }
                    } catch (Exception ignored) {
                    } finally {
                        if (connection != null) connection.disconnect();
                    }
                    final Bitmap decoded = bitmap;
                    if (decoded != null) {
                        mainHandler.post(() -> {
                            if (target == artworkView && expectedUrl.equals(artworkUrl) && !target.isAttachedToWindow()) {
                                return;
                            }
                            if (target == artworkView && expectedUrl.equals(artworkUrl)) {
                                target.setImageBitmap(decoded);
                            } else {
                                decoded.recycle();
                            }
                        });
                    }
                });
            }
        }
    }
}
