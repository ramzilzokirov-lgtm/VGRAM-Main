package org.telegram.ui.VgramModules.UI;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Lightweight animated waveform placeholder for the VGRAM stream player. */
public final class VgramVisualizerView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean playing;
    private long animationStart;

    public VgramVisualizerView(Context context) {
        super(context);
        paint.setStrokeWidth(getResources().getDisplayMetrics().density * 3f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xffc5b878);
        setMinimumHeight((int) (48f * getResources().getDisplayMetrics().density));
        animationStart = android.os.SystemClock.uptimeMillis();
    }

    public void setPlaying(boolean value) {
        if (playing == value) return;
        playing = value;
        animationStart = android.os.SystemClock.uptimeMillis();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float centerY = getHeight() * 0.5f;
        float step = 7f * density;
        float barWidth = 3f * density;
        int count = Math.max(1, (int) (getWidth() / step));
        float phase = (android.os.SystemClock.uptimeMillis() - animationStart) / 260f;
        for (int i = 0; i < count; i++) {
            float x = (i + 0.5f) * getWidth() / count;
            float envelope = 0.22f + 0.78f * Math.abs((float) Math.sin(i * 0.57f));
            float wave = playing ? 0.35f + 0.65f * Math.abs((float) Math.sin(phase + i * 0.31f)) : 0.35f;
            float halfHeight = Math.max(density * 2f, getHeight() * 0.42f * envelope * wave);
            canvas.drawLine(x, centerY - halfHeight, x, centerY + halfHeight, paint);
        }
        if (playing) postInvalidateOnAnimation();
    }
}
