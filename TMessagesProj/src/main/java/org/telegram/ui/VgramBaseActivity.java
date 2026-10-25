package org.telegram.ui;

import org.telegram.messenger.R;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

abstract class VgramBaseActivity extends Activity {
    protected LinearLayout body;
    protected int accent() { return VgramTheme.accent(this); }
    protected int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onCreate(Bundle state) { super.onCreate(state); }

    protected void setupScreen(String eyebrow, String title, String subtitle) {
        getWindow().setStatusBarColor(0xff17191d);
        getWindow().setNavigationBarColor(0xff17100c);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[] { 0xff292c31, 0xff17191d, 0xff202328 }));
        root.setPadding(dp(18), dp(10), dp(18), dp(14));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = label("‹", 31, 0xfff0f0ec, true);
        back.setGravity(Gravity.CENTER);
        back.setBackground(panel(0xff2b2e33, accent(), 18));
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(46)));
        back.setOnClickListener(v -> finish());

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(13), 0, 0, 0);
        brand.addView(label(getString(R.string.VgramBrand), 11, accent(), true));
        brand.addView(label(eyebrow, 10, 0xffb5b5b0, true));
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(12), 0, dp(8));
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        TextView heading = label(title, 29, 0xfff2f1ec, true);
        body.addView(heading, margin(0, 0, 0, 7));
        TextView description = label(subtitle, 14, 0xffa0a3a8, false);
        description.setLineSpacing(dp(3), 1f);
        body.addView(description, margin(0, 0, 0, 19));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView footer = label(getString(R.string.VgramFooter), 10, 0xff72767d, true);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, margin(0, dp(5), 0, 0));
        setContentView(root);
    }

    protected LinearLayout card(String title, String description) {
        return createCard(title, description, false);
    }

    protected LinearLayout expandableCard(String title, String description) {
        return createCard(title, description, true);
    }

    private LinearLayout createCard(String title, String description, boolean expandable) {
        boolean glass = getSharedPreferences("vgram_ui", MODE_PRIVATE).getBoolean("glass", true);
        int fill = glass ? 0x20ffffff : 0xff22252a;
        int stroke = glass ? 0x40ffffff : Color.argb(45, Color.red(accent()), Color.green(accent()), Color.blue(accent()));
        com.google.android.material.card.MaterialCardView surface = new com.google.android.material.card.MaterialCardView(this);
        surface.setCardBackgroundColor(fill);
        surface.setRadius(dp(glass ? 18 : 14));
        surface.setStrokeColor(stroke);
        surface.setStrokeWidth(dp(1));
        surface.setCardElevation(dp(glass ? 5 : 1));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(17), dp(17), dp(17));
        if (glass) {
            card.setBackground(VgramGlassDrawable.card(this));
        }
        surface.addView(card);
        surface.setOnTouchListener((view, event) -> {
            float target = event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN ? 0.955f : 1f;
            SpringForce spring = new SpringForce(target).setDampingRatio(event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN ? SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY : SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY).setStiffness(SpringForce.STIFFNESS_LOW);
            new SpringAnimation(view, DynamicAnimation.SCALE_X).setSpring(spring).animateToFinalPosition(target);
            SpringForce springY = new SpringForce(target).setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY).setStiffness(SpringForce.STIFFNESS_LOW);
            new SpringAnimation(view, DynamicAnimation.SCALE_Y).setSpring(springY).animateToFinalPosition(target);
            return false;
        });

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = label(title, 16, 0xfff1f0eb, true);
        heading.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));
        TextView indicator = null;
        if (expandable) {
            indicator = label("+", 22, accent(), true);
            indicator.setGravity(Gravity.CENTER);
            heading.addView(indicator, new LinearLayout.LayoutParams(dp(28), dp(28)));
        }
        card.addView(heading);
        TextView sub = label(description, 13, 0xffa3a49f, false);
        sub.setLineSpacing(dp(3), 1f);
        if (expandable) {
            sub.setVisibility(View.GONE);
            final TextView toggle = indicator;
            heading.setOnClickListener(v -> {
                boolean show = sub.getVisibility() != View.VISIBLE;
                sub.setVisibility(show ? View.VISIBLE : View.GONE);
                toggle.setText(show ? "−" : "+");
                surface.setCardElevation(show ? dp(2) : dp(1));
            });
            heading.setClickable(true);
            heading.setFocusable(true);
        }
        card.addView(sub, margin(0, 7, 0, 0));
        body.addView(surface, margin(0, 0, 0, 12));
        return card;
    }

    protected TextView label(String value, float size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        text.setIncludeFontPadding(true);
        return text;
    }

    protected TextView addButton(LinearLayout parent, String value, Runnable action) {
        com.google.android.material.button.MaterialButton button = new com.google.android.material.button.MaterialButton(this);
        button.setText(value);
        button.setTextSize(14);
        button.setTextColor(0xff171713);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        button.setGravity(Gravity.CENTER);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setCornerRadius(dp(14));
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent()));
        button.setElevation(0);
        button.setPadding(dp(16), dp(14), dp(16), dp(14));
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(v -> {
            if (getSharedPreferences("vgram_ui", MODE_PRIVATE).getBoolean("animations", true)) {
                v.animate().scaleX(0.98f).scaleY(0.98f).setDuration(70).withEndAction(() ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()).start();
            }
            if (action != null) action.run();
        });
        parent.addView(button, margin(0, dp(13), 0, 0));
        return button;
    }

    protected LinearLayout.LayoutParams margin(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return lp;
    }

    protected GradientDrawable panel(int fill, int stroke, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(1), stroke);
        return shape;
    }

    protected int darken(int color) {
        return Color.rgb((int)(Color.red(color) * .78f), (int)(Color.green(color) * .78f), (int)(Color.blue(color) * .78f));
    }
}
