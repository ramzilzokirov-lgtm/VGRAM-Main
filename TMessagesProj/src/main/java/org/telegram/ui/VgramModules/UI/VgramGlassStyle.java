package org.telegram.ui.VgramModules.UI;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import org.telegram.ui.VgramTheme;

/** Shared liquid-glass bubble styling. Call drawBorder with caller-owned Paint/RectF in hot paths. */
public final class VgramGlassStyle {
    private static final Paint BUBBLE_BORDER_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF BUBBLE_BORDER_BOUNDS = new RectF();
    private VgramGlassStyle() { }

    public static int opacityPercent(Context context) {
        return Math.max(20, Math.min(90, context.getSharedPreferences("vgram_ui", Context.MODE_PRIVATE)
                .getInt("glass_bubble_opacity", 58)));
    }

    public static int bubbleColor(Context context, int baseColor) {
        int alpha = Math.round(255f * opacityPercent(context) / 100f);
        return Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
    }

    public static int edgeColor(Context context) {
        int accent = VgramTheme.accent(context);
        return Color.argb(150, Color.red(accent), Color.green(accent), Color.blue(accent));
    }

    public static void drawDrawableBorder(Canvas canvas, Drawable drawable, Context context) {
        if (canvas == null || drawable == null || context == null) return;
        SharedPreferences preferences = context.getSharedPreferences("vgram_ui", Context.MODE_PRIVATE);
        if (!preferences.getBoolean("glass", true)) return;
        android.graphics.Rect bounds = drawable.getBounds();
        if (bounds == null || bounds.isEmpty()) return;
        BUBBLE_BORDER_BOUNDS.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
        BUBBLE_BORDER_PAINT.setStyle(Paint.Style.STROKE);
        BUBBLE_BORDER_PAINT.setStrokeWidth(context.getResources().getDisplayMetrics().density);
        BUBBLE_BORDER_PAINT.setColor(edgeColor(context));
        canvas.drawRoundRect(BUBBLE_BORDER_BOUNDS, 12f * context.getResources().getDisplayMetrics().density,
                12f * context.getResources().getDisplayMetrics().density, BUBBLE_BORDER_PAINT);
        BUBBLE_BORDER_PAINT.setStyle(Paint.Style.FILL);
    }

    public static void drawBorder(Canvas canvas, RectF bounds, float radius, Paint paint, Context context) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(context.getResources().getDisplayMetrics().density);
        paint.setColor(edgeColor(context));
        canvas.drawRoundRect(bounds, radius, radius, paint);
    }
}
