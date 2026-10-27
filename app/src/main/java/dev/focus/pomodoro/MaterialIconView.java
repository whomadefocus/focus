package dev.focus.pomodoro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.SparseArray;
import androidx.appcompat.widget.AppCompatImageView;

/** ImageView-compatible Material Symbols glyph, sourced from Google Fonts. */
final class MaterialIconView extends AppCompatImageView {
    private static final SparseArray<String> NAMES = new SparseArray<>();
    static {
        NAMES.put(R.drawable.ic_back, "arrow_back");
        NAMES.put(R.drawable.ic_play, "play_arrow");
        NAMES.put(R.drawable.ic_pause, "pause");
        NAMES.put(R.drawable.ic_next, "skip_next");
        NAMES.put(R.drawable.ic_prev, "skip_previous");
        NAMES.put(R.drawable.ic_reset, "restart_alt");
        NAMES.put(R.drawable.ic_notes, "edit_note");
        NAMES.put(R.drawable.ic_music, "music_note");
        NAMES.put(R.drawable.ic_pdf, "picture_as_pdf");
        NAMES.put(R.drawable.ic_settings, "settings");
        NAMES.put(R.drawable.ic_add, "add");
        NAMES.put(R.drawable.ic_folder, "folder_open");
        NAMES.put(R.drawable.ic_eye, "visibility");
        NAMES.put(R.drawable.ic_eye_off, "visibility_off");
        NAMES.put(R.drawable.ic_port, "screen_rotation");
        NAMES.put(R.drawable.ic_land, "stay_current_landscape");
        NAMES.put(R.drawable.ic_noise, "graphic_eq");
        NAMES.put(R.drawable.ic_stat, "timer");
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Typeface typeface;
    private String symbol = "circle";
    private int tint = 0xFF202124;
    private float textSize;

    MaterialIconView(Context context) {
        super(context);
        typeface = UI.symbolTypeface(context);
        paint.setTypeface(typeface);
        textSize = UI.dp(24);
        paint.setTextSize(textSize);
        if (android.os.Build.VERSION.SDK_INT >= 21) paint.setFontFeatureSettings("'liga'");
    }

    @Override public void setImageResource(int resourceId) {
        String name = NAMES.get(resourceId);
        if (name != null) setSymbol(name);
        else {
            symbol = null;
            super.setImageResource(resourceId);
        }
    }

    @Override public void setImageDrawable(android.graphics.drawable.Drawable drawable) {
        symbol = null;
        super.setImageDrawable(drawable);
    }

    void setSymbol(String name) {
        symbol = name;
        invalidate();
    }

    void setTint(int color) {
        tint = color;
        invalidate();
    }

    void setSymbolSize(float sp) {
        textSize = UI.dp(sp);
        paint.setTextSize(textSize);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (symbol == null) {
            super.onDraw(canvas);
            return;
        }
        paint.setColor(tint);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float contentWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        float contentHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        float x = getPaddingLeft() + (contentWidth - paint.measureText(symbol)) / 2f;
        float y = getPaddingTop() + (contentHeight - metrics.ascent - metrics.descent) / 2f;
        canvas.drawText(symbol, x, y, paint);
    }
}