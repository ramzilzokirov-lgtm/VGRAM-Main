package org.telegram.ui.Cells;

import android.graphics.Canvas;
import android.graphics.RectF;

/**
 * Package-local bridge for DialogCell's VGRAM Premium ring drawing.
 * The implementation is shared with the VGRAM UI utility in org.telegram.ui.
 */
final class VgramGlassDrawable {
    private VgramGlassDrawable() {
    }

    static void drawPremiumRing(Canvas canvas, RectF bounds, float phase, float strokeWidth) {
        org.telegram.ui.VgramGlassDrawable.drawPremiumRing(canvas, bounds, phase, strokeWidth);
    }
}
