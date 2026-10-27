package dev.focus.pomodoro;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.provider.AlarmClock;
import android.text.format.DateFormat;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Main page: timer, three round buttons, last PDF, player controller. */
final class HomePage extends ScrollView implements MainActivity.Refresh {
    private final MainActivity a;
    private TimerView tv;
    private ImageView play;
    private LinearLayout pdfCard;
    private LinearLayout datePanel, timerPanel, examPanel, shortcutRow;
    private TextView dateText, clockText, examTime;
    private final TextView[] tabViews = new TextView[3];
    private EditText examMinutes;
    private int selectedTab = 1;

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
        gear.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { a.show(MainActivity.SETTINGS); } });
        head.addView(gear, new LinearLayout.LayoutParams(UI.dp(48), UI.dp(48)));
        col.addView(head, new LinearLayout.LayoutParams(UI.M, UI.W));

        LinearLayout tabs = new LinearLayout(a);
        tabs.setGravity(Gravity.CENTER);
        tabs.setBackground(UI.rect(surface, 10));
        tabs.addView(tab("Clock & date", 1), new LinearLayout.LayoutParams(0, UI.dp(44), 1f));
        tabs.addView(tab("Pomodoro", 0), new LinearLayout.LayoutParams(0, UI.dp(44), 1f));
        tabs.addView(tab("Exam timer", 2), new LinearLayout.LayoutParams(0, UI.dp(44), 1f));
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(UI.M, UI.W);
        tabLp.topMargin = UI.dp(12);
        col.addView(tabs, tabLp);

        datePanel = new LinearLayout(a);
        datePanel.setOrientation(LinearLayout.VERTICAL);
        datePanel.setGravity(Gravity.CENTER);
        dateText = UI.tv(a, "", 18, muted, false);
        dateText.setGravity(Gravity.CENTER);
        clockText = UI.tv(a, "", 56, text, true);
        clockText.setGravity(Gravity.CENTER);
        datePanel.addView(clockText, new LinearLayout.LayoutParams(UI.M, UI.W));
        datePanel.addView(dateText, new LinearLayout.LayoutParams(UI.M, UI.W));
        col.addView(datePanel, new LinearLayout.LayoutParams(UI.M, UI.dp(260)));

        // timer
        timerPanel = new LinearLayout(a);
        timerPanel.setOrientation(LinearLayout.VERTICAL);
        tv = new TimerView(a);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(UI.W, UI.W);
        tlp.gravity = Gravity.CENTER_HORIZONTAL;
        tlp.topMargin = UI.dp(4);
        timerPanel.addView(tv, tlp);

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
        timerPanel.addView(ctl, clp);
        col.addView(timerPanel, new LinearLayout.LayoutParams(UI.M, UI.W));

        examPanel = buildExamPanel();
        col.addView(examPanel, new LinearLayout.LayoutParams(UI.M, UI.dp(260)));
        setTab(selectedTab);

        // three round buttons
        LinearLayout nav = new LinearLayout(a);
        nav.setGravity(Gravity.CENTER);
        nav.addView(navButton(R.drawable.ic_notes, "Notes", MainActivity.NOTES));
        nav.addView(navButton(R.drawable.ic_music, "Music", MainActivity.MUSIC));
        nav.addView(navButton(R.drawable.ic_pdf, "PDFs", MainActivity.PDFS));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(UI.M, UI.W);
        nlp.topMargin = UI.dp(28);
        col.addView(nav, nlp);

        shortcutRow = new LinearLayout(a);
        shortcutRow.setGravity(Gravity.CENTER);
        shortcutRow.addView(quickButton(android.R.drawable.ic_lock_idle_alarm, "Clock", new Runnable() {
            @Override public void run() { openSystemApp(Intent.CATEGORY_APP_CLOCK, AlarmClock.ACTION_SHOW_ALARMS); }
        }));
        shortcutRow.addView(quickButton(android.R.drawable.ic_menu_manage, "Calculator", new Runnable() {
            @Override public void run() { openSystemApp(Intent.CATEGORY_APP_CALCULATOR, null); }
        }));
        shortcutRow.addView(quickButton(android.R.drawable.ic_menu_my_calendar, "Calendar", new Runnable() {
            @Override public void run() { openSystemApp(Intent.CATEGORY_APP_CALENDAR, null); }
        }));
        shortcutRow.addView(quickButton(android.R.drawable.ic_menu_sort_by_size, "All apps", new Runnable() {
            @Override public void run() { a.show(MainActivity.APP_DRAWER); }
        }));
        ImageView addShortcut = UI.circle(a, R.drawable.ic_add, 48, surface, primary, 24);
        addShortcut.setContentDescription("Add app shortcut");
        addShortcut.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { chooseShortcut(); } });
        shortcutRow.addView(addShortcut, new LinearLayout.LayoutParams(UI.dp(48), UI.dp(48)));
        HorizontalScrollView shortcutScroll = new HorizontalScrollView(a);
        shortcutScroll.setHorizontalScrollBarEnabled(false);
        shortcutScroll.addView(shortcutRow, new HorizontalScrollView.LayoutParams(UI.W, UI.M));
        LinearLayout.LayoutParams shortcutLp = new LinearLayout.LayoutParams(UI.M, UI.dp(64));
        shortcutLp.topMargin = UI.dp(12);
        col.addView(shortcutScroll, shortcutLp);
        renderShortcuts();

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

    private View tab(final String label, final int tab) {
        TextView view = UI.tv(a, label, 13, UI.col(a, R.color.text2), true);
        view.setGravity(Gravity.CENTER);
        tabViews[tab] = view;
        view.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setTab(tab); } });
        return view;
    }

    private void setTab(int tab) {
        selectedTab = tab;
        datePanel.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        timerPanel.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        examPanel.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < tabViews.length; i++) {
            tabViews[i].setTextColor(UI.col(a, i == tab ? R.color.surface : R.color.text2));
            tabViews[i].setBackground(i == tab ? UI.ripple(UI.rect(UI.col(a, R.color.primary), 8), false, 8) : null);
        }
    }

    private LinearLayout buildExamPanel() {
        LinearLayout panel = new LinearLayout(a);
        panel.setGravity(Gravity.CENTER);
        panel.setOrientation(LinearLayout.VERTICAL);
        examTime = UI.tv(a, "00:00", 48, UI.col(a, R.color.text), true);
        examTime.setGravity(Gravity.CENTER);
        panel.addView(examTime);
        examMinutes = new EditText(a);
        examMinutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        examMinutes.setHint("Exam duration in minutes");
        examMinutes.setText(String.valueOf(a.getSharedPreferences("exam", 0).getInt("minutes", 60)));
        examMinutes.setGravity(Gravity.CENTER);
        panel.addView(examMinutes, new LinearLayout.LayoutParams(UI.dp(220), UI.W));
        TextView action = UI.tv(a, "Start / pause exam", 15, UI.col(a, R.color.primary), true);
        action.setGravity(Gravity.CENTER);
        action.setPadding(UI.dp(16), UI.dp(12), UI.dp(16), UI.dp(12));
        action.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleExam(); }
        });
        panel.addView(action);
        return panel;
    }

    private void toggleExam() {
        android.content.SharedPreferences prefs = a.getSharedPreferences("exam", 0);
        long end = prefs.getLong("end", 0);
        boolean running = prefs.getBoolean("running", false);
        if (running) {
            prefs.edit().putLong("remaining", Math.max(0, end - System.currentTimeMillis())).putBoolean("running", false).apply();
            ExamTimer.cancel(a);
        } else {
            int minutes;
            try { minutes = Math.max(1, Math.min(600, Integer.parseInt(examMinutes.getText().toString()))); }
            catch (Exception ignored) { minutes = 60; }
            long left = prefs.getLong("remaining", minutes * 60000L);
            if (left <= 0) left = minutes * 60000L;
                long endAt = System.currentTimeMillis() + left;
                prefs.edit().putInt("minutes", minutes).putLong("end", endAt)
                    .putBoolean("running", true).apply();
                ExamTimer.schedule(a, endAt);
        }
        updateExam();
    }

    private void updateExam() {
        android.content.SharedPreferences prefs = a.getSharedPreferences("exam", 0);
        long remaining = prefs.getBoolean("running", false)
                ? Math.max(0, prefs.getLong("end", 0) - System.currentTimeMillis())
                : prefs.getLong("remaining", prefs.getInt("minutes", 60) * 60000L);
        if (prefs.getBoolean("running", false) && remaining == 0) {
            prefs.edit().putBoolean("running", false).putLong("remaining", 0).apply();
        }
        examTime.setText(UI.mmss(remaining));
    }

    private View quickButton(int icon, String label, final Runnable action) {
        ImageView button = UI.ib(a, icon, UI.col(a, R.color.text2), 44, 11);
        button.setContentDescription(label);
        button.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { action.run(); } });
        return button;
    }

    private void openSystemApp(String category, String fallbackAction) {
        PackageManager pm = a.getPackageManager();
        Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(category);
        ResolveInfo result = pm.resolveActivity(launch, 0);
        if (result != null) {
            launch.setClassName(result.activityInfo.packageName, result.activityInfo.name);
            try { a.startActivity(launch); return; } catch (Exception ignored) { }
        }
        if (fallbackAction != null && fallbackAction.startsWith("android.intent")) {
            try { a.startActivity(new Intent(fallbackAction)); return; } catch (Exception ignored) { }
        }
        if (fallbackAction != null && fallbackAction.startsWith("content:")) {
            try { a.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(fallbackAction))); return; } catch (Exception ignored) { }
        }
        a.toast(labelFor(category) + " app not found");
    }

    private String labelFor(String category) {
        return category.equals(Intent.CATEGORY_APP_CLOCK) ? "Clock"
                : category.equals(Intent.CATEGORY_APP_CALCULATOR) ? "Calculator" : "Calendar";
    }

    private void chooseShortcut() {
        final PackageManager pm = a.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> apps = pm.queryIntentActivities(query, 0);
        java.util.Collections.sort(apps, new ResolveInfo.DisplayNameComparator(pm));
        final String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i).loadLabel(pm).toString();
        new AlertDialog.Builder(a).setTitle("Add app shortcut").setItems(labels, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                String pkg = apps.get(which).activityInfo.packageName;
                android.content.SharedPreferences p = a.getSharedPreferences("shortcuts", 0);
                java.util.Set<String> items = new java.util.HashSet<>(p.getStringSet("packages", java.util.Collections.<String>emptySet()));
                items.add(pkg);
                p.edit().putStringSet("packages", items).apply();
                renderShortcuts();
            }
        }).show();
    }

    private void renderShortcuts() {
        if (shortcutRow == null) return;
        while (shortcutRow.getChildCount() > 5) shortcutRow.removeViewAt(4);
        android.content.SharedPreferences p = a.getSharedPreferences("shortcuts", 0);
        java.util.ArrayList<String> packages = new java.util.ArrayList<>(
            p.getStringSet("packages", java.util.Collections.<String>emptySet()));
        java.util.Collections.sort(packages);
        for (String pkg : packages) {
            try {
                ApplicationInfo info = a.getPackageManager().getApplicationInfo(pkg, 0);
                ImageView icon = new ImageView(a);
                icon.setImageDrawable(a.getPackageManager().getApplicationIcon(info));
                icon.setPadding(UI.dp(10), UI.dp(10), UI.dp(10), UI.dp(10));
                icon.setContentDescription(a.getPackageManager().getApplicationLabel(info));
                icon.setOnClickListener(v -> {
                    Intent launch = a.getPackageManager().getLaunchIntentForPackage(pkg);
                    if (launch != null) a.startActivity(launch);
                });
                shortcutRow.addView(icon, 4, new LinearLayout.LayoutParams(UI.dp(48), UI.dp(48)));
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
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
        if (dateText != null) {
            Date now = new Date();
            clockText.setText(DateFormat.getTimeFormat(a).format(now));
            dateText.setText(new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(now));
            updateExam();
        }
        boolean run = Pomo.running();
        long left = Pomo.left(), total = Pomo.total();
        if (run && left <= 0) { Pomo.finish(); return; }
        int ph = Pomo.phase();
        int color = UI.col(a, ph == Pomo.FOCUS ? R.color.primary : R.color.green);
        tv.set(total > 0 ? (float) left / total : 0f, UI.mmss(left), Pomo.phaseName(ph), Pomo.done(), Pomo.every(), color);
        play.setImageResource(run ? R.drawable.ic_pause : R.drawable.ic_play);
        setKeepScreenOn(false);
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

}
