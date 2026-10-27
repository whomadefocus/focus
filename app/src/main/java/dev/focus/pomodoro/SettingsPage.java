package dev.focus.pomodoro;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Full-page settings for timer, reader, and app behavior. */
final class SettingsPage extends LinearLayout {
    private final MainActivity a;

    SettingsPage(MainActivity activity) {
        super(activity);
        a = activity;
        setOrientation(VERTICAL);
        setBackgroundColor(UI.col(a, R.color.bg));
        addView(UI.topBar(a, "Settings", new View.OnClickListener() {
            @Override public void onClick(View v) { a.back(); }
        }), new LinearLayout.LayoutParams(UI.M, UI.W));

        ScrollView scroll = new ScrollView(a);
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(VERTICAL);
        content.setPadding(UI.dp(20), UI.dp(16), UI.dp(20), UI.dp(32));
        scroll.addView(content);
        addView(scroll, new LinearLayout.LayoutParams(UI.M, 0, 1f));

        section(content, "POMODORO");
        final EditText focus = number(Pomo.focusMin());
        final EditText shortBreak = number(Pomo.shortMin());
        final EditText longBreak = number(Pomo.longMin());
        final EditText every = number(Pomo.every());
        row(content, "Focus (minutes)", focus);
        row(content, "Short break (minutes)", shortBreak);
        row(content, "Long break (minutes)", longBreak);
        row(content, "Sessions before long break", every);
        final Switch auto = toggle("Auto-start next phase", Pomo.auto());
        final Switch pull = toggle("Open Focus when a timer ends", Pomo.pull());
        content.addView(auto);
        content.addView(pull);

        section(content, "PDF READER");
        final Switch keepScreen = toggle("Keep screen on while reading", pdfKeepScreen(a));
        content.addView(keepScreen);
        keepScreen.setOnCheckedChangeListener((button, checked) -> a.getSharedPreferences("reader", Context.MODE_PRIVATE)
                .edit().putBoolean("keep_screen", checked).apply());

        section(content, "APP MANAGEMENT");
        TextView screenTime = UI.tv(a, "Screen time, app limits and launcher", 16, UI.col(a, R.color.primary), true);
        screenTime.setGravity(Gravity.CENTER_VERTICAL);
        screenTime.setPadding(UI.dp(4), UI.dp(14), UI.dp(4), UI.dp(14));
        screenTime.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { a.show(MainActivity.SCREEN_TIME); }
        });
        content.addView(screenTime, new LinearLayout.LayoutParams(UI.M, UI.W));

        TextView save = UI.tv(a, "Save timer settings", 16, UI.col(a, R.color.primary), true);
        save.setGravity(Gravity.CENTER);
        save.setPadding(0, UI.dp(16), 0, UI.dp(16));
        save.setBackground(UI.ripple(UI.rect(UI.col(a, R.color.surface), 8), false, 8));
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Pomo.saveSettings(value(focus, 25, 1, 180), value(shortBreak, 5, 1, 180),
                        value(longBreak, 15, 1, 180), value(every, 4, 1, 12), auto.isChecked(), pull.isChecked(), Pomo.awake());
                if (pull.isChecked()) a.ensureOverlay();
                a.toast("Settings saved");
            }
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(UI.M, UI.W);
        saveLp.topMargin = UI.dp(20);
        content.addView(save, saveLp);
    }

    static boolean pdfKeepScreen(Context context) {
        return context.getSharedPreferences("reader", Context.MODE_PRIVATE).getBoolean("keep_screen", true);
    }

    private void section(LinearLayout parent, String title) {
        TextView label = UI.tv(a, title, 12, UI.col(a, R.color.primary), true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UI.M, UI.W);
        lp.topMargin = UI.dp(12);
        lp.bottomMargin = UI.dp(8);
        parent.addView(label, lp);
    }

    private EditText number(int value) {
        EditText edit = new EditText(a);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER);
        edit.setText(String.valueOf(value));
        edit.setGravity(Gravity.END);
        edit.setMinWidth(UI.dp(72));
        edit.setSelectAllOnFocus(true);
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        return edit;
    }

    private void row(LinearLayout parent, String title, View value) {
        LinearLayout line = new LinearLayout(a);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(UI.tv(a, title, 16, UI.col(a, R.color.text), false),
                new LinearLayout.LayoutParams(0, UI.W, 1f));
        line.addView(value);
        parent.addView(line, new LinearLayout.LayoutParams(UI.M, UI.W));
    }

    private Switch toggle(String title, boolean checked) {
        Switch control = new Switch(a);
        control.setText(title);
        control.setTextSize(16);
        control.setChecked(checked);
        control.setPadding(0, UI.dp(10), 0, UI.dp(10));
        return control;
    }

    private static int value(EditText edit, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(edit.getText().toString().trim())));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}