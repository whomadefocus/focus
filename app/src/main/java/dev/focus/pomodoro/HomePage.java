package dev.focus.pomodoro;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.text.format.DateFormat;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.tabs.TabLayout;

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
    private LinearLayout datePanel, timerPanel, examPanel, todayTasks;
    private GridLayout shortcutsGrid;
    private TextView dateText, clockText;
    private TimerView examTimerView;
    private ImageView examPlay;
    private EditText examMinutes;
    private TextView examEnd;
    private MaterialButtonToggleGroup examPresets;
    private int selectedTab = 0;

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

        TabLayout tabs = new TabLayout(a);
        tabs.setTabMode(TabLayout.MODE_FIXED);
        tabs.setTabGravity(TabLayout.GRAVITY_FILL);
        tabs.setBackgroundColor(surface);
        tabs.setSelectedTabIndicatorColor(primary);
        tabs.setTabTextColors(muted, primary);
        tabs.addTab(tabs.newTab().setText("Clock"));
        tabs.addTab(tabs.newTab().setText("Pomodoro"));
        tabs.addTab(tabs.newTab().setText("Timer"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { setTab(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(UI.M, UI.W);
        tabLp.topMargin = UI.dp(12);
        tabLp.leftMargin = -UI.dp(16);
        tabLp.rightMargin = -UI.dp(16);
        col.addView(tabs, tabLp);

        datePanel = new LinearLayout(a);
        datePanel.setOrientation(LinearLayout.VERTICAL);
        datePanel.setGravity(Gravity.CENTER);
        dateText = UI.tv(a, "", 17, muted, false);
        dateText.setGravity(Gravity.CENTER);
        dateText.setContentDescription("Open calendar");
        dateText.setOnClickListener(v -> openCalendar());
        clockText = UI.tv(a, "", 58, text, true);
        clockText.setGravity(Gravity.CENTER);
        clockText.setContentDescription("Open clock");
        clockText.setOnClickListener(v -> openClock());
        datePanel.addView(clockText, new LinearLayout.LayoutParams(UI.M, UI.W));
        datePanel.addView(dateText, new LinearLayout.LayoutParams(UI.M, UI.W));
        col.addView(datePanel, new LinearLayout.LayoutParams(UI.M, UI.dp(250)));

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
        col.addView(examPanel, new LinearLayout.LayoutParams(UI.M, UI.W));
        setTab(selectedTab);

        addSectionTitle(col, "Shortcuts", "add", new Runnable() { @Override public void run() { chooseShortcut(); } });
        shortcutsGrid = new GridLayout(a);
        shortcutsGrid.setColumnCount(3);
        shortcutsGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        addGridTile("checklist", "Tasks", new Runnable() { @Override public void run() { a.show(MainActivity.TASKS); } });
        addGridTile("music_note", "Music", new Runnable() { @Override public void run() { a.show(MainActivity.MUSIC); } });
        addGridTile("picture_as_pdf", "PDFs", new Runnable() { @Override public void run() { a.show(MainActivity.PDFS); } });
        addGridTile("description", "Notes", new Runnable() { @Override public void run() { a.show(MainActivity.NOTES); } });
        addGridTile("alarm", "Clock", new Runnable() { @Override public void run() { openClock(); } });
        addGridTile("calculate", "Calculator", new Runnable() { @Override public void run() { openSystemApp(Intent.CATEGORY_APP_CALCULATOR); } });
        addGridTile("calendar_month", "Calendar", new Runnable() { @Override public void run() { openCalendar(); } });
        addGridTile("apps", "All apps", new Runnable() { @Override public void run() { a.show(MainActivity.APP_DRAWER); } });
        addGridTile("add", "Add shortcut", new Runnable() { @Override public void run() { chooseShortcut(); } });
        renderShortcuts();
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(UI.M, UI.W);
        gridLp.topMargin = UI.dp(2);
        col.addView(shortcutsGrid, gridLp);

        addSectionTitle(col, "Today's tasks", "edit_note", new Runnable() { @Override public void run() { a.show(MainActivity.TASKS); } });
        todayTasks = new LinearLayout(a);
        todayTasks.setOrientation(LinearLayout.VERTICAL);
        todayTasks.setPadding(UI.dp(12), UI.dp(6), UI.dp(12), UI.dp(6));
        todayTasks.setBackground(UI.rect(surface, 10));
        col.addView(todayTasks, new LinearLayout.LayoutParams(UI.M, UI.W));

        // last pdf
        pdfCard = new LinearLayout(a);
        pdfCard.setOrientation(LinearLayout.VERTICAL);
        pdfCard.setPadding(UI.dp(16), UI.dp(12), UI.dp(16), UI.dp(12));
        pdfCard.setBackground(UI.ripple(UI.rect(surface, 12), false, 12));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(UI.M, UI.W);
        plp.topMargin = UI.dp(28);
        col.addView(pdfCard, plp);

        // player
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(UI.M, UI.W);
        mlp.topMargin = UI.dp(12);
        col.addView(new MiniPlayer(a, new Runnable() { @Override public void run() { a.show(MainActivity.MUSIC); } }), mlp);

        refresh();
    }

    private void setTab(int tab) {
        selectedTab = tab;
        datePanel.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        timerPanel.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        examPanel.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
    }

    private void addSectionTitle(LinearLayout parent, String title, String actionIcon, final Runnable action) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = UI.tv(a, title, 18, UI.col(a, R.color.text), true);
        row.addView(label, new LinearLayout.LayoutParams(0, UI.dp(48), 1f));
        TextView button = UI.symbolButton(a, actionIcon, UI.col(a, R.color.primary), 44, 24);
        button.setContentDescription(title.equals("Shortcuts") ? "Add app shortcut" : "Open notes and tasks");
        button.setOnClickListener(v -> action.run());
        row.addView(button);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UI.M, UI.W);
        lp.topMargin = UI.dp(16);
        parent.addView(row, lp);
    }

    private void addGridTile(String iconName, String label, final Runnable action) {
        LinearLayout tile = new LinearLayout(a);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(UI.ripple(UI.rect(UI.col(a, R.color.surface), 10), false, 10));
        tile.addView(UI.symbol(a, iconName, 27, UI.col(a, R.color.primary)),
                new LinearLayout.LayoutParams(UI.M, UI.dp(34)));
        TextView text = UI.tv(a, label, 12, UI.col(a, R.color.text), true);
        text.setGravity(Gravity.CENTER);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(UI.M, UI.W);
        textLp.topMargin = UI.dp(4);
        tile.addView(text, textLp);
        tile.setContentDescription(label);
        tile.setOnClickListener(v -> action.run());
        addGridChild(tile);
    }

    private void addAppGridTile(final String pkg) {
        try {
            final ApplicationInfo info = a.getPackageManager().getApplicationInfo(pkg, 0);
            LinearLayout tile = new LinearLayout(a);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setBackground(UI.ripple(UI.rect(UI.col(a, R.color.surface), 10), false, 10));
            ImageView icon = new ImageView(a);
            icon.setImageDrawable(a.getPackageManager().getApplicationIcon(info));
            tile.addView(icon, new LinearLayout.LayoutParams(UI.dp(32), UI.dp(32)));
            TextView label = UI.tv(a, a.getPackageManager().getApplicationLabel(info), 12, UI.col(a, R.color.text), true);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(UI.M, UI.W);
            labelLp.topMargin = UI.dp(4);
            tile.addView(label, labelLp);
            tile.setOnClickListener(v -> {
                Intent launch = a.getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch != null) a.startActivity(launch);
            });
            tile.setOnLongClickListener(v -> {
                android.content.SharedPreferences prefs = a.getSharedPreferences("shortcuts", 0);
                java.util.Set<String> items = new java.util.HashSet<>(prefs.getStringSet("packages", java.util.Collections.<String>emptySet()));
                items.remove(pkg);
                prefs.edit().putStringSet("packages", items).apply();
                renderShortcuts();
                return true;
            });
            addGridChild(tile);
        } catch (PackageManager.NameNotFoundException ignored) { }
    }

    private void addGridChild(View tile) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = UI.dp(88);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(UI.dp(4), UI.dp(4), UI.dp(4), UI.dp(4));
        shortcutsGrid.addView(tile, lp);
    }

    private void renderTodayTasks() {
        todayTasks.removeAllViews();
        List<TaskStore.Task> tasks = TaskStore.forDay(a, Notes.today());
        if (tasks.isEmpty()) {
            TextView empty = UI.tv(a, "No tasks planned for today", 14, UI.col(a, R.color.text2), false);
            empty.setPadding(UI.dp(4), UI.dp(10), UI.dp(4), UI.dp(10));
            todayTasks.addView(empty);
            return;
        }
        int completed = 0;
        for (TaskStore.Task task : tasks) if (TaskStore.doneOn(task, Notes.today())) completed++;
        TextView progress = UI.tv(a, completed + " of " + tasks.size() + " completed", 12, UI.col(a, R.color.text2), false);
        progress.setPadding(UI.dp(4), UI.dp(4), UI.dp(4), UI.dp(6));
        todayTasks.addView(progress);
        for (final TaskStore.Task task : tasks) {
            CheckBox check = new CheckBox(a);
            check.setText(task.title);
            check.setTextColor(UI.col(a, R.color.text));
            check.setTextSize(14);
            check.setChecked(TaskStore.doneOn(task, Notes.today()));
            check.setOnCheckedChangeListener((button, checked) -> {
                TaskStore.setDoneForDay(a, task, Notes.today(), checked);
                renderTodayTasks();
            });
            todayTasks.addView(check, new LinearLayout.LayoutParams(UI.M, UI.W));
        }
    }

    private LinearLayout buildExamPanel() {
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(UI.dp(12), UI.dp(8), UI.dp(12), UI.dp(12));
        panel.setBackground(UI.rect(UI.col(a, R.color.surface), 12));
        TextView heading = UI.tv(a, "TIMER SESSION", 12, UI.col(a, R.color.primary), true);
        heading.setGravity(Gravity.CENTER);
        panel.addView(heading, new LinearLayout.LayoutParams(UI.M, UI.dp(28)));
        examTimerView = new TimerView(a);
        LinearLayout.LayoutParams timerLp = new LinearLayout.LayoutParams(UI.W, UI.W);
        timerLp.gravity = Gravity.CENTER_HORIZONTAL;
        panel.addView(examTimerView, timerLp);
        LinearLayout durationRow = new LinearLayout(a);
        durationRow.setGravity(Gravity.CENTER);
        durationRow.addView(UI.tv(a, "Duration", 14, UI.col(a, R.color.text2), false));
        examMinutes = new EditText(a);
        examMinutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        examMinutes.setHint("30");
        examMinutes.setSelectAllOnFocus(true);
        examMinutes.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(3)});
        examMinutes.setText(String.valueOf(a.getSharedPreferences("exam", 0).getInt("minutes", 30)));
        examMinutes.setGravity(Gravity.CENTER);
        examMinutes.setTextColor(UI.col(a, R.color.text));
        examMinutes.setHintTextColor(UI.col(a, R.color.text2));
        examMinutes.setBackground(UI.rect(UI.col(a, R.color.bg), 8));
        LinearLayout.LayoutParams minutesLp = new LinearLayout.LayoutParams(UI.dp(76), UI.dp(52));
        minutesLp.leftMargin = UI.dp(12);
        durationRow.addView(examMinutes, minutesLp);
        TextView minLabel = UI.tv(a, "min", 14, UI.col(a, R.color.text2), false);
        LinearLayout.LayoutParams minLp = new LinearLayout.LayoutParams(UI.W, UI.W);
        minLp.leftMargin = UI.dp(6);
        durationRow.addView(minLabel, minLp);
        panel.addView(durationRow);

        examPresets = new MaterialButtonToggleGroup(a);
        examPresets.setSingleSelection(true);
        examPresets.setSelectionRequired(false);
        examPresets.setGravity(Gravity.CENTER);
        int[] presets = {15, 30, 45, 60};
        int storedMinutes = a.getSharedPreferences("exam", 0).getInt("minutes", 30);
        for (final int value : presets) {
            MaterialButton preset = new MaterialButton(a, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            preset.setId(View.generateViewId());
            preset.setText(String.valueOf(value));
            preset.setTextSize(13);
            preset.setInsetTop(0);
            preset.setInsetBottom(0);
            preset.setMinWidth(UI.dp(52));
            preset.setPadding(UI.dp(8), 0, UI.dp(8), 0);
            preset.setTag(Integer.valueOf(value));
            examPresets.addView(preset, new LinearLayout.LayoutParams(UI.W, UI.dp(40)));
            if (value == storedMinutes) examPresets.check(preset.getId());
        }
        LinearLayout.LayoutParams presetLp = new LinearLayout.LayoutParams(UI.M, UI.W);
        presetLp.topMargin = UI.dp(6);
        panel.addView(examPresets, presetLp);
        examPresets.addOnButtonCheckedListener(new MaterialButtonToggleGroup.OnButtonCheckedListener() {
            @Override public void onButtonChecked(MaterialButtonToggleGroup group, int checkedId, boolean checked) {
                if (!checked) return;
                MaterialButton selected = group.findViewById(checkedId);
                if (selected == null) return;
                int minutes = ((Integer) selected.getTag()).intValue();
                examMinutes.setText(String.valueOf(minutes));
                if (!a.getSharedPreferences("exam", 0).getBoolean("running", false)) resetExam();
            }
        });
        examMinutes.setOnFocusChangeListener((view, focused) -> {
            if (!focused) syncExamDuration();
        });
        examEnd = UI.tv(a, "Ready when you are", 12, UI.col(a, R.color.text2), false);
        examEnd.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams endLp = new LinearLayout.LayoutParams(UI.M, UI.W);
        endLp.topMargin = UI.dp(4);
        panel.addView(examEnd, endLp);

        LinearLayout controls = new LinearLayout(a);
        controls.setGravity(Gravity.CENTER);
        ImageView reset = UI.circle(a, R.drawable.ic_reset, 48, UI.col(a, R.color.surface), UI.col(a, R.color.text2), 24);
        reset.setContentDescription("Reset timer");
        reset.setOnClickListener(v -> resetExam());
        examPlay = UI.circle(a, R.drawable.ic_play, 72, UI.col(a, R.color.primary), Color.WHITE, 36);
        examPlay.setContentDescription("Start or pause timer");
        examPlay.setOnClickListener(v -> toggleExam());
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(UI.dp(48), UI.dp(48));
        resetLp.setMargins(UI.dp(24), UI.dp(12), UI.dp(24), UI.dp(12));
        controls.addView(reset, resetLp);
        controls.addView(examPlay, new LinearLayout.LayoutParams(UI.dp(72), UI.dp(72)));
        panel.addView(controls);
        updateExam();
        return panel;
    }

    private void syncExamDuration() {
        if (examMinutes == null || a.getSharedPreferences("exam", 0).getBoolean("running", false)) return;
        int minutes;
        try { minutes = Math.max(1, Math.min(600, Integer.parseInt(examMinutes.getText().toString().trim()))); }
        catch (Exception ignored) { minutes = 30; }
        a.getSharedPreferences("exam", 0).edit().putInt("minutes", minutes)
                .putLong("remaining", minutes * 60000L).putBoolean("running", false).apply();
        if (examPresets != null) {
            boolean matching = false;
            for (int i = 0; i < examPresets.getChildCount(); i++) {
                View child = examPresets.getChildAt(i);
                if (child instanceof MaterialButton && ((Integer) child.getTag()).intValue() == minutes) {
                    examPresets.check(child.getId());
                    matching = true;
                    break;
                }
            }
            if (!matching) examPresets.clearChecked();
        }
        updateExam();
    }

    private void toggleExam() {
        android.content.SharedPreferences prefs = a.getSharedPreferences("exam", 0);
        long end = prefs.getLong("end", 0);
        boolean running = prefs.getBoolean("running", false);
        if (running) {
            prefs.edit().putLong("remaining", Math.max(0, end - System.currentTimeMillis()))
                    .putBoolean("running", false).putBoolean("active", true).apply();
            ExamTimer.cancel(a);
        } else {
            int minutes;
            try { minutes = Math.max(1, Math.min(600, Integer.parseInt(examMinutes.getText().toString()))); }
            catch (Exception ignored) { minutes = 30; }
            long left = prefs.getLong("remaining", 0);
            if (left <= 0 || minutes != prefs.getInt("minutes", 30)) left = minutes * 60000L;
            long endAt = System.currentTimeMillis() + left;
            prefs.edit().putInt("minutes", minutes).putLong("remaining", left).putLong("end", endAt)
                    .putBoolean("running", true).putBoolean("active", true).apply();
            ExamTimer.schedule(a, endAt);
        }
        updateExam();
    }

    private void resetExam() {
        int minutes;
        try { minutes = Math.max(1, Math.min(600, Integer.parseInt(examMinutes.getText().toString().trim()))); }
        catch (Exception ignored) { minutes = 30; }
        ExamTimer.cancel(a);
        a.getSharedPreferences("exam", 0).edit().putInt("minutes", minutes)
            .putLong("remaining", minutes * 60000L).putBoolean("running", false).putBoolean("active", true).apply();
        updateExam();
    }

    private void updateExam() {
        android.content.SharedPreferences prefs = a.getSharedPreferences("exam", 0);
        long remaining = prefs.getBoolean("running", false)
                ? Math.max(0, prefs.getLong("end", 0) - System.currentTimeMillis())
                : prefs.getLong("remaining", prefs.getInt("minutes", 30) * 60000L);
        boolean running = prefs.getBoolean("running", false);
        if (running && remaining == 0) {
            ExamReceiver.finish(a);
            running = false;
        }
        int minutes = Math.max(1, prefs.getInt("minutes", 30));
        long total = minutes * 60000L;
        examTimerView.set(total > 0 ? (float) remaining / total : 0f, UI.mmss(remaining), "Timer", 0, 0,
                UI.col(a, R.color.primary));
        if (examPlay != null) examPlay.setImageResource(running ? R.drawable.ic_pause : R.drawable.ic_play);
        if (examMinutes != null) examMinutes.setEnabled(!running);
        if (examPresets != null) {
            examPresets.setEnabled(!running);
            for (int i = 0; i < examPresets.getChildCount(); i++) examPresets.getChildAt(i).setEnabled(!running);
        }
        if (examEnd != null) {
            examEnd.setText(running ? "Finishes at " + DateFormat.getTimeFormat(a).format(new Date(prefs.getLong("end", 0)))
                : remaining == 0 ? "Session complete" : "Ready when you are");
        }
    }

    private void openSystemApp(String category) {
        PackageManager pm = a.getPackageManager();
        Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> apps = pm.queryIntentActivities(launch, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo app : apps) {
            try {
                launch.setClassName(app.activityInfo.packageName, app.activityInfo.name);
                a.startActivity(launch);
                return;
            } catch (Exception ignored) { }
        }
        try { a.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(category)); return; } catch (Exception ignored) { }
        a.toast(labelFor(category) + " app not found");
    }

    private void openClock() {
        try { a.startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS)); }
        catch (Exception ignored) { openSystemApp("android.intent.category.APP_ALARM"); }
    }

    private void openCalendar() {
        openSystemApp(Intent.CATEGORY_APP_CALENDAR);
    }

    private String labelFor(String category) {
        return category.equals("android.intent.category.APP_ALARM") ? "Clock"
                : category.equals(Intent.CATEGORY_APP_CALCULATOR) ? "Calculator" : "Calendar";
    }

    private void chooseShortcut() {
        a.show(MainActivity.APP_PICKER);
    }

    private void renderShortcuts() {
        if (shortcutsGrid == null) return;
        while (shortcutsGrid.getChildCount() > 9) shortcutsGrid.removeViewAt(9);
        android.content.SharedPreferences p = a.getSharedPreferences("shortcuts", 0);
        java.util.ArrayList<String> packages = new java.util.ArrayList<>(
            p.getStringSet("packages", java.util.Collections.<String>emptySet()));
        java.util.Collections.sort(packages);
        for (String pkg : packages) addAppGridTile(pkg);
    }

    /** Re-reads the "continue reading" card. */
    @Override public void refresh() {
        renderTodayTasks();
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
