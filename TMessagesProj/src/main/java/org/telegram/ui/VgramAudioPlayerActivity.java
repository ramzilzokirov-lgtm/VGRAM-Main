package org.telegram.ui;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

public class VgramAudioPlayerActivity extends VgramBaseActivity {
    private EditText urlField;
    private TextView status, playButton, trackTitle, emptyTitle, emptyMessage;
    private SeekBar progress;
    private TextView elapsedTime, totalTime;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressTicker = new Runnable() { @Override public void run() { if (playback != null && progress != null && !draggingProgress) playbackListener.onProgress(playback.getCurrentPosition(), playback.getDuration()); progressHandler.postDelayed(this, 200L); } };
    private WaveView wave;
    private VgramPlaybackManager playback;
    private boolean draggingProgress;

    private final VgramPlaybackManager.PlaybackListener playbackListener = new VgramPlaybackManager.PlaybackListener() {
        @Override public void onTrackChanged(VgramPlaybackManager.VgramTrack track) {
            if (track == null) {
                if (trackTitle != null) trackTitle.setText("Ничего не воспроизводится");
                if (status != null) status.setText("Готов к воспроизведению");
                return;
            }
            if (trackTitle != null) trackTitle.setText(track.displayTitle());
            if (urlField != null && !track.streamUrl.isEmpty() && !track.streamUrl.equals(urlField.getText().toString())) urlField.setText(track.streamUrl);
            if (status != null) status.setText("Трек выбран");
            if (emptyTitle != null) emptyTitle.setText("");
            if (emptyMessage != null) emptyMessage.setText("");
        }
        @Override public void onStateChanged(boolean isPlaying) {
            if (playButton != null) playButton.setText(isPlaying ? "Ⅱ  Пауза" : "▶  Воспроизвести / продолжить");
            if (wave != null) wave.setPlaying(isPlaying);
            if (status != null && playback != null && playback.getCurrentTrack() != null) status.setText(isPlaying ? "Воспроизведение" : playback.isBuffering() ? "Буферизация…" : "Пауза");
        }
        @Override public void onProgress(long currentMs, long totalMs) {
            if (progress == null || draggingProgress) return;
            progress.setMax((int) Math.min(Integer.MAX_VALUE, Math.max(1L, totalMs)));
            progress.setProgress((int) Math.min(progress.getMax(), currentMs));
            if (elapsedTime != null) elapsedTime.setText(formatTime(currentMs));
            if (totalTime != null) totalTime.setText(formatTime(totalMs));
        }
        @Override public void onError(String message) {
            if (status != null) status.setText("Ошибка воспроизведения");
            if (playButton != null) playButton.setText("↻  Повторить");
            if (!isFinishing()) Toast.makeText(VgramAudioPlayerActivity.this, message, Toast.LENGTH_LONG).show();
            showEmptyState("Проверьте подключение к сети", message);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        playback = VgramPlaybackManager.getInstance(getApplicationContext());
        setupScreen("VGRAM AUDIO", "Потоковый плеер", "Воспроизведение продолжается при закрытии этого экрана.");
        LinearLayout c = card("Сейчас играет", "Вставьте прямую HTTP(S)-ссылку на аудиофайл или поток.");
        trackTitle = label("Ничего не воспроизводится", 14, 0xfff1f0eb, true);
        c.addView(trackTitle, margin(0, dp(2), 0, dp(8)));
        wave = new WaveView();
        c.addView(wave, margin(0, dp(8), 0, dp(10)));
        progress = new SeekBar(this);
        progress.setMax(1);
        c.addView(progress, margin(0, 0, 0, dp(2)));
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        elapsedTime = label("00:00", 11, 0xffc5b878, true);
        totalTime = label("00:00", 11, 0xffb9bbc1, false);
        timeRow.addView(elapsedTime, new LinearLayout.LayoutParams(0, -2, 1));
        totalTime.setGravity(Gravity.RIGHT);
        timeRow.addView(totalTime, new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(timeRow, margin(0, 0, 0, dp(8)));
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) { if (fromUser && elapsedTime != null) elapsedTime.setText(formatTime(value)); }
            @Override public void onStartTrackingTouch(SeekBar bar) { draggingProgress = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                draggingProgress = false;
                playback.seekTo(bar.getProgress());
            }
        });
        urlField = new EditText(this);
        urlField.setSingleLine(true);
        urlField.setTextColor(0xfff1f0eb);
        urlField.setHintTextColor(0xff9b9da3);
        urlField.setHint("https://…/audio.mp3");
        urlField.setTextSize(14);
        urlField.setPadding(dp(13), dp(12), dp(13), dp(12));
        urlField.setBackground(panel(0xff202328, 0x667faaa5, 14));
        c.addView(urlField, margin(0, dp(4), 0, dp(10)));
        status = label("Готов к воспроизведению", 12, 0xffb9bbc1, false);
        c.addView(status, margin(0, 0, 0, dp(10)));
        playButton = label("▶  Воспроизвести поток", 14, 0xff17191d, true);
        playButton.setGravity(Gravity.CENTER);
        playButton.setPadding(dp(15), dp(14), dp(15), dp(14));
        playButton.setBackground(panel(accent(), 0x99ffffff, 18));
        c.addView(playButton, new LinearLayout.LayoutParams(-1, -2));
        playButton.setOnClickListener(v -> onPlayPressed());
        body.addView(c, margin(0, 0, 0, dp(12)));

        LinearLayout emptyCard = card("Поиск аудио", "Введите прямую ссылку на аудиопоток и нажмите «Повторить поиск».");
        emptyTitle = label("Ничего не найдено", 15, 0xfff1f0eb, true);
        emptyCard.addView(emptyTitle, margin(0, 0, 0, dp(4)));
        emptyMessage = label("Поиск выполняется по прямым HTTP(S)-ссылкам.", 12, 0xffb9bbc1, false);
        emptyCard.addView(emptyMessage, margin(0, 0, 0, dp(10)));
        TextView retry = label("↻  Повторить поиск", 14, 0xff17191d, true);
        retry.setGravity(Gravity.CENTER);
        retry.setPadding(dp(12), dp(12), dp(12), dp(12));
        retry.setBackground(panel(accent(), 0x99ffffff, 16));
        retry.setOnClickListener(v -> searchAndPlay());
        VgramMotion.applySpringTouch(retry);
        emptyCard.addView(retry, new LinearLayout.LayoutParams(-1, -2));
        body.addView(emptyCard, margin(0, 0, 0, dp(12)));
        body.addView(card("Поддержка источников", "Плеер принимает прямые HTTP(S)-ссылки на аудиопотоки. Страницы SoundCloud и Spotify не являются прямыми аудиопотоками."), margin(0, 0, 0, dp(12)));
        VgramMotion.applySpringTouch(playButton);
        renderCurrentTrack();
    }

    private void onPlayPressed() {
        if (playback.getCurrentTrack() != null && playback.getPlayer().getMediaItemCount() > 0) {
            playback.togglePlayPause();
        } else {
            searchAndPlay();
        }
    }

    private void searchAndPlay() {
        String raw = urlField == null || urlField.getText() == null ? "" : urlField.getText().toString().trim();
        Uri uri = Uri.parse(raw);
        String scheme = uri.getScheme();
        if (uri.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            showEmptyState("Ничего не найдено", "Укажите полную ссылку HTTP(S) на прямой аудиопоток.");
            return;
        }
        status.setText("Подключение к потоку…");
        playback.play(new VgramPlaybackManager.VgramTrack(raw, raw, "", raw, "", 0L));
    }

    private void showEmptyState(String title, String message) {
        if (emptyTitle != null) emptyTitle.setText(title);
        if (emptyMessage != null) emptyMessage.setText(message);
    }

    private void renderCurrentTrack() {
        VgramPlaybackManager.VgramTrack track = playback.getCurrentTrack();
        if (track != null) {
            trackTitle.setText(track.displayTitle());
            if (!track.streamUrl.isEmpty()) urlField.setText(track.streamUrl);
        }
        playbackListener.onStateChanged(playback.isPlaying());
        playbackListener.onProgress(playback.getCurrentPosition(), playback.getDuration());
    }

    @Override protected void onResume() {
        super.onResume();
        if (playback == null) playback = VgramPlaybackManager.getInstance(getApplicationContext());
        playback.addListener(playbackListener);
        progressHandler.removeCallbacks(progressTicker);
        progressHandler.post(progressTicker);
    }

    @Override protected void onPause() {
        if (playback != null) playback.removeListener(playbackListener);
        progressHandler.removeCallbacks(progressTicker);
        super.onPause();
    }

    @Override protected void onDestroy() {
        // Deliberately do not stop/release playback: the manager owns the player.
        if (playback != null) playback.removeListener(playbackListener);
        progressHandler.removeCallbacks(progressTicker);
        super.onDestroy();
    }

    private static String formatTime(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        return String.format(java.util.Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    private final class WaveView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private boolean playing;
        private long start = System.currentTimeMillis();
        WaveView() { super(VgramAudioPlayerActivity.this); setLayerType(View.LAYER_TYPE_HARDWARE, null); }
        @Override protected void onMeasure(int ws, int hs) { setMeasuredDimension(MeasureSpec.getSize(ws), dp(112)); }
        void setPlaying(boolean value) { playing = value; if (value) start = System.currentTimeMillis(); invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight(), mid = h / 2;
            p.setColor(0x442c3036); r.set(0, 0, w, h); canvas.drawRoundRect(r, dp(18), dp(18), p);
            int n = 44; float gap = dp(3), bw = Math.max(dp(2), (w - gap * (n - 1) - dp(24)) / n);
            float t = (System.currentTimeMillis() - start) / 220f;
            for (int i = 0; i < n; i++) {
                float signal = (float) (Math.sin(i * .49f + (playing ? t : 0)) * .5 + .5);
                float bh = dp(10) + signal * (playing ? dp(65) : dp(28));
                float left = dp(12) + i * (bw + gap);
                r.set(left, mid - bh / 2, left + bw, mid + bh / 2);
                p.setColor(i % 5 == 0 ? accent() : 0xffb8c4c3);
                canvas.drawRoundRect(r, bw, bw, p);
            }
            if (playing) postInvalidateDelayed(45);
        }
    }
}
