package dev.focus.pomodoro;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** One editable card per day. Tasks are lines starting with a checkbox; tap the box to tick it. */
final class NotesPage extends LinearLayout {
    static final char BOX = '\u2610', CHECK = '\u2611';

    static final class Day {
        String day, text;
    }

    private static final class RowH {
        Day d;
        EditText e;
        TextView head;
        TextWatcher w;
    }

    private final MainActivity a;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final HashMap<String, String> pending = new HashMap<>();
    private final ArrayList<Day> days = new ArrayList<>();
    private Rows<Day> rows;

    private final Runnable flushR = new Runnable() { @Override public void run() { flush(); } };
    private final Runnable reloadR = new Runnable() { @Override public void run() { reloadToday(); } };

    NotesPage(MainActivity act) {
        super(act);
        a = act;
        setOrientation(VERTICAL);
        setBackgroundColor(UI.col(a, R.color.bg));
        addView(UI.topBar(a, "Notes", new View.OnClickListener() { @Override public void onClick(View v) { a.back(); } }),
                new LinearLayout.LayoutParams(UI.M, UI.W));

        for (String d : Notes.days(a)) {
            Day x = new Day();
            x.day = d;
            x.text = Notes.read(a, d);
            days.add(x);
        }

        RecyclerView rv = new RecyclerView(a);
        rv.setLayoutManager(new LinearLayoutManager(a));
        rv.setClipToPadding(false);
        rv.setPadding(UI.dp(8), UI.dp(8), UI.dp(8), UI.dp(80));
        rows = new Rows<Day>() {
            @Override View make(Context c) { return makeRow(c); }

            @Override void bind(View v, Day d) {
                RowH h = (RowH) v.getTag();
                if (h.w != null) h.e.removeTextChangedListener(h.w);
                h.d = d;
                h.head.setText(label(d.day));
                h.e.setText(d.text);
                h.w = new W(d, h.e);
                h.e.addTextChangedListener(h.w);
            }
        };
        rows.set(days);
        rv.setAdapter(rows);
        addView(rv, new LinearLayout.LayoutParams(UI.M, 0, 1f));
    }

    private View makeRow(Context c) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(VERTICAL);
        card.setBackground(UI.rect(UI.col(c, R.color.surface), 8));
        card.setElevation(UI.dp(1));
        card.setPadding(UI.dp(14), UI.dp(6), UI.dp(8), UI.dp(8));
        RecyclerView.LayoutParams rlp = new RecyclerView.LayoutParams(UI.M, UI.W);
        rlp.setMargins(UI.dp(4), UI.dp(4), UI.dp(4), UI.dp(4));
        card.setLayoutParams(rlp);

        final RowH h = new RowH();
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        h.head = UI.tv(c, "", 14, UI.col(c, R.color.text2), true);
        top.addView(h.head, new LinearLayout.LayoutParams(0, UI.W, 1f));
        TextView task = UI.tv(c, BOX + " Task", 13, UI.col(c, R.color.primary), true);
        task.setPadding(UI.dp(12), UI.dp(8), UI.dp(12), UI.dp(8));
        task.setBackground(UI.ripple(null, false, 16));
        task.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { addTask(h.e); } });
        top.addView(task, new LinearLayout.LayoutParams(UI.W, UI.W));
        card.addView(top, new LinearLayout.LayoutParams(UI.M, UI.W));

        EditText e = new EditText(c);
        e.setBackground(null);
        e.setPadding(0, UI.dp(4), UI.dp(8), UI.dp(4));
        e.setTextColor(UI.col(c, R.color.text));
        e.setHintTextColor(UI.col(c, R.color.text2));
        e.setHint("Write something\u2026");
        e.setTextSize(16);
        e.setLineSpacing(0, 1.15f);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setMinLines(3);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy;

            @Override public boolean onTouch(View v, MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                    dx = ev.getX();
                    dy = ev.getY();
                } else if (ev.getAction() == MotionEvent.ACTION_UP
                        && Math.abs(ev.getX() - dx) < UI.dp(8) && Math.abs(ev.getY() - dy) < UI.dp(8)) {
                    EditText t = (EditText) v;
                    int off = t.getOffsetForPosition(ev.getX(), ev.getY());
                    Editable ed = t.getText();
                    if (off >= 0 && off <= ed.length()) {
                        int ls = lineStart(ed, off);
                        if (ls < ed.length() && isBox(ed.charAt(ls)) && off <= ls + 1) {
                            ed.replace(ls, ls + 1, String.valueOf(ed.charAt(ls) == BOX ? CHECK : BOX));
                        }
                    }
                }
                return false;
            }
        });
        h.e = e;
        card.addView(e, new LinearLayout.LayoutParams(UI.M, UI.W));
        card.setTag(h);
        return card;
    }

    private void addTask(EditText t) {
        Editable ed = t.getText();
        int pos = Math.max(0, Math.min(t.getSelectionStart(), ed.length()));
        int ls = lineStart(ed, pos);
        if (!(ls < ed.length() && isBox(ed.charAt(ls)))) ed.insert(ls, BOX + " ");
        t.requestFocus();
        ((InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(t, 0);
    }

    static boolean isBox(char c) { return c == BOX || c == CHECK; }

    static int lineStart(CharSequence s, int pos) {
        int i = pos;
        while (i > 0 && s.charAt(i - 1) != '\n') i--;
        return i;
    }

    /** Saves edits and continues task lists when Enter is pressed. */
    private final class W implements TextWatcher {
        final Day d;
        final EditText e;
        int nl = -1;
        boolean busy;

        W(Day d, EditText e) { this.d = d; this.e = e; }

        @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }

        @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
            if (!busy && b == 0 && c == 1 && s.charAt(st) == '\n') nl = st;
        }

        @Override public void afterTextChanged(Editable ed) {
            if (busy) return;
            if (nl >= 0) {
                int p = nl;
                nl = -1;
                busy = true;
                int ls = lineStart(ed, p);
                if (p - ls >= 2 && isBox(ed.charAt(ls)) && ed.charAt(ls + 1) == ' ') {
                    if (p - ls == 2) {
                        ed.delete(ls, p + 1);              // empty task + Enter ends the list
                    } else {
                        ed.insert(p + 1, BOX + " ");       // next task
                        e.setSelection(p + 3);
                    }
                }
                busy = false;
            }
            d.text = ed.toString();
            pending.put(d.day, d.text);
            h.removeCallbacks(flushR);
            h.postDelayed(flushR, 500);
        }
    }

    private void flush() {
        h.removeCallbacks(flushR);
        for (Map.Entry<String, String> en : pending.entrySet()) Notes.write(a, en.getKey(), en.getValue());
        pending.clear();
    }

    /** A pomodoro was just logged into today's file: refresh that card. */
    private void reloadToday() {
        String t = Notes.today();
        for (int i = 0; i < days.size(); i++) {
            if (days.get(i).day.equals(t)) {
                days.get(i).text = Notes.read(a, t);
                rows.notifyItemChanged(i);
                return;
            }
        }
    }

    static String label(String day) {
        try {
            Date dt = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day);
            String s = new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(dt);
            return day.equals(Notes.today()) ? "Today \u00B7 " + s : s;
        } catch (Exception e) {
            return day;
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Notes.flusher = flushR;
        Notes.changed = reloadR;
    }

    @Override protected void onDetachedFromWindow() {
        flush();
        Notes.flusher = null;
        Notes.changed = null;
        super.onDetachedFromWindow();
    }
}
