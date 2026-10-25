package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.RenderEffect;

/** Shared VGRAM glass surfaces and Premium neon-ring drawing helpers. */
public final class VgramGlassDrawable {
    private static final Paint RING_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private VgramGlassDrawable() {}

    public static GradientDrawable card(Context context) {
        float density = context == null ? 1f : context.getResources().getDisplayMetrics().density;
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] { Color.argb(38, 255, 255, 255), Color.argb(26, 0, 0, 0) });
        drawable.setCornerRadius(18f * density);
        drawable.setStroke(Math.max(1, Math.round(density)), Color.argb(60, 255, 255, 255));
        return drawable;
    }

    /** Enables Android's real window-level blur-behind for translucent dialogs/sheets.
     * Call only on a dimmed, translucent dialog window; not on the main opaque activity window. */
    public static void enableBlurBehind(Window window, int radiusPx) {
        if (window == null || Build.VERSION.SDK_INT < 31) return;
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.setBlurBehindRadius(Math.max(1, radiusPx));
        window.setAttributes(attributes);
    }

    /** Applies blur to the supplied backdrop view itself on Android 12+. This is not a true
     * backdrop-sampling API; callers must use a separate background layer behind the content. */
    public static void blurLayer(View backdrop, float radius) {
        if (backdrop == null || Build.VERSION.SDK_INT < 31) return;
        backdrop.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
    }

    public static void clearBlur(View view) {
        if (view != null && Build.VERSION.SDK_INT >= 31) view.setRenderEffect(null);
    }

    public static void drawPremiumRing(Canvas canvas, RectF bounds, float phase, float strokeWidth) {
        if (canvas == null || bounds == null || bounds.isEmpty()) return;
        int[] colors = new int[] { 0xff54e8ff, 0xffa36bff, 0xffff58c7, 0xffc5b878, 0xff54e8ff };
        SweepGradient gradient = new SweepGradient(bounds.centerX(), bounds.centerY(), colors, null);
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setRotate(phase, bounds.centerX(), bounds.centerY());
        gradient.setLocalMatrix(matrix);
        RING_PAINT.setShader(gradient);
        RING_PAINT.setStyle(Paint.Style.STROKE);
        RING_PAINT.setStrokeWidth(strokeWidth);
        RING_PAINT.setShadowLayer(strokeWidth * 1.5f, 0, 0, 0xff9e71ff);
        canvas.drawRoundRect(bounds, bounds.width() / 2f, bounds.height() / 2f, RING_PAINT);
        RING_PAINT.clearShadowLayer();
        RING_PAINT.setShader(null);
        RING_PAINT.setStyle(Paint.Style.FILL);
    }
}