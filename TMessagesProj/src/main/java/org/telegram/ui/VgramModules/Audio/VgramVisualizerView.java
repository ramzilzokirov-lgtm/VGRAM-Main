package org.telegram.ui.VgramModules.Audio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/** Seven compact neon bars; bars settle smoothly to a baseline while paused. */
public final class VgramVisualizerView extends View {
    private static final int BAR_COUNT = 7;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float[] levels = new float[BAR_COUNT];
    private boolean playing;
    private long animationStart = SystemClock.uptimeMillis();
    private int accent = 0xffc5b878;

    public VgramVisualizerView(Context context) {
        super(context);
        setContentDescription("VGRAM audio visualizer");
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public void setPlaying(boolean value) {
        if (playing != value) {
            playing = value;
            animationStart = SystemClock.uptimeMillis();
        }
        invalidate();
    }

    public void setAccentColor(int color) {
        accent = color;
        invalidate();
    }

    /** Optional normalized amplitudes (0..1) from a future audio-frame source. */
    public void setLevels(float[] source) {
        if (source == null) return;
        int count = Math.min(source.length, BAR_COUNT);
        for (int i = 0; i < count; i++) levels[i] = Math.max(0f, Math.min(1f, source[i]));
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
                ? dp(48) : MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                ? dp(24) : MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, Math.min(height, dp(34)));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;
        float gap = dp(3);
        float barWidth = Math.max(dp(2), (width - gap * (BAR_COUNT - 1)) / BAR_COUNT);
        float baseline = height * 0.5f;
        float t = (SystemClock.uptimeMillis() - animationStart) / 180.0f;
        for (int i = 0; i < BAR_COUNT; i++) {
            float target = playing ? (0.18f + 0.72f * (float) (0.5 + 0.5 * Math.sin(t + i * 0.88))) : 0.06f;
            if (levels[i] > 0f && playing) target = Math.max(target * 0.45f, levels[i]);
            float barHeight = Math.max(dp(2), (height - dp(4)) * target);
            float left = i * (barWidth + gap);
            rect.set(left, baseline - barHeight / 2f, Math.min(width, left + barWidth), baseline + barHeight / 2f);
            paint.setColor(i == BAR_COUNT / 2 ? accent : blend(accent, 0xffd9e2e0, i / (float) (BAR_COUNT - 1)));
            canvas.drawRoundRect(rect, barWidth, barWidth, paint);
        }
        if (playing) postInvalidateDelayed(32L);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int blend(int from, int to, float amount) {
        float f = Math.max(0f, Math.min(1f, amount));
        int a = (from >>> 24) & 255, r = (from >>> 16) & 255, g = (from >>> 8) & 255, b = from & 255;
        int ar = (to >>> 24) & 255, rr = (to >>> 16) & 255, gr = (to >>> 8) & 255, br = to & 255;
        return ((int) (a + (ar - a) * f) << 24) | ((int) (r + (rr - r) * f) << 16)
                | ((int) (g + (gr - g) * f) << 8) | (int) (b + (br - b) * f);
    }
}
