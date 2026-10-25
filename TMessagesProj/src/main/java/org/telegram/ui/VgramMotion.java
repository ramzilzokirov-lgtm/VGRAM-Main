package org.telegram.ui;

import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/** Shared lightweight touch physics for VGRAM interactive surfaces. */
public final class VgramMotion {
    private VgramMotion() {}

    public static void applySpringTouch(View view) {
        if (view == null || Boolean.TRUE.equals(view.getTag(R.id.vgram_motion_attached))) return;
        view.setTag(R.id.vgram_motion_attached, Boolean.TRUE);
        view.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                animateScale(v, 0.955f, false);
            } else if (action == MotionEvent.ACTION_UP) {
                animateScale(v, 1.0f, true);
            } else if (action == MotionEvent.ACTION_CANCEL) {
                animateScale(v, 1.0f, false);
            }
            // Never consume the event: normal click, long-click and accessibility handling remain intact.
            return false;
        });
    }

    private static void animateScale(View view, float target, boolean haptic) {
        SpringForce force = new SpringForce(target)
                .setStiffness(target < 1f ? SpringForce.STIFFNESS_MEDIUM : SpringForce.STIFFNESS_LOW)
                .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
        new SpringAnimation(view, DynamicAnimation.SCALE_X).setSpring(force).animateToFinalPosition(target);
        SpringForce forceY = new SpringForce(target)
                .setStiffness(target < 1f ? SpringForce.STIFFNESS_MEDIUM : SpringForce.STIFFNESS_LOW)
                .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
        new SpringAnimation(view, DynamicAnimation.SCALE_Y).setSpring(forceY).animateToFinalPosition(target);
        if (haptic) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    public static void applyOverscroll(View view) {
        if (view == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            view.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        }
    }

    public static void crossfadeIn(View view) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate().alpha(1f).setDuration(200L).start();
    }
}
