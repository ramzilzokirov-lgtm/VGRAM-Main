package org.telegram.ui;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.UserConfig;
import org.telegram.ui.VgramModules.Audio.VgramVisualizerView;

import java.util.ArrayList;
import java.util.Locale;

public class VgramAudioPlayerActivity extends VgramBaseActivity {
    private static final int REQUEST_AUDIO_PERMISSION = 4817;
    private final ArrayList<VgramPlaybackManager.VgramTrack> allTracks = new ArrayList<>();
    private final ArrayList<VgramPlaybackManager.VgramTrack> visibleTracks = new ArrayList<>();
    private VgramPlaybackManager playback;
    private TrackAdapter adapter;
    private EditText searchField;
    private TextView currentTitle, currentArtist, playButton, elapsed, total, empty;
    private SeekBar progress;
    private VgramVisualizerView visualizer;
    private ImageView blurredCover;
    private LinearLayout root;
    private boolean dragging;
    private int currentIndex = -1;
    private boolean stereoPan;

    private final VgramPlaybackManager.PlaybackListener listener = new VgramPlaybackManager.PlaybackListener() {
        @Override public void onTrackChanged(VgramPlaybackManager.VgramTrack track) {
            if (track == null) {
                currentTitle.setText("Ничего не воспроизводится");
                currentArtist.setText("Выберите трек из медиатеки");
                return;
            }
            currentTitle.setText(track.title);
            currentArtist.setText(track.artist.isEmpty() ? "Неизвестный исполнитель" : track.artist);
            for (int i = 0; i < allTracks.size(); i++) {
                if (allTracks.get(i).id.equals(track.id)) { currentIndex = i; break; }
            }
            if (adapter != null) adapter.notifyDataSetChanged();
        }
        @Override public void onStateChanged(boolean playing) {
            playButton.setText(playing ? "Ⅱ" : "▶");
            visualizer.setPlaying(playing);
        }
        @Override public void onProgress(long now, long duration) {
            if (dragging) return;
            progress.setMax((int) Math.max(1L, Math.min(Integer.MAX_VALUE, duration)));
            progress.setProgress((int) Math.min(progress.getMax(), now));
            elapsed.setText(formatTime(now));
            total.setText(formatTime(duration));
        }
        @Override public void onError(String message) {
            Toast.makeText(VgramAudioPlayerActivity.this, message, Toast.LENGTH_LONG).show();
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        playback = VgramPlaybackManager.getInstance(getApplicationContext());
        setupScreen("VGRAM AUDIO", "Музыкальный центр", "Локальная музыка и поиск в Spotify / SoundCloud.");
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(10), dp(16), dp(16));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xff171326, 0xff0d0c13, 0xff09090e}));

        TextView heroTitle = label("ТВОЯ МУЗЫКА", 22, 0xfff5f1ff, true);
        root.addView(heroTitle, margin(0, dp(2), 0, dp(3)));
        TextView heroSubtitle = label("Ищи треки в сервисах или включай файлы устройства", 12, 0xffaaa4bc, false);
        root.addView(heroSubtitle, margin(0, 0, 0, dp(12)));

        searchField = new EditText(this);
        searchField.setSingleLine(true);
        searchField.setTextColor(0xfff5f1ff);
        searchField.setHintTextColor(0xffaaa4bc);
        searchField.setHint("⌕  Название трека или исполнитель");
        searchField.setTextSize(15);
        searchField.setPadding(dp(18), dp(12), dp(18), dp(12));
        searchField.setBackground(panel(0xff211d32, 0x997c4dff, 24));
        searchField.setCompoundDrawablePadding(dp(8));
        searchField.setElevation(dp(3));
        root.addView(searchField, new LinearLayout.LayoutParams(-1, -2));

        // Native provider shortcuts: search stays inside the user's official Spotify/SoundCloud app
        // when installed, otherwise Android opens the provider's web search page.
        TextView providerCaption = label("ПОИСК В СЕРВИСАХ", 10, 0xffaaa4bc, true);
        root.addView(providerCaption, margin(dp(2), dp(13), 0, dp(2)));
        LinearLayout providers = new LinearLayout(this);
        providers.setOrientation(LinearLayout.HORIZONTAL);
        providers.setGravity(Gravity.CENTER_VERTICAL);
        TextView spotifySearch = providerChip("♫   Spotify", "Поиск треков в Spotify", 0xff1ed760);
        TextView soundCloudSearch = providerChip("☁   SoundCloud", "Поиск треков в SoundCloud", 0xffff7a24);
        LinearLayout.LayoutParams providerParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        providerParams.topMargin = dp(9);
        providerParams.rightMargin = dp(6);
        providers.addView(spotifySearch, providerParams);
        LinearLayout.LayoutParams cloudParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        cloudParams.topMargin = dp(9);
        cloudParams.leftMargin = dp(6);
        providers.addView(soundCloudSearch, cloudParams);
        root.addView(providers);
        VgramMotion.applySpringTouch(spotifySearch);
        VgramMotion.applySpringTouch(soundCloudSearch);
        spotifySearch.setOnClickListener(v -> openProviderSearch("spotify"));
        soundCloudSearch.setOnClickListener(v -> openProviderSearch("soundcloud"));

        FrameLayout playerCard = new FrameLayout(this);
        playerCard.setBackground(panel(0xff211b35, 0x667c4dff, 26));
        playerCard.setElevation(dp(5));
        LinearLayout playerContent = new LinearLayout(this);
        playerContent.setOrientation(LinearLayout.VERTICAL);
        playerContent.setPadding(dp(18), dp(17), dp(18), dp(16));
        blurredCover = new ImageView(this);
        blurredCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        blurredCover.setAlpha(0.12f);
        if (Build.VERSION.SDK_INT >= 31) blurredCover.setRenderEffect(RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP));
        playerCard.addView(blurredCover, new FrameLayout.LayoutParams(-1, -1));
        playerCard.addView(playerContent, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        currentTitle = label("Ничего не воспроизводится", 17, 0xfff5f1ff, true);
        currentArtist = label("Выберите трек из медиатеки", 13, 0xffb7b1c8, false);
        playerContent.addView(currentTitle);
        playerContent.addView(currentArtist, margin(0, dp(3), 0, dp(8)));
        visualizer = new VgramVisualizerView(this);
        playerContent.addView(visualizer, new LinearLayout.LayoutParams(-1, dp(58)));
        progress = new SeekBar(this);
        progress.setProgressTintList(ColorStateList.valueOf(0xffb69aff));
        progress.setThumbTintList(ColorStateList.valueOf(0xffd4c3ff));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(0xff454052));
        playerContent.addView(progress, margin(0, dp(4), 0, 0));
        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        elapsed = label("00:00", 11, 0xffc7c1d6, false);
        total = label("00:00", 11, 0xffc7c1d6, false);
        times.addView(elapsed, new LinearLayout.LayoutParams(0, -2, 1));
        total.setGravity(Gravity.RIGHT);
        times.addView(total, new LinearLayout.LayoutParams(0, -2, 1));
        playerContent.addView(times);
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) { if (fromUser) elapsed.setText(formatTime(value)); }
            public void onStartTrackingTouch(SeekBar bar) { dragging = true; }
            public void onStopTrackingTouch(SeekBar bar) { dragging = false; playback.seekTo(bar.getProgress()); }
        });

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(8), 0, dp(4));
        TextView prev = control("⏮", 18);
        playButton = control("▶", 22);
        playButton.setTextColor(Color.WHITE);
        playButton.setBackground(panel(0xff8057e8, 0xffb69aff, 28));
        playButton.setElevation(dp(4));
        TextView next = control("⏭", 18);
        TextView stereo = control("STEREO  OFF", 11);
        controls.addView(prev, controlParams());
        controls.addView(playButton, controlParams());
        controls.addView(next, controlParams());
        controls.addView(stereo, controlParams());
        playerContent.addView(controls);
        VgramMotion.applySpringTouch(prev);
        VgramMotion.applySpringTouch(playButton);
        VgramMotion.applySpringTouch(next);
        VgramMotion.applySpringTouch(stereo);
        prev.setOnClickListener(v -> playRelative(-1));
        next.setOnClickListener(v -> playRelative(1));
        playButton.setOnClickListener(v -> playback.togglePlayPause());
        stereo.setOnClickListener(v -> {
            stereoPan = !stereoPan;
            stereo.setText(stereoPan ? "STEREO  ON" : "STEREO  OFF");
            stereo.setTextColor(stereoPan ? 0xffc8aaff : 0xffb7b1c8);
            org.telegram.ui.VgramModules.Audio.VgramStereoPanAudioProcessor processor = VgramAudioService.getStereoPanAudioProcessor();
            if (processor != null) { if (stereoPan) processor.setPhase(0.5f); else processor.resetPan(); }
        });
        root.addView(playerCard, margin(0, dp(12), 0, dp(10)));

        LinearLayout libraryHeader = new LinearLayout(this);
        libraryHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView section = label("На устройстве", 17, 0xfff5f1ff, true);
        TextView localTag = label("LOCAL", 10, 0xffc8aaff, true);
        localTag.setGravity(Gravity.CENTER);
        localTag.setPadding(dp(9), dp(5), dp(9), dp(5));
        localTag.setBackground(panel(0x227c4dff, 0x557c4dff, 14));
        libraryHeader.addView(section, new LinearLayout.LayoutParams(0, -2, 1));
        libraryHeader.addView(localTag, new LinearLayout.LayoutParams(-2, -2));
        root.addView(libraryHeader, margin(0, dp(5), 0, dp(8)));
        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setClipToPadding(false);
        list.setPadding(0, dp(2), 0, dp(8));
        list.setBackgroundColor(Color.TRANSPARENT);
        adapter = new TrackAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        empty = label("Ищем музыку на устройстве…", 13, 0xffb7b1c8, false);
        empty.setGravity(Gravity.CENTER);
        root.addView(empty, new LinearLayout.LayoutParams(-1, dp(44)));
        body.addView(root, new LinearLayout.LayoutParams(-1, -1));
        root.setAlpha(0f);
        root.setTranslationY(dp(14));
        root.animate().alpha(1f).translationY(0f).setDuration(320L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();

        searchField.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { filterTracks(s.toString()); }
            public void afterTextChanged(Editable e) {}
        });
        if (hasAudioPermission()) loadLocalTracks(); else requestAudioPermission();
        playback.addListener(listener);
    }

    private boolean hasAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= 33 ? "android.permission.READ_MEDIA_AUDIO" : Manifest.permission.READ_EXTERNAL_STORAGE;
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= 33 ? "android.permission.READ_MEDIA_AUDIO" : Manifest.permission.READ_EXTERNAL_STORAGE;
        requestPermissions(new String[]{permission}, REQUEST_AUDIO_PERMISSION);
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_AUDIO_PERMISSION) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) loadLocalTracks();
            else empty.setText("Разрешите доступ к аудио, чтобы искать музыку на устройстве.");
        }
    }

    private void loadLocalTracks() {
        allTracks.clear();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.IS_MUSIC};
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        try (Cursor cursor = getContentResolver().query(collection, projection, selection, null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String title = cursor.getString(titleCol);
                    String artist = cursor.getString(artistCol);
                    long duration = cursor.getLong(durationCol);
                    long album = cursor.getLong(albumCol);
                    Uri uri = ContentUris.withAppendedId(collection, id);
                    Uri cover = Uri.parse("content://media/external/audio/albumart/" + album);
                    allTracks.add(new VgramPlaybackManager.VgramTrack(Long.toString(id),
                            title == null || title.isEmpty() ? "Без названия" : title,
                            artist == null ? "" : artist, uri.toString(), cover.toString(), duration));
                }
            }
        } catch (SecurityException error) {
            empty.setText("Нет разрешения на чтение музыкальных файлов.");
        } catch (RuntimeException error) {
            empty.setText("Не удалось прочитать медиатеку устройства.");
            android.util.Log.w("VgramAudioPlayer", "MediaStore query failed", error);
        }
        filterTracks(searchField.getText().toString());
    }

    private void filterTracks(String query) {
        visibleTracks.clear();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (VgramPlaybackManager.VgramTrack track : allTracks) {
            if (needle.isEmpty() || track.title.toLowerCase(Locale.ROOT).contains(needle)
                    || track.artist.toLowerCase(Locale.ROOT).contains(needle)) visibleTracks.add(track);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        empty.setText(visibleTracks.isEmpty()
                ? (allTracks.isEmpty() ? "Музыка на устройстве не найдена" : "По запросу ничего не найдено")
                : visibleTracks.size() + " треков");
    }

    private void playRelative(int offset) {
        if (allTracks.isEmpty()) return;
        int next = currentIndex < 0 ? 0 : (currentIndex + offset + allTracks.size()) % allTracks.size();
        playTrack(allTracks.get(next));
    }

    private void playTrack(VgramPlaybackManager.VgramTrack track) {
        currentIndex = allTracks.indexOf(track);
        playback.play(track);
    }

    private TextView providerChip(String text, String description, int accent) {
        TextView chip = label(text, 13, 0xfff5f1ff, true);
        chip.setGravity(Gravity.CENTER);
        chip.setContentDescription(description);
        chip.setPadding(dp(10), 0, dp(10), 0);
        chip.setBackground(panel(0xff211d30, (accent & 0x00ffffff) | 0xff000000, 18));
        chip.setElevation(dp(3));
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setMinHeight(dp(44));
        return chip;
    }

    private void openProviderSearch(String provider) {
        String query = searchField == null ? "" : searchField.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "Введите название трека или исполнителя", Toast.LENGTH_SHORT).show();
            searchField.requestFocus();
            return;
        }
        // Search the device library in VGRAM as the user types. Provider buttons are
        // native deep links only; no WebView, HTML, or embedded website is used.
        String encoded = Uri.encode(query);
        Uri appUri = "spotify".equals(provider)
                ? Uri.parse("spotify:search:" + encoded)
                : Uri.parse("soundcloud://search?q=" + encoded);
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, appUri);
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException | SecurityException error) {
            String name = "spotify".equals(provider) ? "Spotify" : "SoundCloud";
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(name + " не найден")
                    .setMessage("Поиск по медиатеке VGRAM работает в поле выше. Чтобы искать каталог " + name
                            + ", установите официальное приложение сервиса и повторите попытку.")
                    .setPositiveButton("Понятно", null)
                    .show();
        }
    }

    private TextView control(String text, int size) {
        TextView v = label(text, size, 0xffe9e3ff, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(10), dp(10), dp(10), dp(10));
        v.setBackground(panel(0x18ffffff, 0x30ffffff, 24));
        VgramMotion.applySpringTouch(v);
        return v;
    }

    private LinearLayout.LayoutParams controlParams() {
        return new LinearLayout.LayoutParams(0, dp(48), 1);
    }

    private static String formatTime(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        return String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    private final class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.Holder> {
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(VgramAudioPlayerActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(8), dp(8), dp(8));
            row.setBackground(panel(0xff171622, 0x332e2a42, 18));
            row.setElevation(dp(1));
            ImageView cover = new ImageView(VgramAudioPlayerActivity.this);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackground(panel(0xff29233d, 0x557c4dff, 14));
            row.addView(cover, new LinearLayout.LayoutParams(dp(48), dp(48)));
            LinearLayout labels = new LinearLayout(VgramAudioPlayerActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(dp(10), 0, dp(8), 0);
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            TextView title = label("", 14, 0xfff5f1ff, true);
            TextView artist = label("", 12, 0xffb7b1c8, false);
            TextView duration = label("", 11, 0xffb7b1c8, false);
            labels.addView(title);
            labels.addView(artist, margin(0, dp(2), 0, 0));
            labels.addView(duration, margin(0, dp(2), 0, 0));
            TextView play = control("▶", 15);
            row.addView(play, new LinearLayout.LayoutParams(dp(44), dp(44)));
            return new Holder(row, cover, title, artist, duration, play);
        }
        @Override public void onBindViewHolder(@NonNull Holder h, int position) {
            VgramPlaybackManager.VgramTrack track = visibleTracks.get(position);
            h.title.setText(track.title);
            h.artist.setText(track.artist.isEmpty() ? "Неизвестный исполнитель" : track.artist);
            h.duration.setText(formatTime(track.durationMs));
            try {
                h.cover.setImageURI(Uri.parse(track.coverUrl));
                if (h.cover.getDrawable() == null) h.cover.setImageResource(android.R.drawable.ic_media_play);
            } catch (RuntimeException ignored) { h.cover.setImageResource(android.R.drawable.ic_media_play); }
            VgramMotion.applySpringTouch(h.itemView);
            VgramMotion.applySpringTouch(h.play);
            h.itemView.setContentDescription(track.title + ", " + track.artist + ", " + formatTime(track.durationMs));
            h.itemView.setOnClickListener(v -> playTrack(track));
            h.play.setContentDescription("Воспроизвести " + track.title);
            h.play.setOnClickListener(v -> playTrack(track));
        }
        @Override public int getItemCount() { return visibleTracks.size(); }
        final class Holder extends RecyclerView.ViewHolder {
            final ImageView cover; final TextView title, artist, duration, play;
            Holder(View item, ImageView cover, TextView title, TextView artist, TextView duration, TextView play) {
                super(item); this.cover=cover; this.title=title; this.artist=artist; this.duration=duration; this.play=play;
            }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (playback != null) playback.addListener(listener);
    }
    @Override protected void onPause() {
        if (playback != null) playback.removeListener(listener);
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (playback != null) playback.removeListener(listener);
        super.onDestroy();
    }
}
