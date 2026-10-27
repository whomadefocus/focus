package dev.focus.pomodoro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Ring + big time + session dots. */
final class TimerView extends View {
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG),
            time = new Paint(Paint.ANTI_ALIAS_FLAG), label = new Paint(Paint.ANTI_ALIAS_FLAG),
            dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final int track;
    private float frac = 1f;
    private String t = "25:00", lab = "Focus";
    private int done, every = 4, color;

    TimerView(Context c) {
        super(c);
        track = UI.col(c, R.color.divider);
        color = UI.col(c, R.color.primary);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(UI.dp(10));
        ring.setColor(track);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(UI.dp(10));
        arc.setStrokeCap(Paint.Cap.ROUND);
        time.setTextAlign(Paint.Align.CENTER);
        time.setTextSize(UI.dp(56));
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setColor(UI.col(c, R.color.text));
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(UI.dp(16));
        label.setColor(UI.col(c, R.color.text2));
    }

    void set(float frac, String time, String label, int done, int every, int color) {
        if (this.frac == frac && this.t.equals(time) && this.lab.equals(label) && this.done == done
                && this.every == every && this.color == color) return;
        this.frac = frac; this.t = time; this.lab = label; this.done = done; this.every = every; this.color = color;
        invalidate();
    }

    @Override protected void onMeasure(int w, int h) {
        int s = Math.min(View.MeasureSpec.getSize(w), UI.dp(300));
        if (s <= 0) s = UI.dp(300);
        setMeasuredDimension(s, s);
    }

    @Override protected void onDraw(Canvas c) {
        float p = ring.getStrokeWidth() / 2 + UI.dp(4);
        box.set(p, p, getWidth() - p, getHeight() - p);
        c.drawArc(box, 0, 360, false, ring);
        arc.setColor(color);
        if (frac > 0) c.drawArc(box, -90, 360f * frac, false, arc);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        c.drawText(lab, cx, cy - UI.dp(40), label);
        c.drawText(t, cx, cy + UI.dp(18), time);
        for (int i = 0; i < every; i++) {
            dot.setColor(i < done ? color : track);
            c.drawCircle(cx + (i - (every - 1) / 2f) * UI.dp(16), cy + UI.dp(52), UI.dp(4), dot);
        }
    }
}
