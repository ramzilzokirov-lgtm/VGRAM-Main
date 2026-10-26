package org.telegram.ui.VgramModules.UI;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.os.Build;
import android.view.WindowInsets;
import android.view.RoundedCorner;
import android.view.View;

import org.telegram.ui.VgramTheme;

import java.lang.ref.WeakReference;

/** Non-interactive, allocation-free edge glow overlay for LaunchActivity. */
public final class VgramEdgeGlowView extends View implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final int MODE_OFF = 0;
    public static final int MODE_SOUND = 1;
    public static final int MODE_ALWAYS = 2;
    private static WeakReference<VgramEdgeGlowView> activeView = new WeakReference<>(null);
    private final SharedPreferences preferences;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path edgePath = new Path();
    private final RectF bounds = new RectF();
    private int mode;
    private int accent;
    private long pulseUntil;
    private long animationStart = SystemClock.uptimeMillis();
    private float density;
    private float cornerRadius;
    private boolean attached;

    public VgramEdgeGlowView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        cornerRadius = dp(28);
        preferences = context.getApplicationContext().getSharedPreferences("vgram_ui", Context.MODE_PRIVATE);
        mode = preferences.getInt("edge_glow_mode", MODE_OFF);
        accent = VgramTheme.accent(context);
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(false);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setContentDescription("VGRAM edge glow");
    }

    public static void pulse(Context context, float intensity) {
        VgramEdgeGlowView view = activeView.get();
        if (view == null || view.mode == MODE_OFF) return;
        view.pulseUntil = SystemClock.uptimeMillis() + (long) (220 + Math.max(0f, Math.min(1f, intensity)) * 480);
        view.invalidate();
    }

    public void pulse(float intensity) {
        pulseUntil = SystemClock.uptimeMillis() + (long) (220 + Math.max(0f, Math.min(1f, intensity)) * 480);
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        activeView = new WeakReference<>(this);
        preferences.registerOnSharedPreferenceChangeListener(this);
        refreshPreferences();
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        if (activeView.get() == this) activeView.clear();
        removeCallbacks(invalidateFrame);
        super.onDetachedFromWindow();
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if ("edge_glow_mode".equals(key) || "accent".equals(key) || "vgram_custom_accent".equals(key)) {
            refreshPreferences();
            invalidate();
        }
    }

    private void refreshPreferences() {
        mode = preferences.getInt("edge_glow_mode", MODE_OFF);
        accent = VgramTheme.accent(getContext());
    }

    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 31) {
            float radius = 0;
            RoundedCorner[] corners = {
                    insets.getRoundedCorner(WindowInsets.ROUND_CORNER_TOP_LEFT),
                    insets.getRoundedCorner(WindowInsets.ROUND_CORNER_TOP_RIGHT),
                    insets.getRoundedCorner(WindowInsets.ROUND_CORNER_BOTTOM_LEFT),
                    insets.getRoundedCorner(WindowInsets.ROUND_CORNER_BOTTOM_RIGHT)
            };
            for (RoundedCorner corner : corners) {
                if (corner != null) radius = Math.max(radius, corner.getRadius());
            }
            if (radius > 0) cornerRadius = radius;
        }
        return super.onApplyWindowInsets(insets);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mode == MODE_OFF || getWidth() <= 0 || getHeight() <= 0) return;
        long now = SystemClock.uptimeMillis();
        boolean pulsing = now < pulseUntil;
        if (mode == MODE_SOUND && !pulsing) return;
        float sweep = (float) ((now - animationStart) % 2100L) / 2100f;
        float strength = mode == MODE_ALWAYS ? 0.35f + 0.18f * (float) Math.sin(sweep * Math.PI * 2)
                : Math.max(0f, (pulseUntil - now) / 700f);
        int alpha = Math.max(0, Math.min(210, (int) (strength * 190)));
        int color = (alpha << 24) | (accent & 0x00ffffff);
        bounds.set(dp(8), dp(8), getWidth() - dp(8), getHeight() - dp(8));
        edgePath.reset();
        float corner = Math.min(cornerRadius, Math.min(bounds.width(), bounds.height()) / 5f);
        edgePath.addRoundRect(bounds, corner, corner, Path.Direction.CW);
        paint.setColor(color);
        paint.setStrokeWidth(dp(2.4f + (pulsing ? 1.2f : 0f)));
        canvas.drawPath(edgePath, paint);
        if (mode == MODE_ALWAYS || pulsing) postInvalidateOnAnimation();
    }

    private final Runnable invalidateFrame = this::invalidate;

    private float dp(float value) { return value * density; }
}
