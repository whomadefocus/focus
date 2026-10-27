package dev.focus.pomodoro;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Tiny view-building helpers (the whole UI is built in code: no layout inflation, no extra libs). */
final class UI {
    static final int M = -1, W = -2;
    static float D = 1f;
    private static boolean dark;
    private static Typeface materialSymbols;

    static void init(Context c) {
        D = c.getResources().getDisplayMetrics().density;
        dark = c.getSharedPreferences("appearance", Context.MODE_PRIVATE).getBoolean("dark", false);
    }

    static boolean dark() { return dark; }

    static void applyTheme(android.app.Activity activity) {
        if (activity.getSharedPreferences("appearance", Context.MODE_PRIVATE).getBoolean("dark", false)) {
            activity.setTheme(R.style.AppTheme_Dark);
        }
    }

    static int dp(float v) { return Math.round(v * D); }

    @SuppressWarnings("deprecation")
    static int col(Context c, int id) {
        if (dark) {
            if (id == R.color.bg) return 0xFF101416;
            if (id == R.color.surface) return 0xFF1B2225;
            if (id == R.color.text) return 0xFFE6ECEE;
            if (id == R.color.text2) return 0xFF9EAAAE;
            if (id == R.color.primary) return 0xFF8FD3C7;
            if (id == R.color.primary_dark) return 0xFF315D57;
            if (id == R.color.green) return 0xFFA8C87A;
            if (id == R.color.divider) return 0xFF364044;
            if (id == R.color.ic_bg) return 0xFF8FD3C7;
        }
        return c.getResources().getColor(id);
    }

    static TextView symbol(Context c, String name, float size, int tint) {
        TextView icon = tv(c, name, size, tint, false);
        icon.setTypeface(symbolTypeface(c));
        if (android.os.Build.VERSION.SDK_INT >= 21) icon.setFontFeatureSettings("'liga'");
        icon.setGravity(Gravity.CENTER);
        return icon;
    }

    static Typeface symbolTypeface(Context context) {
        if (materialSymbols == null) materialSymbols = Typeface.createFromAsset(
                context.getAssets(), "fonts/material_symbols_rounded.ttf");
        return materialSymbols;
    }

    static TextView symbolButton(Context c, String name, int tint, int sizeDp, float iconSp) {
        TextView button = symbol(c, name, iconSp, tint);
        button.setBackground(ripple(null, true, 0));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    static GradientDrawable rect(int fill, float rDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(rDp));
        return g;
    }

    static GradientDrawable oval(int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        return g;
    }

    static Drawable ripple(Drawable bg, boolean oval, float rDp) {
        GradientDrawable mask = oval ? oval(0xFF000000) : rect(0xFF000000, rDp);
        return new RippleDrawable(ColorStateList.valueOf(0x33808080), bg, mask);
    }

    static TextView tv(Context c, CharSequence t, float sp, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    /** Flat icon button with a round ripple. */
    static ImageView ib(Context c, int res, int tint, int sizeDp, int padDp) {
        ImageView v = new MaterialIconView(c);
        v.setImageResource(res);
        if (v instanceof MaterialIconView) ((MaterialIconView) v).setTint(tint); else v.setColorFilter(tint);
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        v.setPadding(dp(padDp), dp(padDp), dp(padDp), dp(padDp));
        v.setBackground(ripple(null, true, 0));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return v;
    }

    /** Filled circular button. */
    static ImageView circle(Context c, int res, int sizeDp, int bg, int tint, int iconDp) {
        ImageView v = new MaterialIconView(c);
        v.setImageResource(res);
        if (v instanceof MaterialIconView) ((MaterialIconView) v).setTint(tint); else v.setColorFilter(tint);
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int p = dp((sizeDp - iconDp) / 2f);
        v.setPadding(p, p, p, p);
        v.setBackground(ripple(oval(bg), true, 0));
        v.setElevation(dp(2));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return v;
    }

    static LinearLayout topBar(Context c, String title, View.OnClickListener back, View... actions) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(4), dp(4), dp(8), dp(4));
        b.setBackgroundColor(col(c, R.color.surface));
        b.setElevation(dp(4));
        ImageView bk = ib(c, R.drawable.ic_back, col(c, R.color.text), 48, 12);
        bk.setOnClickListener(back);
        b.addView(bk, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView t = tv(c, title, 20, col(c, R.color.text), true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, W, 1f);
        tl.leftMargin = dp(8);
        b.addView(t, tl);
        for (View a : actions) b.addView(a, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return b;
    }

    /** Thin progress bar (pct 0..100). Give the parent a fixed height. */
    static View bar(Context c, int pct) {
        pct = Math.max(0, Math.min(100, pct));
        LinearLayout l = new LinearLayout(c);
        l.setBackgroundColor(col(c, R.color.divider));
        View f = new View(c);
        f.setBackgroundColor(col(c, R.color.primary));
        l.addView(f, new LinearLayout.LayoutParams(0, M, pct));
        l.addView(new View(c), new LinearLayout.LayoutParams(0, M, 100 - pct));
        return l;
    }

    static String mmss(long ms) {
        long s = (ms + 999) / 1000;
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }
}
