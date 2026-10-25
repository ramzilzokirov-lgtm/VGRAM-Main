package org.telegram.messenger;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Process-wide owner of VGRAM music playback. UI components observe this manager;
 * destroying an Activity must never release the shared player.
 */
public final class VgramPlaybackManager {
    public interface PlayerListener {
        void onPlaybackChanged(@Nullable Track track, boolean isPlaying,
                               boolean isBuffering, long positionMs, long durationMs);
        default void onPlayerError(@NonNull PlaybackException error) { }
    }

    /** Immutable description of a playable audio item. */
    public static final class Track {
        @NonNull public final String id;
        @NonNull public final String title;
        @NonNull public final String artist;
        @NonNull public final Uri uri;
        @Nullable public final Uri artworkUri;

        public Track(@NonNull String id, @NonNull String title, @NonNull String artist,
                     @NonNull Uri uri, @Nullable Uri artworkUri) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.uri = uri;
            this.artworkUri = artworkUri;
        }

        MediaItem toMediaItem() {
            MediaItem.Builder builder = new MediaItem.Builder()
                    .setMediaId(id)
                    .setUri(uri)
                    .setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(title).setArtist(artist)
                            .setArtworkUri(artworkUri).build());
            return builder.build();
        }
    }

    private static volatile VgramPlaybackManager instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArraySet<PlayerListener> listeners = new CopyOnWriteArraySet<>();
    private final ExoPlayer player;
    private final MediaSession mediaSession;
    @Nullable private Track currentTrack;
    private boolean released;

    public static VgramPlaybackManager getInstance(@NonNull Context context) {
        VgramPlaybackManager result = instance;
        if (result == null) {
            synchronized (VgramPlaybackManager.class) {
                result = instance;
                if (result == null) {
                    result = new VgramPlaybackManager(context.getApplicationContext());
                    instance = result;
                }
            }
        }
        return result;
    }

    private VgramPlaybackManager(@NonNull Context appContext) {
        player = new ExoPlayer.Builder(appContext).build();
        mediaSession = new MediaSession.Builder(appContext, player).build();
        player.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean isPlaying) { notifyListeners(); }
            @Override public void onPlaybackStateChanged(int playbackState) { notifyListeners(); }
            @Override public void onMediaItemTransition(@Nullable MediaItem item, int reason) {
                notifyListeners();
            }
            @Override public void onPlayerError(@NonNull PlaybackException error) {
                for (PlayerListener listener : listeners) {
                    listener.onPlayerError(error);
                }
                notifyListeners();
            }
        });
    }

    public void addListener(@NonNull PlayerListener listener) {
        listeners.add(listener);
        mainHandler.post(() -> dispatchTo(listener));
    }

    public void removeListener(@NonNull PlayerListener listener) {
        listeners.remove(listener);
    }

    public void play(@NonNull Track track) {
        runOnMain(() -> {
            if (released) return;
            boolean sameTrack = currentTrack != null && currentTrack.id.equals(track.id);
            currentTrack = track;
            if (!sameTrack) {
                player.setMediaItem(track.toMediaItem());
                player.prepare();
            }
            player.play();
            notifyListeners();
        });
    }

    public void togglePlayPause() {
        runOnMain(() -> {
            if (released || currentTrack == null) return;
            if (player.isPlaying()) player.pause();
            else {
                if (player.getPlaybackState() == Player.STATE_IDLE) player.prepare();
                player.play();
            }
            notifyListeners();
        });
    }

    public void seekTo(long ms) {
        runOnMain(() -> {
            if (!released && currentTrack != null) {
                player.seekTo(Math.max(0L, ms));
                notifyListeners();
            }
        });
    }

    /** Stops playback and clears the active item; does not destroy the singleton. */
    public void stop() {
        runOnMain(() -> {
            if (released) return;
            player.stop();
            player.clearMediaItems();
            currentTrack = null;
            notifyListeners();
        });
    }

    @Nullable public Track getCurrentTrack() { return currentTrack; }
    public boolean isPlaying() { return player.isPlaying(); }
    public boolean isBuffering() { return player.getPlaybackState() == Player.STATE_BUFFERING; }
    public long getPositionMs() { return player.getCurrentPosition(); }
    public long getDurationMs() {
        long duration = player.getDuration();
        return duration == androidx.media3.common.C.TIME_UNSET ? 0L : Math.max(0L, duration);
    }
    @NonNull public ExoPlayer getPlayer() { return player; }
    @NonNull public MediaSession getMediaSession() { return mediaSession; }

    private void notifyListeners() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::notifyListeners);
            return;
        }
        for (PlayerListener listener : listeners) dispatchTo(listener);
    }

    private void dispatchTo(@NonNull PlayerListener listener) {
        listener.onPlaybackChanged(currentTrack, player.isPlaying(),
                player.getPlaybackState() == Player.STATE_BUFFERING,
                player.getCurrentPosition(), getDurationMs());
    }

    private void runOnMain(@NonNull Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else mainHandler.post(action);
    }
}
