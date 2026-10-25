package org.telegram.ui;

import android.content.Context;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Process-wide facade for VGRAM audio playback. VgramAudioService owns the
 * application-context ExoPlayer; this manager exposes a stable track/state API
 * and notifies both the full player and persistent mini-player.
 */
public final class VgramPlaybackManager {
    public interface PlayerListener {
        default void onTrackChanged(Track track) {}
        default void onPlaybackStateChanged(boolean isPlaying, boolean buffering) {}
        default void onPlayerError(String message) {}
    }

    public static final class Track {
        public final String title;
        public final String artist;
        public final String streamUrl;
        public final String artworkUrl;

        public Track(String title, String artist, String streamUrl, String artworkUrl) {
            this.title = safe(title);
            this.artist = safe(artist);
            this.streamUrl = safe(streamUrl);
            this.artworkUrl = safe(artworkUrl);
        }

        public String displayTitle() {
            return artist.isEmpty() ? title : title + " — " + artist;
        }

        private static String safe(String value) { return value == null ? "" : value.trim(); }
    }

    private static volatile VgramPlaybackManager instance;
    private final Context appContext;
    private final ExoPlayer player;
    private final Set<PlayerListener> listeners = new CopyOnWriteArraySet<>();
    private volatile Track currentTrack;
    private volatile boolean buffering;

    private final Player.Listener engineListener = new Player.Listener() {
        @Override public void onIsPlayingChanged(boolean isPlaying) {
            notifyPlaybackState();
        }
        @Override public void onPlaybackStateChanged(int state) {
            buffering = state == Player.STATE_BUFFERING;
            notifyPlaybackState();
        }
        @Override public void onMediaItemTransition(MediaItem item, int reason) {
            notifyTrackChanged();
        }
        @Override public void onPlayerError(PlaybackException error) {
            String message = error == null ? "Не удалось воспроизвести поток" : error.getErrorCodeName();
            for (PlayerListener listener : listeners) listener.onPlayerError(message);
            notifyPlaybackState();
        }
    };

    private VgramPlaybackManager(Context context) {
        appContext = context.getApplicationContext();
        player = VgramAudioService.getPlayer(appContext);
        currentTrack = VgramAudioService.hasTrack()
                ? new Track(VgramAudioService.getTitle(), "", "", VgramAudioService.getArtworkUrl()) : null;
        player.addListener(engineListener);
    }

    public static VgramPlaybackManager get(Context context) {
        VgramPlaybackManager result = instance;
        if (result == null) {
            synchronized (VgramPlaybackManager.class) {
                result = instance;
                if (result == null) instance = result = new VgramPlaybackManager(context);
            }
        }
        return result;
    }

    public ExoPlayer getPlayer() { return player; }
    public Track getCurrentTrack() { return currentTrack; }
    public boolean isPlaying() { return player.isPlaying(); }
    public boolean isBuffering() { return buffering; }

    public void addListener(PlayerListener listener) {
        if (listener == null) return;
        listeners.add(listener);
        Track track = currentTrack;
        listener.onTrackChanged(track);
        listener.onPlaybackStateChanged(player.isPlaying(), buffering);
    }

    public void removeListener(PlayerListener listener) { if (listener != null) listeners.remove(listener); }

    public void play(Track track) {
        if (track == null || track.streamUrl.isEmpty()) {
            for (PlayerListener listener : listeners) listener.onPlayerError("У трека нет доступного потокового URL");
            return;
        }
        currentTrack = track;
        VgramAudioService.setTrack(appContext, track.displayTitle(), track.streamUrl, track.artworkUrl);
        notifyTrackChanged();
        notifyPlaybackState();
    }

    public void togglePlayPause() {
        if (player.getMediaItemCount() == 0) return;
        if (player.isPlaying()) player.pause(); else player.play();
        notifyPlaybackState();
    }

    public void seekTo(long ms) { player.seekTo(Math.max(0L, ms)); }

    public void stop() {
        VgramAudioService.stop(appContext);
        currentTrack = null;
        buffering = false;
        notifyTrackChanged();
        notifyPlaybackState();
    }

    private void notifyTrackChanged() {
        Track track = currentTrack;
        for (PlayerListener listener : listeners) listener.onTrackChanged(track);
    }

    private void notifyPlaybackState() {
        boolean playing = player.isPlaying();
        boolean isBuffering = buffering;
        for (PlayerListener listener : listeners) listener.onPlaybackStateChanged(playing, isBuffering);
    }
}
