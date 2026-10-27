package dev.focus.pomodoro;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Main page: timer, three round buttons, last PDF, player controller. */
final class HomePage extends ScrollView implements MainActivity.Refresh {
    private final MainActivity a;
    private TimerView tv;
    private ImageView play;
    private LinearLayout pdfCard;

    private final Runnable onState = new Runnable() { @Override public void run() { update(); } };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            update();
            postDelayed(this, 250);
        }
    };

    HomePage(MainActivity act) {
        super(act);
        a = act;
        setFillViewport(true);
        setBackgroundColor(UI.col(a, R.color.bg));
        int text = UI.col(a, R.color.text), muted = UI.col(a, R.color.text2);
        int surface = UI.col(a, R.color.surface), primary = UI.col(a, R.color.primary);

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(UI.dp(16), UI.dp(8), UI.dp(16), UI.dp(24));
        addView(col, new FrameLayout.LayoutParams(UI.M, UI.W));

        // header
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(UI.tv(a, "Focus", 24, text, true), new LinearLayout.LayoutParams(0, UI.W, 1f));
        ImageView gear = UI.ib(a, R.drawable.ic_settings, muted, 48, 12);
        gear.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showSettings(); } });
        head.addView(gear, new LinearLayout.LayoutParams(UI.dp(48), UI.dp(48)));
        col.addView(head, new LinearLayout.LayoutParams(UI.M, UI.W));

        // timer
        tv = new TimerView(a);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(UI.W, UI.W);
        tlp.gravity = Gravity.CENTER_HORIZONTAL;
        tlp.topMargin = UI.dp(4);
        col.addView(tv, tlp);

        // controls
        LinearLayout ctl = new LinearLayout(a);
        ctl.setGravity(Gravity.CENTER);
        ImageView reset = UI.circle(a, R.drawable.ic_reset, 48, surface, muted, 24);
        reset.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Pomo.reset(); } });
        play = UI.circle(a, R.drawable.ic_play, 72, primary, Color.WHITE, 36);
        play.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Pomo.toggle(); } });
        ImageView skip = UI.circle(a, R.drawable.ic_next, 48, surface, muted, 24);
        skip.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Pomo.skip(); } });
        LinearLayout.LayoutParams side = new LinearLayout.LayoutParams(UI.dp(48), UI.dp(48));
        side.setMargins(UI.dp(20), 0, UI.dp(20), 0);
        LinearLayout.LayoutParams mid = new LinearLayout.LayoutParams(UI.dp(72), UI.dp(72));
        ctl.addView(reset, side);
        ctl.addView(play, mid);
        ctl.addView(skip, new LinearLayout.LayoutParams(side));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(UI.M, UI.W);
        clp.topMargin = UI.dp(8);
        col.addView(ctl, clp);

        // three round buttons
        LinearLayout nav = new LinearLayout(a);
        nav.setGravity(Gravity.CENTER);
        nav.addView(navButton(R.drawable.ic_notes, "Notes", MainActivity.NOTES));
        nav.addView(navButton(R.drawable.ic_music, "Music", MainActivity.MUSIC));
        nav.addView(navButton(R.drawable.ic_pdf, "PDFs", MainActivity.PDFS));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(UI.M, UI.W);
        nlp.topMargin = UI.dp(28);
        col.addView(nav, nlp);

        // last pdf
        pdfCard = new LinearLayout(a);
        pdfCard.setOrientation(LinearLayout.VERTICAL);
        pdfCard.setPadding(UI.dp(16), UI.dp(12), UI.dp(16), UI.dp(12));
        pdfCard.setBackground(UI.ripple(UI.rect(surface, 12), false, 12));
        pdfCard.setElevation(UI.dp(2));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(UI.M, UI.W);
        plp.topMargin = UI.dp(28);
        col.addView(pdfCard, plp);

        // player
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(UI.M, UI.W);
        mlp.topMargin = UI.dp(12);
        col.addView(new MiniPlayer(a, new Runnable() { @Override public void run() { a.show(MainActivity.MUSIC); } }), mlp);

        refresh();
    }

    private View navButton(int icon, String label, final int target) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView b = UI.circle(a, icon, 64, UI.col(a, R.color.surface), UI.col(a, R.color.primary), 28);
        b.setContentDescription(label);
        b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { a.show(target); } });
        c.addView(b, new LinearLayout.LayoutParams(UI.dp(64), UI.dp(64)));
        TextView t = UI.tv(a, label, 12, UI.col(a, R.color.text2), false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(UI.W, UI.W);
        tl.topMargin = UI.dp(6);
        c.addView(t, tl);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UI.dp(88), UI.W);
        c.setLayoutParams(lp);
        return c;
    }

    /** Re-reads the "continue reading" card. */
    @Override public void refresh() {
        pdfCard.removeAllViews();
        final PdfLib.Doc d = PdfLib.lastRead();
        if (d == null) { pdfCard.setVisibility(View.GONE); return; }
        pdfCard.setVisibility(View.VISIBLE);
        TextView k = UI.tv(a, "CONTINUE READING", 11, UI.col(a, R.color.primary), true);
        TextView n = UI.tv(a, d.name, 16, UI.col(a, R.color.text), true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        TextView s = UI.tv(a, PdfLib.status(d), 13, UI.col(a, R.color.text2), false);
        pdfCard.addView(k);
        pdfCard.addView(n);
        pdfCard.addView(s);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(UI.M, UI.dp(3));
        bl.topMargin = UI.dp(8);
        pdfCard.addView(UI.bar(a, PdfLib.pct(d)), bl);
        pdfCard.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { a.openPdf(d.uri); } });
    }

    private void update() {
        boolean run = Pomo.running();
        long left = Pomo.left(), total = Pomo.total();
        if (run && left <= 0) { Pomo.finish(); return; }
        int ph = Pomo.phase();
        int color = UI.col(a, ph == Pomo.FOCUS ? R.color.primary : R.color.green);
        tv.set(total > 0 ? (float) left / total : 0f, UI.mmss(left), Pomo.phaseName(ph), Pomo.done(), Pomo.every(), color);
        play.setImageResource(run ? R.drawable.ic_pause : R.drawable.ic_play);
        setKeepScreenOn(run && Pomo.awake());
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Pomo.listeners.add(onState);
        refresh();
        post(tick);
    }

    @Override protected void onDetachedFromWindow() {
        Pomo.listeners.remove(onState);
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    // ---------- settings dialog ----------
    private EditText num(int v) {
        EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(String.valueOf(v));
        e.setGravity(Gravity.END);
        e.setMinWidth(UI.dp(64));
        e.setSelectAllOnFocus(true);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        return e;
    }

    private void addRow(LinearLayout l, String label, View v) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(UI.tv(a, label, 16, UI.col(a, R.color.text), false), new LinearLayout.LayoutParams(0, UI.W, 1f));
        r.addView(v, new LinearLayout.LayoutParams(UI.W, UI.W));
        l.addView(r, new LinearLayout.LayoutParams(UI.M, UI.W));
    }

    private Switch sw(LinearLayout l, String label, boolean on) {
        Switch s = new Switch(a);
        s.setText(label);
        s.setTextSize(16);
        s.setChecked(on);
        s.setPadding(0, UI.dp(10), 0, UI.dp(10));
        l.addView(s, new LinearLayout.LayoutParams(UI.M, UI.W));
        return s;
    }

    private static int val(EditText e, int def, int lo, int hi) {
        try {
            return Math.max(lo, Math.min(hi, Integer.parseInt(e.getText().toString().trim())));
        } catch (Exception x) {
            return def;
        }
    }

    private void showSettings() {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(UI.dp(24), UI.dp(8), UI.dp(24), UI.dp(8));
        final EditText f = num(Pomo.focusMin()), s = num(Pomo.shortMin()), lo = num(Pomo.longMin()), ev = num(Pomo.every());
        addRow(l, "Focus (min)", f);
        addRow(l, "Short break (min)", s);
        addRow(l, "Long break (min)", lo);
        addRow(l, "Sessions before long break", ev);
        final Switch auto = sw(l, "Auto-start next phase", Pomo.auto());
        final Switch pull = sw(l, "Pull me out of other apps when time is up", Pomo.pull());
        final Switch awake = sw(l, "Keep screen on while running", Pomo.awake());
        ScrollView sv = new ScrollView(a);
        sv.addView(l);
        new AlertDialog.Builder(a)
                .setTitle("Timer settings")
                .setView(sv)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Pomo.saveSettings(val(f, 25, 1, 180), val(s, 5, 1, 180), val(lo, 15, 1, 180), val(ev, 4, 1, 12),
                                auto.isChecked(), pull.isChecked(), awake.isChecked());
                        if (pull.isChecked()) a.ensureOverlay();
                    }
                }).show();
    }
}
