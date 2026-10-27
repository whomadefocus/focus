package dev.focus.pomodoro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Compact A-Z index for alphabetically ordered RecyclerViews. */
final class AlphabetRail extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RecyclerView list;
    private final ArrayList<String> labels = new ArrayList<>();

    AlphabetRail(Context context, RecyclerView list) {
        super(context);
        this.list = list;
        setBackground(UI.rect(UI.col(context, R.color.surface), 12));
        setContentDescription("Alphabetical list index");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void setLabels(List<String> values) {
        labels.clear();
        labels.addAll(values);
        setVisibility(labels.size() > 5 ? VISIBLE : GONE);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float row = getHeight() / 26f;
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Math.min(UI.dp(10), row * 0.72f));
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setColor(UI.col(getContext(), R.color.primary));
        for (int i = 0; i < 26; i++) {
            canvas.drawText(String.valueOf((char) ('A' + i)), getWidth() / 2f, row * (i + 0.72f), paint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN
                && event.getActionMasked() != MotionEvent.ACTION_MOVE
                && event.getActionMasked() != MotionEvent.ACTION_UP) return true;
        if (labels.isEmpty()) return true;
        int letter = Math.max(0, Math.min(25, (int) (event.getY() * 26 / Math.max(1, getHeight()))));
        int position = findPosition((char) ('a' + letter));
        RecyclerView.LayoutManager manager = list.getLayoutManager();
        if (position >= 0 && manager instanceof LinearLayoutManager) {
            ((LinearLayoutManager) manager).scrollToPositionWithOffset(position, 0);
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
        return true;
    }

    private int findPosition(char letter) {
        int next = -1;
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            if (label == null || label.trim().isEmpty()) continue;
            char first = label.trim().toLowerCase(Locale.ROOT).charAt(0);
            if (first == letter) return i;
            if (next < 0 && first > letter && Character.isLetter(first)) next = i;
        }
        return next >= 0 ? next : labels.size() - 1;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
