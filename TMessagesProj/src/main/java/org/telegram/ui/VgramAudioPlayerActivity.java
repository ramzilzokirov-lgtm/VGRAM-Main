package org.telegram.ui;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.net.Uri;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.IOException;

public class VgramAudioPlayerActivity extends VgramBaseActivity {
    private MediaPlayer player;
    private EditText urlField;
    private TextView status, playButton;
    private WaveView wave;
    private boolean preparing;
    private HandlerThread playerThread;
    private Handler playerHandler;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        playerThread = new HandlerThread("VGRAM-AudioPlayer");
        playerThread.start();
        playerHandler = new Handler(playerThread.getLooper());
        setupScreen("VGRAM AUDIO","Потоковый плеер","Воспроизведение сетевого аудио без сохранения файла на устройстве.");
        LinearLayout c=card("Сейчас играет","Вставь прямую ссылку на аудиофайл или поток. Поддерживаемый формат зависит от сервера.");
        wave=new WaveView(); c.addView(wave,margin(0,dp(10),0,dp(12)));
        urlField=new EditText(this); urlField.setSingleLine(true); urlField.setTextColor(0xfff1f0eb); urlField.setHintTextColor(0xff9b9da3); urlField.setHint("https://…/audio.mp3"); urlField.setTextSize(14); urlField.setPadding(dp(13),dp(12),dp(13),dp(12)); urlField.setBackground(panel(0xff202328,0x667faaa5,14)); c.addView(urlField,margin(0,dp(4),0,dp(10)));
        status=label("Готов к воспроизведению",12,0xffb9bbc1,false); c.addView(status,margin(0,0,0,dp(10)));
        playButton=label("▶  Воспроизвести поток",14,0xff17191d,true); playButton.setGravity(Gravity.CENTER); playButton.setPadding(dp(15),dp(14),dp(15),dp(14)); playButton.setBackground(panel(accent(),0x99ffffff,18)); c.addView(playButton,new LinearLayout.LayoutParams(-1,-2)); playButton.setOnClickListener(v->togglePlayback()); body.addView(c,margin(0,0,0,dp(12)));
        body.addView(card("Поддержка источников","Плеер воспроизводит прямые HTTP(S)-ссылки на аудиопотоки. Страницы SoundCloud и Spotify не являются прямыми аудиопотоками; для них нужен официальный API или SDK."),margin(0,0,0,dp(12)));
    }
    private void togglePlayback() {
        if (preparing || playerHandler == null) return;
        String raw = urlField.getText() == null ? "" : urlField.getText().toString().trim();
        Uri uri = Uri.parse(raw);
        String scheme = uri.getScheme();
        if (uri.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            status.setText("Укажи полный HTTP(S) URL прямого аудиопотока"); return;
        }
        preparing = true; status.setText("Подключение к потоку…"); playButton.setEnabled(false);
        playerHandler.post(() -> {
            try {
                if (player != null) {
                    if (player.isPlaying()) {
                        player.pause();
                        runOnUiThread(() -> { preparing=false; playButton.setEnabled(true); playButton.setText("▶  Продолжить"); status.setText("Пауза"); wave.setPlaying(false); });
                    } else {
                        player.start();
                        runOnUiThread(() -> { preparing=false; playButton.setEnabled(true); playButton.setText("Ⅱ  Пауза"); status.setText("Воспроизведение"); wave.setPlaying(true); });
                    }
                    return;
                }
                MediaPlayer created = new MediaPlayer(); player = created;
                created.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                created.setOnPreparedListener(mp -> {
                    try {
                        if (isFinishing() || isDestroyed()) { releasePlayerInternal(); return; }
                        mp.start();
                        runOnUiThread(() -> { preparing=false; playButton.setEnabled(true); playButton.setText("Ⅱ  Пауза"); status.setText("Воспроизведение потока"); wave.setPlaying(true); });
                    } catch (RuntimeException error) { releasePlayerInternal(); runOnUiThread(() -> showPlayerError("Не удалось начать воспроизведение")); }
                });
                created.setOnCompletionListener(mp -> { releasePlayerInternal(); runOnUiThread(() -> { preparing=false; playButton.setEnabled(true); playButton.setText("▶  Воспроизвести поток"); status.setText("Поток завершён"); wave.setPlaying(false); }); });
                created.setOnErrorListener((mp, what, extra) -> { releasePlayerInternal(); runOnUiThread(() -> showPlayerError("Поток не поддерживается или сервер недоступен")); return true; });
                // Network source initialization is deliberately kept off the main thread.
                created.setDataSource(raw);
                created.prepareAsync();
            } catch (Exception error) { releasePlayerInternal(); runOnUiThread(() -> showPlayerError("Ошибка подключения. Проверь ссылку и формат аудио.")); }
        });
    }
    private void showPlayerError(String message) {
        preparing=false;
        if (playButton!=null) { playButton.setEnabled(true); playButton.setText("▶  Повторить"); }
        if (status!=null) status.setText(message);
        if (wave!=null) wave.setPlaying(false);
    }
    private void releasePlayerInternal() {
        MediaPlayer current=player; player=null;
        if(current!=null) {
            try { current.setOnPreparedListener(null); } catch(RuntimeException ignored) {}
            try { current.setOnCompletionListener(null); } catch(RuntimeException ignored) {}
            try { current.setOnErrorListener(null); } catch(RuntimeException ignored) {}
            try { current.reset(); } catch(RuntimeException ignored) {}
            try { current.release(); } catch(RuntimeException ignored) {}
        }
        preparing=false;
    }
    @Override protected void onDestroy() {
        Handler handler=playerHandler; HandlerThread thread=playerThread; playerHandler=null; playerThread=null;
        if(handler!=null) handler.post(this::releasePlayerInternal);
        if(thread!=null) thread.quitSafely();
        super.onDestroy();
    }
    private final class WaveView extends View{
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF r=new RectF();private boolean playing;private long start=System.currentTimeMillis();
        WaveView(){super(VgramAudioPlayerActivity.this);setLayerType(View.LAYER_TYPE_HARDWARE,null);}
        @Override protected void onMeasure(int ws,int hs){setMeasuredDimension(MeasureSpec.getSize(ws),dp(112));}
        void setPlaying(boolean b){playing=b;if(b)start=System.currentTimeMillis();invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),mid=h/2;p.setColor(0x442c3036);r.set(0,0,w,h);c.drawRoundRect(r,dp(18),dp(18),p);int n=44;float gap=dp(3),bw=Math.max(dp(2),(w-gap*(n-1)-dp(24))/n),t=(System.currentTimeMillis()-start)/220f;
            for(int i=0;i<n;i++){float sig=(float)(Math.sin(i*.49f+(playing?t:0))*.5+.5),bh=dp(10)+sig*(playing?dp(65):dp(28)),left=dp(12)+i*(bw+gap);r.set(left,mid-bh/2,left+bw,mid+bh/2);p.setColor(i%5==0?accent():0xffb8c4c3);c.drawRoundRect(r,bw,bw,p);}if(playing)postInvalidateDelayed(45);}
    }
}