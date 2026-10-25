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
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import org.telegram.messenger.AndroidUtilities;

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

    public static synchronized ExoPlayer getPlayer(Context context) {
        if (player == null) {
            appContext = context.getApplicationContext();
            player = new ExoPlayer.Builder(appContext).build();
            installLaunchActivityMiniPlayerHook(context.getApplicationContext());
            player.addListener(new Player.Listener() {
                @Override public void onIsPlayingChanged(boolean isPlaying) { refreshMiniPlayer(); }
                @Override public void onMediaItemTransition(MediaItem item, int reason) { refreshMiniPlayer(); }
                @Override public void onPlaybackStateChanged(int state) { refreshMiniPlayer(); }
            });
        }
        return player;
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
                    miniBar = null; titleView = null; playView = null; artworkView = null;
                }
            }
        });
    }

    public static synchronized void setTrack(Context context, String trackTitle, String stream, String coverUrl) {
        ExoPlayer p = getPlayer(context);
        title = trackTitle == null ? "" : trackTitle;
        artworkUrl = coverUrl == null ? "" : coverUrl;
        p.setMediaItem(MediaItem.fromUri(stream));
        p.prepare();
        p.play();
        refreshMiniPlayer();
    }

    public static synchronized void toggle(Context context) {
        ExoPlayer p = getPlayer(context);
        if (p.getMediaItemCount() == 0) return;
        if (p.isPlaying()) p.pause(); else p.play();
        refreshMiniPlayer();
    }

    public static synchronized void stop(Context context) {
        ExoPlayer p = getPlayer(context);
        p.stop();
        p.clearMediaItems();
        title = "";
        artworkUrl = "";
        refreshMiniPlayer();
    }

    public static synchronized boolean hasTrack() {
        return player != null && player.getMediaItemCount() > 0;
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
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, AndroidUtilities.dp(56), Gravity.BOTTOM);
        lp.leftMargin = AndroidUtilities.dp(12);
        lp.rightMargin = AndroidUtilities.dp(12);
        lp.bottomMargin = AndroidUtilities.dp(76);
        root.addView(miniBar, lp);
        miniBar.setOnClickListener(v -> activity.startActivity(new Intent(activity, VgramPlayer.class)));
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
