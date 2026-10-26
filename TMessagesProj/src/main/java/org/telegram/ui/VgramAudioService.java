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
                    @Override public void onIsPlayingChanged(boolean isPlaying) { refreshMiniPlayerSafely(); }
                    @Override public void onMediaItemTransition(MediaItem item, int reason) { refreshMiniPlayerSafely(); }
                    @Override public void onPlaybackStateChanged(int state) { refreshMiniPlayerSafely(); }
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
        if (activity == null || root == null || activity.isFinishing()) return;
        if (miniBar != null && miniBar.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) miniBar.getParent()).removeView(miniBar);
        }
        LinearLayout bar = new LinearLayout(activity);
        miniBar = new FrameLayout(activity);
        miniBar.setClipToPadding(false);
        miniBar.setBackground(VgramGlassDrawable.card(activity));
        miniBar.setElevation(AndroidUtilities.dp(8));
        miniBar.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6));
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView cover = new ImageView(activity);
        artworkView = cover;
        cover.setImageResource(android.R.drawable.ic_media_play);
        cover.setBackground(background());
        cover.setClipToOutline(true);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(cover, new LinearLayout.LayoutParams(AndroidUtilities.dp(40), AndroidUtilities.dp(40)));
        TextView track = new TextView(activity);
        titleView = track;
        track.setTextColor(0xfff4f0e5);
        track.setTextSize(13);
        track.setSingleLine(true);
        track.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        track.setMarqueeRepeatLimit(-1);
        track.setSelected(true);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1);
        textParams.leftMargin = AndroidUtilities.dp(10);
        textParams.rightMargin = AndroidUtilities.dp(6);
        row.addView(track, textParams);
        TextView play = new TextView(activity);
        playView = play;
        play.setGravity(Gravity.CENTER);
        play.setTextSize(20);
        play.setTextColor(0xffd6b65d);
        row.addView(play, new LinearLayout.LayoutParams(AndroidUtilities.dp(40), AndroidUtilities.dp(40)));
        TextView close = new TextView(activity);
        close.setText("×");
        close.setTextSize(22);
        close.setTextColor(0xffd6d2c8);
        close.setGravity(Gravity.CENTER);
        row.addView(close, new LinearLayout.LayoutParams(AndroidUtilities.dp(34), AndroidUtilities.dp(40)));
        miniBar.addView(row, new FrameLayout.LayoutParams(-1, -1));
        miniVisualizer = new VgramVisualizerView(activity);
        miniVisualizer.setAccentColor(VgramTheme.accent(activity));
        FrameLayout.LayoutParams visualizerParams = new FrameLayout.LayoutParams(AndroidUtilities.dp(32), AndroidUtilities.dp(20), Gravity.CENTER);
        miniBar.addView(miniVisualizer, visualizerParams);
        titleView.setVisibility(View.GONE);
        close.setVisibility(View.GONE);
        cover.setLayoutParams(new LinearLayout.LayoutParams(AndroidUtilities.dp(32), AndroidUtilities.dp(32)));
        play.setLayoutParams(new LinearLayout.LayoutParams(AndroidUtilities.dp(32), AndroidUtilities.dp(32)));
        miniBar.setContentDescription("VGRAM Player — нажмите, чтобы развернуть; удерживайте и перемещайте для смены положения");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(AndroidUtilities.dp(120), AndroidUtilities.dp(48), Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = Math.max(AndroidUtilities.dp(8), root.getWidth() - AndroidUtilities.dp(132));
        lp.topMargin = AndroidUtilities.dp(110);
        root.addView(miniBar, lp);
        final float[] down = new float[2];
        final int[] start = new int[2];
        final boolean[] moved = new boolean[1];
        miniBar.setOnTouchListener((view, event) -> {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) miniBar.getLayoutParams();
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                down[0] = event.getRawX(); down[1] = event.getRawY();
                start[0] = params.leftMargin; start[1] = params.topMargin; moved[0] = false;
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                int dx = Math.round(event.getRawX() - down[0]);
                int dy = Math.round(event.getRawY() - down[1]);
                if (Math.abs(dx) > AndroidUtilities.dp(4) || Math.abs(dy) > AndroidUtilities.dp(4)) moved[0] = true;
                if (moved[0]) {
                    params.leftMargin = Math.max(0, Math.min(root.getWidth() - miniBar.getWidth(), start[0] + dx));
                    params.topMargin = Math.max(0, Math.min(root.getHeight() - miniBar.getHeight(), start[1] + dy));
                    params.gravity = Gravity.TOP | Gravity.LEFT;
                    miniBar.setLayoutParams(params);
                }
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (!moved[0] && event.getAction() == MotionEvent.ACTION_UP) {
                    activity.startActivity(new Intent(activity, VgramPlayer.class));
                    return true;
                }
                int maxX = Math.max(0, root.getWidth() - miniBar.getWidth());
                int maxY = Math.max(0, root.getHeight() - miniBar.getHeight());
                int targetX = params.leftMargin + miniBar.getWidth() / 2 < root.getWidth() / 2 ? AndroidUtilities.dp(8) : Math.max(AndroidUtilities.dp(8), maxX - AndroidUtilities.dp(8));
                int targetY = Math.max(AndroidUtilities.dp(8), Math.min(maxY - AndroidUtilities.dp(8), params.topMargin));
                int fromX = params.leftMargin, fromY = params.topMargin;
                miniBar.animate().cancel();
                android.animation.ValueAnimator snap = android.animation.ValueAnimator.ofFloat(0f, 1f);
                snap.setDuration(280L);
                snap.setInterpolator(new android.view.animation.OvershootInterpolator(1.15f));
                snap.addUpdateListener(anim -> {
                    float t = (Float) anim.getAnimatedValue();
                    FrameLayout.LayoutParams moving = (FrameLayout.LayoutParams) miniBar.getLayoutParams();
                    moving.leftMargin = Math.round(fromX + (targetX - fromX) * t);
                    moving.topMargin = Math.round(fromY + (targetY - fromY) * t);
                    miniBar.setLayoutParams(moving);
                });
                snap.start();
                return true;
            }
            return true;
        });
        play.setOnClickListener(v -> toggle(activity));
        close.setOnClickListener(v -> stop(activity));
        VgramMotion.applySpringTouch(miniBar);
        VgramMotion.applySpringTouch(play);
        VgramMotion.applySpringTouch(close);
        refreshMiniPlayer();
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
