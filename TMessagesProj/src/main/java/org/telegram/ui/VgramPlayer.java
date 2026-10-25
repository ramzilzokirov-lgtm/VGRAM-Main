package org.telegram.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * VGRAM streaming player. Search uses the public iTunes Search API and plays its
 * direct preview streams through Media3; no track is downloaded to local storage.
 */
public final class VgramPlayer extends VgramBaseActivity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "VGRAM-MusicSearch");
        thread.setDaemon(true);
        return thread;
    });
    private ExoPlayer player;
    private VgramPlaybackManager playbackManager;
    private EditText search;
    private LinearLayout results;
    private LinearLayout miniPlayer;
    private TextView miniTitle;
    private TextView miniPlay;
    private TextView status;
    private WaveView waveform;
    private Runnable pendingSearch;
    private String currentTitle = "";
    private boolean released;
    private Player.Listener activityPlayerListener;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            playbackManager = VgramPlaybackManager.get(getApplicationContext());
            player = playbackManager.getPlayer();
            currentTitle = VgramAudioService.getTitle();
            activityPlayerListener = new Player.Listener() {
                @Override public void onPlayerError(PlaybackException error) {
                    main.post(() -> showError("Не удалось воспроизвести поток: " + error.getErrorCodeName()));
                }
                @Override public void onIsPlayingChanged(boolean isPlaying) {
                    main.post(() -> {
                        if (waveform != null) waveform.setPlaying(isPlaying);
                        if (miniPlay != null) miniPlay.setText(isPlaying ? "Ⅱ" : "▶");
                    });
                }
            };
            player.addListener(activityPlayerListener);
            buildPlayerUi();
        } catch (RuntimeException error) {
            showError("Не удалось запустить аудиоплеер.");
            finish();
        }
    }

    private void buildPlayerUi() {
        setupScreen("STREAMING AUDIO", "VGRAM Player", "Поиск треков и потоковое воспроизведение превью. Аудиофайл не сохраняется на устройстве.");
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(0xfff3f1e9);
        search.setHintTextColor(0xff999da4);
        search.setHint("Название трека или исполнитель");
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        search.setBackground(panel(0x20ffffff, 0x40ffffff, 18));
        body.addView(search, margin(0, 0, 0, 8));

        status = label("Введите запрос для поиска.", 12, 0xffb5b5b0, false);
        body.addView(status, margin(0, 0, 0, 8));

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        VgramMotion.applyOverscroll(results);
        body.addView(results, new LinearLayout.LayoutParams(-1, -2));

        waveform = new WaveView();
        body.addView(waveform, margin(0, dp(8), 0, dp(6)));

        miniPlayer = new LinearLayout(this);
        miniPlayer.setGravity(Gravity.CENTER_VERTICAL);
        miniPlayer.setPadding(dp(12), dp(8), dp(10), dp(8));
        miniPlayer.setBackground(panel(0x20ffffff, 0x40ffffff, 18));
        miniTitle = label("Выберите трек", 13, 0xfff1efe8, true);
        miniTitle.setSingleLine(true);
        miniTitle.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        miniTitle.setMarqueeRepeatLimit(-1);
        miniTitle.setSelected(true);
        miniPlayer.addView(miniTitle, new LinearLayout.LayoutParams(0, dp(40), 1));
        miniPlay = label("▶", 20, accent(), true);
        miniPlay.setGravity(Gravity.CENTER);
        miniPlayer.addView(miniPlay, new LinearLayout.LayoutParams(dp(44), dp(42)));
        TextView close = label("×", 23, 0xffd5d7dc, true);
        close.setGravity(Gravity.CENTER);
        miniPlayer.addView(close, new LinearLayout.LayoutParams(dp(38), dp(42)));
        body.addView(miniPlayer, margin(0, dp(8), 0, 0));
        VgramMotion.applySpringTouch(miniPlay);
        VgramMotion.applySpringTouch(close);
        VgramMotion.applySpringTouch(miniPlayer);
        miniPlay.setOnClickListener(v -> { v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); togglePlayback(); });
        close.setOnClickListener(v -> stopPlayback());
        miniPlayer.setOnClickListener(v -> {
            body.setAlpha(0.7f);
            body.animate().alpha(1f).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingSearch != null) main.removeCallbacks(pendingSearch);
                final String query = s.toString().trim();
                if (query.isEmpty()) {
                    results.removeAllViews();
                    status.setText("Введите запрос для поиска.");
                    return;
                }
                pendingSearch = () -> searchTracks(query);
                main.postDelayed(pendingSearch, 500);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        bindSpring(miniPlay);
        bindSpring(close);
    }

    private void searchTracks(String query) {
        if (released) return;
        status.setText("Ищем треки…");
        network.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
                URL url = new URL("https://itunes.apple.com/search?media=music&entity=song&limit=25&term=" + encoded);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("Accept", "application/json");
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                }
                JSONObject response = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
                JSONArray tracks = response.optJSONArray("results");
                main.post(() -> renderTracks(tracks));
            } catch (Exception error) {
                main.post(() -> {
                    status.setText("");
                    showEmptyState("Нет соединения", "Проверьте интернет и повторите поиск.");
                    showError("Ошибка сетевого поиска. Проверьте подключение.");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void renderTracks(JSONArray tracks) {
        if (released || results == null) return;
        results.removeAllViews();
        if (tracks == null || tracks.length() == 0) {
            status.setText("");
            showEmptyState("Ничего не найдено", "Попробуйте другое название или имя исполнителя.");
            return;
        }
        status.setText("Найдено: " + tracks.length() + " · потоковые превью");
        for (int i = 0; i < tracks.length(); i++) {
            JSONObject track = tracks.optJSONObject(i);
            if (track == null) continue;
            String title = track.optString("trackName", "Без названия");
            String artist = track.optString("artistName", "Неизвестный исполнитель");
            String stream = track.optString("previewUrl", "");
            String artwork = track.optString("artworkUrl100", "");
            long duration = track.optLong("trackTimeMillis", 0);
            if (stream.isEmpty()) continue;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(9), dp(12), dp(9));
            row.setBackground(panel(0x182f3339, 0x28ffffff, 16));
            LinearLayout textColumn = new LinearLayout(this);
            textColumn.setOrientation(LinearLayout.VERTICAL);
            TextView trackName = label(title, 14, 0xfff3f1e9, true);
            trackName.setMaxLines(2);
            TextView trackArtist = label(artist + " · " + formatDuration(duration), 12, 0xffa7abb2, false);
            textColumn.addView(trackName);
            textColumn.addView(trackArtist, margin(0, dp(3), 0, 0));
            row.addView(textColumn, new LinearLayout.LayoutParams(0, -2, 1));
            TextView play = label("▶", 20, accent(), true);
            play.setGravity(Gravity.CENTER);
            row.addView(play, new LinearLayout.LayoutParams(dp(42), dp(42)));
            results.addView(row, margin(0, 0, 0, dp(7)));
            View.OnClickListener start = v -> playTrack(title, artist, stream, artwork, row);
            VgramMotion.applySpringTouch(row);
            VgramMotion.applySpringTouch(play);
            row.setOnClickListener(start);
            play.setOnClickListener(start);
            bindSpring(row);
            bindSpring(play);
        }
    }

    private void playTrack(String title, String artist, String stream, String artwork, View row) {
        if (player == null || released) {
            showError("Аудиоплеер недоступен.");
            return;
        }
        if (stream == null || stream.trim().isEmpty()) {
            showError("У трека нет доступного потокового URL.");
            return;
        }
        try {
            currentTitle = title + " — " + artist;
            miniTitle.setText(currentTitle);
            miniTitle.setSelected(true);
            playbackManager.play(new VgramPlaybackManager.Track(title, artist, stream, artwork));
            player = playbackManager.getPlayer();
            waveform.setPlaying(true);
            row.setTranslationX(dp(10));
            row.animate().translationX(0).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
        } catch (RuntimeException error) {
            showError("Не удалось открыть аудиопоток.");
        }
    }

    private void togglePlayback() {
        if (player == null || released || player.getMediaItemCount() == 0) {
            showError("Сначала выберите трек.");
            return;
        }
        playbackManager.togglePlayPause();
    }

    private void stopPlayback() {
        if (playbackManager != null && !released) playbackManager.stop();
        currentTitle = "";
        miniTitle.setText("Выберите трек");
        miniPlay.setText("▶");
        if (waveform != null) waveform.setPlaying(false);
    }

    private void showEmptyState(String heading, String detail) {
        if (results == null || isFinishing() || isDestroyed()) return;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(20), dp(22), dp(20), dp(22));
        card.setBackground(panel(0x242f3339, 0x55d6b65d, 20));
        TextView icon = label("♫", 30, accent(), true);
        icon.setGravity(Gravity.CENTER);
        TextView title = label(heading, 16, 0xfff3f1e9, true);
        title.setGravity(Gravity.CENTER);
        TextView message = label(detail, 13, 0xffb5b5b0, false);
        message.setGravity(Gravity.CENTER);
        message.setMaxLines(3);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(-1, -2);
        textLp.topMargin = dp(6);
        card.addView(icon);
        card.addView(title, textLp);
        card.addView(message, textLp);
        TextView retry = label("Повторить поиск", 13, accent(), true);
        retry.setGravity(Gravity.CENTER);
        retry.setPadding(dp(14), dp(10), dp(14), dp(10));
        retry.setBackground(panel(0x30d6b65d, 0x66d6b65d, 14));
        LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(-2, -2);
        retryLp.topMargin = dp(14);
        card.addView(retry, retryLp);
        VgramMotion.applySpringTouch(retry);
        retry.setOnClickListener(v -> {
            String query = search == null ? "" : search.getText().toString().trim();
            if (query.isEmpty()) {
                if (search != null) search.setError("Введите название трека или исполнителя");
            } else {
                results.removeAllViews();
                searchTracks(query);
            }
        });
        results.addView(card, margin(0, 0, 0, dp(8)));
        VgramMotion.crossfadeIn(card);
    }

    private String formatDuration(long milliseconds) {
        long seconds = Math.max(0, milliseconds / 1000);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private void showError(String message) {
        if (isFinishing() || isDestroyed()) return;
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void bindSpring(View view) {
        view.setOnTouchListener((v, event) -> {
            float target = event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN ? 0.96f : 1f;
            androidx.dynamicanimation.animation.SpringForce force = new androidx.dynamicanimation.animation.SpringForce(target)
                    .setStiffness(androidx.dynamicanimation.animation.SpringForce.STIFFNESS_LOW)
                    .setDampingRatio(androidx.dynamicanimation.animation.SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
            new androidx.dynamicanimation.animation.SpringAnimation(v, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X)
                    .setSpring(force).animateToFinalPosition(target);
            androidx.dynamicanimation.animation.SpringForce forceY = new androidx.dynamicanimation.animation.SpringForce(target)
                    .setStiffness(androidx.dynamicanimation.animation.SpringForce.STIFFNESS_LOW)
                    .setDampingRatio(androidx.dynamicanimation.animation.SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
            new androidx.dynamicanimation.animation.SpringAnimation(v, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y)
                    .setSpring(forceY).animateToFinalPosition(target);
            return false;
        });
    }

    @Override protected void onDestroy() {
        released = true;
        if (pendingSearch != null) main.removeCallbacks(pendingSearch);
        network.shutdownNow();
        if (player != null && activityPlayerListener != null) {
            player.removeListener(activityPlayerListener);
            activityPlayerListener = null;
        }
        player = null;
        super.onDestroy();
    }

    private final class WaveView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean playing;
        private long startedAt;
        WaveView() { super(VgramPlayer.this); setMinimumHeight(dp(38)); }
        void setPlaying(boolean value) {
            if (playing == value) return;
            playing = value;
            startedAt = System.currentTimeMillis();
            invalidate();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int bars = 36;
            float width = getWidth() / (float) (bars * 2);
            float mid = getHeight() / 2f;
            paint.setColor(accent());
            paint.setStrokeWidth(Math.max(dp(2), width));
            paint.setStrokeCap(Paint.Cap.ROUND);
            long time = System.currentTimeMillis() - startedAt;
            for (int i = 0; i < bars; i++) {
                float amplitude = playing ? (float) (0.2 + 0.8 * Math.abs(Math.sin(time / 180.0 + i * 0.67))) : 0.14f;
                float height = dp(4) + amplitude * Math.max(dp(5), getHeight() * 0.43f);
                float x = (i * 2 + 1) * width;
                canvas.drawLine(x, mid - height, x, mid + height, paint);
            }
            if (playing) postInvalidateDelayed(50);
        }
    }
}
