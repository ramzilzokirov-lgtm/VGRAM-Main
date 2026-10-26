package org.telegram.ui.VgramModules.UI;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

/** Lightweight animated aurora spots; shaders and Paint instances are reused between frames. */
public final class VgramAuroraDrawable extends Drawable {
    private final Paint[] paints = { new Paint(Paint.ANTI_ALIAS_FLAG), new Paint(Paint.ANTI_ALIAS_FLAG), new Paint(Paint.ANTI_ALIAS_FLAG) };
    private final float density;
    private int accent;
    private long startTime = SystemClock.uptimeMillis();
    private boolean running = true;
    private final Runnable frame = this::invalidateSelf;

    public VgramAuroraDrawable(float density, int accent) {
        this.density = Math.max(0.5f, density);
        this.accent = accent;
        rebuildShaders();
    }

    public void setAccentColor(int color) {
        if (accent == color) return;
        accent = color;
        rebuildShaders();
        invalidateSelf();
    }

    public void start() {
        if (running) return;
        running = true;
        startTime = SystemClock.uptimeMillis();
        scheduleSelf(frame, SystemClock.uptimeMillis() + 32);
    }

    public void stop() {
        running = false;
        unscheduleSelf(frame);
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        float w = b.width(), h = b.height();
        float t = (SystemClock.uptimeMillis() - startTime) / 2400f;
        float x1 = w * (0.18f + 0.12f * (float) Math.sin(t));
        float y1 = h * (0.20f + 0.10f * (float) Math.cos(t * 0.83f));
        float x2 = w * (0.82f + 0.10f * (float) Math.cos(t * 0.72f));
        float y2 = h * (0.56f + 0.13f * (float) Math.sin(t * 0.91f));
        float x3 = w * (0.48f + 0.16f * (float) Math.sin(t * 0.61f + 1.4f));
        float y3 = h * (0.90f + 0.06f * (float) Math.cos(t * 0.77f));
        canvas.drawColor(0xff17191d);
        canvas.drawCircle(x1, y1, Math.max(w, h) * 0.56f, paints[0]);
        canvas.drawCircle(x2, y2, Math.max(w, h) * 0.49f, paints[1]);
        canvas.drawCircle(x3, y3, Math.max(w, h) * 0.40f, paints[2]);
        if (running) scheduleSelf(frame, SystemClock.uptimeMillis() + 32);
    }

    private void rebuildShaders() {
        int soft = withAlpha(accent, 92);
        int dim = withAlpha(accent, 38);
        int mint = withAlpha(mix(accent, 0xff76c8c0, 0.42f), 58);
        paints[0].setShader(new RadialGradient(0, 0, 1, new int[]{soft, dim, Color.TRANSPARENT},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        paints[1].setShader(new RadialGradient(0, 0, 1, new int[]{mint, dim, Color.TRANSPARENT},
                new float[]{0f, 0.50f, 1f}, Shader.TileMode.CLAMP));
        paints[2].setShader(new RadialGradient(0, 0, 1, new int[]{dim, Color.TRANSPARENT},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        for (Paint paint : paints) paint.setDither(true);
    }

    private static int withAlpha(int color, int alpha) { return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00ffffff); }
    private static int mix(int a, int b, float f) {
        int r = Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * f);
        int g = Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * f);
        int bl = Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f);
        return Color.rgb(r, g, bl);
    }
    @Override protected void onBoundsChange(Rect bounds) { rebuildShaders(); }
    @Override public void setAlpha(int alpha) { for (Paint paint : paints) paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter colorFilter) { for (Paint paint : paints) paint.setColorFilter(colorFilter); invalidateSelf(); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
}
