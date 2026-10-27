package org.telegram.ui;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/** Process-wide playback facade. The service owns the application-scoped player and mini-player. */
public final class VgramPlaybackManager {
    public static final class VgramTrack {
        public final String id, title, artist, streamUrl, coverUrl;
        public final long durationMs;
        public VgramTrack(String id, String title, String artist, String streamUrl, String coverUrl, long durationMs) {
            this.id = safe(id); this.title = safe(title); this.artist = safe(artist);
            this.streamUrl = safe(streamUrl); this.coverUrl = safe(coverUrl);
            this.durationMs = Math.max(0L, durationMs);
        }
        public String displayTitle() { return artist.isEmpty() ? title : title + " — " + artist; }
        private static String safe(String value) { return value == null ? "" : value.trim(); }
    }

    /** Kept as a source-compatible alias for early VGRAM player screens. */
    public static final class Track {
        public final String title, artist, streamUrl, artworkUrl;
        public Track(String title, String artist, String streamUrl, String artworkUrl) {
            this.title = safe(title); this.artist = safe(artist); this.streamUrl = safe(streamUrl); this.artworkUrl = safe(artworkUrl);
        }
        private static String safe(String value) { return value == null ? "" : value.trim(); }
        VgramTrack asVgramTrack() { return new VgramTrack(streamUrl, title, artist, streamUrl, artworkUrl, 0L); }
    }

    public interface PlaybackListener {
        default void onStateChanged(boolean isPlaying) {}
        default void onTrackChanged(VgramTrack track) {}
        default void onProgress(long currentMs, long totalMs) {}
        default void onError(String message) {}
    }
    /** Compatibility with the initial manager API. */
    public interface PlayerListener {
        default void onTrackChanged(Track track) {}
        default void onPlaybackStateChanged(boolean isPlaying, boolean buffering) {}
        default void onPlayerError(String message) {}
    }

    private static volatile VgramPlaybackManager instance;
    private final Context appContext;
    private final ExoPlayer player;
    private final Set<PlaybackListener> listeners = new CopyOnWriteArraySet<>();
    private final Set<PlayerListener> legacyListeners = new CopyOnWriteArraySet<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile VgramTrack currentTrack;
    private volatile boolean buffering;
    private final Runnable progressTask = new Runnable() {
        @Override public void run() {
            if (currentTrack != null) {
                long d = player.getDuration();
                if (d < 0L) d = currentTrack.durationMs;
                final long total = Math.max(0L, d);
                final long position = Math.max(0L, player.getCurrentPosition());
                for (PlaybackListener listener : listeners) {
                    try { listener.onProgress(position, total); } catch (RuntimeException ignored) {}
                }
                if (player.isPlaying()) main.postDelayed(this, 500L);
            }
        }
    };
    private final Player.Listener engineListener = new Player.Listener() {
        @Override public void onIsPlayingChanged(boolean isPlaying) {
            notifyState();
            main.removeCallbacks(progressTask);
            if (isPlaying) main.post(progressTask);
        }
        @Override public void onPlaybackStateChanged(int state) {
            buffering = state == Player.STATE_BUFFERING;
            notifyState();
            if (state == Player.STATE_READY) notifyProgress();
        }
        @Override public void onMediaItemTransition(MediaItem item, int reason) {
            if (player.getMediaItemCount() == 0) currentTrack = null;
            notifyTrack(); notifyState(); notifyProgress();
        }
        @Override public void onPlayerError(PlaybackException error) {
            String message = error == null || error.getMessage() == null
                    ? "Не удалось воспроизвести аудиопоток" : error.getMessage();
            for (PlaybackListener listener : listeners) {
                try { listener.onError(message); } catch (RuntimeException ignored) {}
            }
            for (PlayerListener listener : legacyListeners) {
                try { listener.onPlayerError(message); } catch (RuntimeException ignored) {}
            }
            notifyState();
        }
    };

    private VgramPlaybackManager(Context context) {
        appContext = context.getApplicationContext();
        player = VgramAudioService.getPlayer(appContext);
        player.addListener(engineListener);
        if (VgramAudioService.hasTrack()) {
            currentTrack = new VgramTrack("", VgramAudioService.getTitle(), "", "", VgramAudioService.getArtworkUrl(), 0L);
        }
    }
    public static VgramPlaybackManager getInstance(Context context) {
        if (context == null) throw new IllegalArgumentException("Context must not be null");
        VgramPlaybackManager value = instance;
        if (value == null) synchronized (VgramPlaybackManager.class) {
            value = instance;
            if (value == null) instance = value = new VgramPlaybackManager(context);
        }
        return value;
    }
    public static VgramPlaybackManager get(Context context) { return getInstance(context); }
    public VgramTrack getCurrentTrack() { return currentTrack; }
    public ExoPlayer getPlayer() { return player; }
    public boolean isPlaying() { return player.isPlaying(); }
    public boolean isBuffering() { return buffering; }
    public long getCurrentPosition() { return Math.max(0L, player.getCurrentPosition()); }
    public long getDuration() { long d = player.getDuration(); return d < 0L ? (currentTrack == null ? 0L : currentTrack.durationMs) : d; }

    public void addListener(PlaybackListener listener) {
        if (listener == null) return;
        listeners.add(listener);
        main.post(() -> {
            if (!listeners.contains(listener)) return;
            try { listener.onTrackChanged(currentTrack); listener.onStateChanged(isPlaying()); listener.onProgress(getCurrentPosition(), getDuration()); }
            catch (RuntimeException ignored) {}
        });
        main.removeCallbacks(progressTask); main.post(progressTask);
    }
    public void removeListener(PlaybackListener listener) { if (listener != null) listeners.remove(listener); }
    public void addListener(PlayerListener listener) {
        if (listener == null) return;
        legacyListeners.add(listener);
        Track t = currentTrack == null ? null : new Track(currentTrack.title, currentTrack.artist, currentTrack.streamUrl, currentTrack.coverUrl);
        listener.onTrackChanged(t); listener.onPlaybackStateChanged(isPlaying(), buffering);
    }
    public void removeListener(PlayerListener listener) { if (listener != null) legacyListeners.remove(listener); }

    public void play(VgramTrack track) {
        if (track == null || track.streamUrl.isEmpty()) { notifyError("У трека нет доступной ссылки на аудиопоток"); return; }
        Uri uri = Uri.parse(track.streamUrl);
        String scheme = uri.getScheme();
        boolean remoteStream = uri.getHost() != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        boolean localMedia = "content".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme);
        if (!remoteStream && !localMedia) {
            notifyError("Источник аудио недоступен"); return;
        }
        currentTrack = track; buffering = true; main.removeCallbacks(progressTask); notifyTrack(); notifyState();
        try { VgramAudioService.setTrack(appContext, track.displayTitle(), track.streamUrl, track.coverUrl); }
        catch (RuntimeException error) { buffering = false; notifyError("Не удалось начать воспроизведение"); notifyState(); }
    }
    public void play(Track track) { if (track == null) { stop(); return; } play(track.asVgramTrack()); }
    public void togglePlayPause() {
        try {
            if (player.getMediaItemCount() == 0) { if (currentTrack != null) play(currentTrack); return; }
            if (player.isPlaying()) player.pause(); else player.play();
            notifyState();
        } catch (RuntimeException error) { notifyError("Не удалось изменить состояние воспроизведения"); }
    }
    public void seekTo(long positionMs) {
        try { long target = Math.max(0L, positionMs); long duration = player.getDuration(); if (duration >= 0L) target = Math.min(target, duration); player.seekTo(target); notifyProgress(); }
        catch (RuntimeException error) { notifyError("Не удалось перемотать аудио"); }
    }
    public void stop() {
        main.removeCallbacks(progressTask); currentTrack = null; buffering = false;
        try { VgramAudioService.stop(appContext); } catch (RuntimeException error) { notifyError("Не удалось остановить воспроизведение"); }
        notifyTrack(); notifyState(); notifyProgress();
    }
    private void notifyTrack() {
        VgramTrack track = currentTrack;
        for (PlaybackListener listener : listeners) try { listener.onTrackChanged(track); } catch (RuntimeException ignored) {}
        Track old = track == null ? null : new Track(track.title, track.artist, track.streamUrl, track.coverUrl);
        for (PlayerListener listener : legacyListeners) try { listener.onTrackChanged(old); } catch (RuntimeException ignored) {}
    }
    private void notifyState() {
        boolean playing = player.isPlaying(), isBuffering = buffering;
        for (PlaybackListener listener : listeners) try { listener.onStateChanged(playing); } catch (RuntimeException ignored) {}
        for (PlayerListener listener : legacyListeners) try { listener.onPlaybackStateChanged(playing, isBuffering); } catch (RuntimeException ignored) {}
    }
    private void notifyProgress() {
        long d = getDuration(), p = getCurrentPosition();
        for (PlaybackListener listener : listeners) try { listener.onProgress(p, d); } catch (RuntimeException ignored) {}
    }
    private void notifyError(String message) {
        for (PlaybackListener listener : listeners) try { listener.onError(message); } catch (RuntimeException ignored) {}
        for (PlayerListener listener : legacyListeners) try { listener.onPlayerError(message); } catch (RuntimeException ignored) {}
    }
}
