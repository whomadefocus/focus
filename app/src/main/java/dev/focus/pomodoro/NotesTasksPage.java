package dev.focus.pomodoro;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.graphics.Paint;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** A day-focused task manager with filters, reminders and an autosaved note. */
final class NotesTasksPage extends LinearLayout implements MainActivity.Refresh {
    private static final int FILTER_TODAY = 0, FILTER_UPCOMING = 1, FILTER_COMPLETED = 2;

    private final MainActivity a;
    private final Calendar date = Calendar.getInstance();
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private String day = Notes.today();
    private int filter;
    private TextView dateLabel, summary;
    private EditText note;
    private RecyclerView list;
    private Rows<TaskStore.Task> rows;
    private TextWatcher noteWatcher;
    private Runnable pendingSave;
    private volatile String cachedNoteText = "";
    private volatile String cachedNoteDay = Notes.today();

    NotesTasksPage(MainActivity activity) {
        super(activity);
        a = activity;
        setOrientation(VERTICAL);
        setBackgroundColor(UI.col(a, R.color.bg));

        TextView add = UI.symbolButton(a, "add", UI.col(a, R.color.text), 48, 24);
        add.setContentDescription("Add task");
        add.setOnClickListener(v -> editTask(null));
        addView(UI.topBar(a, "Notes & tasks", v -> a.back(), add),
                new LinearLayout.LayoutParams(UI.M, UI.W));
        addView(dateHeader(), new LinearLayout.LayoutParams(UI.M, UI.dp(56)));

        TabLayout tabs = new TabLayout(a);
        tabs.setTabMode(TabLayout.MODE_FIXED);
        tabs.setTabGravity(TabLayout.GRAVITY_FILL);
        tabs.setBackgroundColor(UI.col(a, R.color.surface));
        tabs.setTabTextColors(UI.col(a, R.color.text2), UI.col(a, R.color.primary));
        tabs.setSelectedTabIndicatorColor(UI.col(a, R.color.primary));
        tabs.addTab(tabs.newTab().setText("Today"));
        tabs.addTab(tabs.newTab().setText("Upcoming"));
        tabs.addTab(tabs.newTab().setText("Completed"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { filter = tab.getPosition(); renderTasks(); }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
        addView(tabs, new LinearLayout.LayoutParams(UI.M, UI.W));

        summary = UI.tv(a, "", 13, UI.col(a, R.color.text2), false);
        summary.setPadding(UI.dp(16), UI.dp(10), UI.dp(16), UI.dp(4));
        addView(summary, new LinearLayout.LayoutParams(UI.M, UI.W));

        list = new RecyclerView(a);
        list.setLayoutManager(new LinearLayoutManager(a));
        list.setClipToPadding(false);
        list.setPadding(UI.dp(8), UI.dp(2), UI.dp(8), UI.dp(8));
        rows = new Rows<TaskStore.Task>() {
            @Override View make(Context context) { return makeTaskRow(context); }
            @Override void bind(View view, TaskStore.Task task) { bindTaskRow(view, task); }
        };
        list.setAdapter(rows);
        addView(list, new LinearLayout.LayoutParams(UI.M, 0, 1f));

        LinearLayout noteSection = new LinearLayout(a);
        noteSection.setOrientation(VERTICAL);
        noteSection.setPadding(UI.dp(16), UI.dp(8), UI.dp(16), UI.dp(4));
        noteSection.addView(UI.tv(a, "DAILY NOTE", 12, UI.col(a, R.color.primary), true));
        note = new EditText(a);
        note.setTextColor(UI.col(a, R.color.text));
        note.setHintTextColor(UI.col(a, R.color.text2));
        note.setHint("Capture a thought for this day");
        note.setMinLines(2);
        note.setMaxLines(4);
        note.setGravity(Gravity.TOP | Gravity.START);
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        note.setPadding(UI.dp(12), UI.dp(10), UI.dp(12), UI.dp(10));
        note.setBackground(UI.rect(UI.col(a, R.color.surface), 8));
        noteSection.addView(note, new LinearLayout.LayoutParams(UI.M, UI.W));
        addView(noteSection, new LinearLayout.LayoutParams(UI.M, UI.W));

        TextView addTask = UI.tv(a, "+  Add task", 15, UI.col(a, R.color.surface), true);
        addTask.setGravity(Gravity.CENTER);
        addTask.setBackground(UI.ripple(UI.rect(UI.col(a, R.color.primary), 10), false, 10));
        addTask.setOnClickListener(v -> editTask(null));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(UI.M, UI.dp(48));
        addLp.setMargins(UI.dp(16), UI.dp(8), UI.dp(16), UI.dp(12));
        addView(addTask, addLp);

        updateDateLabel();
        loadNote();
        renderTasks();
    }

    private View dateHeader() {
        LinearLayout header = new LinearLayout(a);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView previous = UI.symbolButton(a, "chevron_left", UI.col(a, R.color.text2), 48, 24);
        previous.setContentDescription("Previous day");
        previous.setOnClickListener(v -> shiftDate(-1));
        header.addView(previous);
        dateLabel = UI.tv(a, "", 16, UI.col(a, R.color.text), true);
        dateLabel.setGravity(Gravity.CENTER);
        dateLabel.setOnClickListener(v -> pickDate());
        header.addView(dateLabel, new LinearLayout.LayoutParams(0, UI.M, 1f));
        TextView next = UI.symbolButton(a, "chevron_right", UI.col(a, R.color.text2), 48, 24);
        next.setContentDescription("Next day");
        next.setOnClickListener(v -> shiftDate(1));
        header.addView(next);
        return header;
    }

    private View makeTaskRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UI.dp(12), UI.dp(7), UI.dp(8), UI.dp(7));
        row.setBackground(UI.ripple(UI.rect(UI.col(context, R.color.surface), 8), false, 8));
        CheckBox checkbox = new CheckBox(context);
        row.addView(checkbox, new LinearLayout.LayoutParams(UI.dp(48), UI.dp(52)));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        TextView title = UI.tv(context, "", 15, UI.col(context, R.color.text), true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView meta = UI.tv(context, "", 12, UI.col(context, R.color.text2), false);
        labels.addView(title);
        labels.addView(meta);
        row.addView(labels, new LinearLayout.LayoutParams(0, UI.W, 1f));
        TextView more = UI.symbol(context, "more_vert", 22, UI.col(context, R.color.text2));
        row.addView(more, new LinearLayout.LayoutParams(UI.dp(36), UI.dp(44)));
        row.setTag(new Object[]{checkbox, title, meta, more});
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(UI.M, UI.W);
        lp.setMargins(UI.dp(4), UI.dp(4), UI.dp(4), UI.dp(4));
        row.setLayoutParams(lp);
        return row;
    }

    private void bindTaskRow(View view, final TaskStore.Task task) {
        Object[] holder = (Object[]) view.getTag();
        CheckBox checkbox = (CheckBox) holder[0];
        TextView title = (TextView) holder[1];
        TextView meta = (TextView) holder[2];
        checkbox.setOnCheckedChangeListener(null);
        checkbox.setChecked(task.done);
        title.setText(task.title);
        title.setPaintFlags(task.done ? title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                : title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        String detail = filter == FILTER_TODAY ? "" : formatDay(task.day);
        if (task.remindAt > 0) {
            if (!detail.isEmpty()) detail += " · ";
            detail += "Reminder " + android.text.format.DateFormat.getTimeFormat(a).format(new Date(task.remindAt));
        }
        meta.setText(detail);
        checkbox.setOnCheckedChangeListener((button, checked) -> {
            task.done = checked;
            TaskStore.update(a, task);
            renderTasks();
        });
        View.OnClickListener edit = v -> editTask(task);
        title.setOnClickListener(edit);
        meta.setOnClickListener(edit);
        ((View) holder[3]).setOnClickListener(v -> taskMenu(task));
    }

    private void taskMenu(TaskStore.Task task) {
        new android.app.AlertDialog.Builder(a).setTitle(task.title)
                .setItems(new String[]{"Edit task", task.remindAt > 0 ? "Change reminder" : "Add reminder", "Delete task"},
                        (dialog, choice) -> {
                            if (choice == 0) editTask(task);
                            else if (choice == 1) pickReminder(task);
                            else new android.app.AlertDialog.Builder(a).setMessage("Delete this task?")
                                    .setNegativeButton("Cancel", null).setPositiveButton("Delete", (d, w) -> {
                                        TaskStore.delete(a, task.id);
                                        renderTasks();
                                    }).show();
                        }).show();
    }

    private void editTask(final TaskStore.Task existing) {
        final EditText title = new EditText(a);
        title.setSingleLine(true);
        title.setHint("Task name");
        if (existing != null) title.setText(existing.title);
        final long[] remindAt = {existing == null ? 0 : existing.remindAt};
        final TextView reminder = UI.tv(a, reminderLabel(remindAt[0]), 14, UI.col(a, R.color.primary), true);
        reminder.setPadding(0, UI.dp(12), 0, UI.dp(8));
        reminder.setOnClickListener(v -> pickReminder(remindAt, reminder));
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(VERTICAL);
        body.setPadding(UI.dp(20), 0, UI.dp(20), 0);
        body.addView(title);
        body.addView(reminder);
        new android.app.AlertDialog.Builder(a).setTitle(existing == null ? "New task" : "Edit task").setView(body)
                .setNegativeButton("Cancel", null).setPositiveButton(existing == null ? "Add" : "Save", (dialog, which) -> {
                    String value = title.getText().toString().trim();
                    if (value.isEmpty()) { a.toast("Enter a task name"); return; }
                    if (existing == null) TaskStore.add(a, day, value, remindAt[0]);
                    else {
                        existing.title = value;
                        existing.remindAt = remindAt[0];
                        TaskStore.update(a, existing);
                    }
                    renderTasks();
                }).show();
    }

    private void pickReminder(final long[] output, final TextView label) {
        Calendar value = Calendar.getInstance();
        showDateTime(value, selected -> {
            output[0] = selected.getTimeInMillis();
            label.setText(reminderLabel(output[0]));
        });
    }

    private void pickReminder(final TaskStore.Task task) {
        Calendar value = Calendar.getInstance();
        if (task.remindAt > 0) value.setTimeInMillis(task.remindAt);
        showDateTime(value, selected -> {
            task.remindAt = selected.getTimeInMillis();
            TaskStore.update(a, task);
            renderTasks();
        });
    }

    private interface DateChosen { void chosen(Calendar value); }

    private void showDateTime(final Calendar value, final DateChosen callback) {
        new DatePickerDialog(a, (picker, year, month, dayOfMonth) -> {
            value.set(year, month, dayOfMonth);
            new TimePickerDialog(a, (timePicker, hour, minute) -> {
                value.set(Calendar.HOUR_OF_DAY, hour);
                value.set(Calendar.MINUTE, minute);
                value.set(Calendar.SECOND, 0);
                callback.chosen(value);
            }, value.get(Calendar.HOUR_OF_DAY), value.get(Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(a)).show();
        }, value.get(Calendar.YEAR), value.get(Calendar.MONTH), value.get(Calendar.DAY_OF_MONTH)).show();
    }

    private String reminderLabel(long timestamp) {
        if (timestamp <= 0) return "Add reminder";
        Date value = new Date(timestamp);
        return "Reminder · " + android.text.format.DateFormat.getMediumDateFormat(a).format(value)
                + " " + android.text.format.DateFormat.getTimeFormat(a).format(value);
    }

    private void shiftDate(int amount) {
        saveNoteNow();
        date.add(Calendar.DAY_OF_MONTH, amount);
        day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date.getTime());
        updateDateLabel();
        loadNote();
        renderTasks();
    }

    private void pickDate() {
        saveNoteNow();
        new DatePickerDialog(a, (picker, year, month, dayOfMonth) -> {
            date.set(year, month, dayOfMonth);
            day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date.getTime());
            updateDateLabel();
            loadNote();
            renderTasks();
        }, date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateLabel() {
        String prefix = day.equals(Notes.today()) ? "Today" : new SimpleDateFormat("EEEE", Locale.getDefault()).format(date.getTime());
        dateLabel.setText(prefix + " · " + new SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(date.getTime()));
    }

    private void loadNote() {
        if (noteWatcher != null) note.removeTextChangedListener(noteWatcher);
        cachedNoteDay = day;
        cachedNoteText = Notes.read(a, day);
        note.setText(cachedNoteText);
        noteWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable value) {
                cachedNoteDay = day;
                cachedNoteText = value.toString();
                if (pendingSave != null) handler.removeCallbacks(pendingSave);
                final String saveDay = cachedNoteDay;
                final String saveText = cachedNoteText;
                pendingSave = () -> Notes.write(a, saveDay, saveText);
                handler.postDelayed(pendingSave, 450);
            }
        };
        note.addTextChangedListener(noteWatcher);
    }

    private void saveNoteNow() {
        if (pendingSave != null) handler.removeCallbacks(pendingSave);
        pendingSave = null;
        if (note != null) {
            cachedNoteDay = day;
            cachedNoteText = note.getText().toString();
        }
        Notes.write(a, cachedNoteDay, cachedNoteText);
    }

    private void renderTasks() {
        if (rows == null || summary == null) return;
        List<TaskStore.Task> all = TaskStore.all(a);
        ArrayList<TaskStore.Task> visible = new ArrayList<>();
        int todayTotal = 0, todayDone = 0;
        long now = System.currentTimeMillis();
        for (TaskStore.Task task : all) {
            if (task.day.equals(Notes.today())) { todayTotal++; if (task.done) todayDone++; }
            if (filter == FILTER_TODAY && task.day.equals(day)) visible.add(task);
            else if (filter == FILTER_UPCOMING && !task.done
                    && (task.day.compareTo(Notes.today()) > 0 || task.remindAt > now)) visible.add(task);
            else if (filter == FILTER_COMPLETED && task.done) visible.add(task);
        }
        Collections.sort(visible, (left, right) -> {
            if (left.done != right.done) return left.done ? 1 : -1;
            long leftTime = left.remindAt > 0 ? left.remindAt : dayStart(left.day);
            long rightTime = right.remindAt > 0 ? right.remindAt : dayStart(right.day);
            return Long.compare(leftTime, rightTime);
        });
        rows.set(visible);
        summary.setText(filter == FILTER_TODAY ? todayDone + " of " + todayTotal + " tasks completed"
                : visible.size() + (filter == FILTER_UPCOMING ? " upcoming tasks" : " completed tasks"));
    }

    private long dayStart(String value) {
        try { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value).getTime(); }
        catch (Exception ignored) { return Long.MAX_VALUE; }
    }

    private String formatDay(String value) {
        if (value.equals(Notes.today())) return "Today";
        try { return new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(
                new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value)); }
        catch (Exception ignored) { return value; }
    }

    @Override public void refresh() { renderTasks(); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Notes.flusher = () -> Notes.write(a, cachedNoteDay, cachedNoteText);
        Notes.changed = () -> a.runOnUiThread(() -> {
            if (day.equals(Notes.today())) loadNote();
            renderTasks();
        });
    }

    @Override protected void onDetachedFromWindow() {
        saveNoteNow();
        Notes.flusher = null;
        Notes.changed = null;
        super.onDetachedFromWindow();
    }
}
