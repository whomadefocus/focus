package dev.focus.pomodoro;

import android.app.DatePickerDialog;
import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Searchable notebook of daily notes and focus logs. */
final class NotesLibraryPage extends LinearLayout implements MainActivity.Refresh {
    private static final class Entry {
        String day, title, content;
        long id;
        boolean daily;
    }

    private final MainActivity a;
    private final ArrayList<Entry> entries = new ArrayList<>();
    private final EditText search;
    private final Rows<Entry> rows;

    NotesLibraryPage(MainActivity activity) {
        super(activity);
        a = activity;
        setOrientation(VERTICAL);
        setBackgroundColor(UI.col(a, R.color.bg));

        TextView add = UI.symbolButton(a, "add", UI.col(a, R.color.text), 48, 24);
        add.setContentDescription("Create note");
        add.setOnClickListener(v -> pickDateAndCreate());
        addView(UI.topBar(a, "Notes", v -> a.back(), add),
                new LinearLayout.LayoutParams(UI.M, UI.W));

        search = new EditText(a);
        search.setSingleLine(true);
        search.setHint("Search daily notes");
        search.setTextColor(UI.col(a, R.color.text));
        search.setHintTextColor(UI.col(a, R.color.text2));
        search.setPadding(UI.dp(14), 0, UI.dp(14), 0);
        search.setBackground(UI.rect(UI.col(a, R.color.surface), 8));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(UI.M, UI.dp(48));
        searchLp.setMargins(UI.dp(12), UI.dp(10), UI.dp(12), UI.dp(4));
        addView(search, searchLp);

        RecyclerView list = new RecyclerView(a);
        list.setLayoutManager(new LinearLayoutManager(a));
        list.setClipToPadding(false);
        list.setPadding(UI.dp(8), UI.dp(4), UI.dp(8), UI.dp(16));
        rows = new Rows<Entry>() {
            @Override View make(Context context) { return makeRow(context); }
            @Override void bind(View view, Entry entry) { bindRow(view, entry); }
        };
        list.setAdapter(rows);
        addView(list, new LinearLayout.LayoutParams(UI.M, 0, 1f));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
        refresh();
    }

    private View makeRow(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setPadding(UI.dp(16), UI.dp(12), UI.dp(16), UI.dp(14));
        card.setBackground(UI.ripple(UI.rect(UI.col(context, R.color.surface), 8), false, 8));
        TextView date = UI.tv(context, "", 12, UI.col(context, R.color.primary), true);
        TextView title = UI.tv(context, "", 16, UI.col(context, R.color.text), true);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView preview = UI.tv(context, "", 13, UI.col(context, R.color.text2), false);
        preview.setMaxLines(3);
        preview.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(UI.M, UI.W);
        dateLp.bottomMargin = UI.dp(5);
        card.addView(date, dateLp);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(UI.M, UI.W);
        titleLp.bottomMargin = UI.dp(5);
        card.addView(title, titleLp);
        card.addView(preview, new LinearLayout.LayoutParams(UI.M, UI.W));
        card.setTag(new Object[]{date, title, preview});
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(UI.M, UI.W);
        lp.setMargins(UI.dp(4), UI.dp(4), UI.dp(4), UI.dp(4));
        card.setLayoutParams(lp);
        return card;
    }

    private void bindRow(View view, final Entry entry) {
        Object[] holder = (Object[]) view.getTag();
        ((TextView) holder[0]).setText(formatDay(entry.day));
        ((TextView) holder[1]).setText(entry.daily ? "Daily notes" : entry.title.isEmpty() ? "Untitled note" : entry.title);
        ((TextView) holder[2]).setText(entry.content.trim());
        view.setOnClickListener(v -> {
            if (entry.daily) {
                new android.app.AlertDialog.Builder(a).setTitle(formatDay(entry.day)).setMessage(entry.content)
                        .setPositiveButton("Close", null).setNeutralButton("New note", (dialog, which) -> editNote(null, entry.day)).show();
            } else editNote(notebookNote(entry), entry.day);
        });
        view.setOnLongClickListener(v -> {
            if (entry.daily) return true;
            new android.app.AlertDialog.Builder(a).setTitle("Delete note?").setMessage(entry.title)
                    .setNegativeButton("Cancel", null).setPositiveButton("Delete", (dialog, which) -> {
                        Notebook.delete(a, entry.id);
                        refresh();
                    }).show();
            return true;
        });
    }

    private void pickDateAndCreate() {
        java.util.Calendar date = java.util.Calendar.getInstance();
        new DatePickerDialog(a, (picker, year, month, dayOfMonth) -> {
            date.set(year, month, dayOfMonth);
            editNote(null, new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date.getTime()));
        }, date.get(java.util.Calendar.YEAR), date.get(java.util.Calendar.MONTH),
                date.get(java.util.Calendar.DAY_OF_MONTH)).show();
    }

    private Notebook.Note notebookNote(Entry entry) {
        Notebook.Note note = new Notebook.Note();
        note.id = entry.id;
        note.day = entry.day;
        note.title = entry.title;
        note.body = entry.content;
        return note;
    }

    private void editNote(final Notebook.Note existing, String noteDay) {
        final String[] day = {noteDay};
        final EditText title = new EditText(a);
        title.setSingleLine(true);
        title.setHint("Title");
        title.setTextColor(UI.col(a, R.color.text));
        title.setHintTextColor(UI.col(a, R.color.text2));
        if (existing != null) title.setText(existing.title);
        final EditText body = new EditText(a);
        body.setMinLines(4);
        body.setMaxLines(9);
        body.setGravity(Gravity.TOP | Gravity.START);
        body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setHint("Write a note for " + formatDay(noteDay));
        body.setTextColor(UI.col(a, R.color.text));
        body.setHintTextColor(UI.col(a, R.color.text2));
        body.setTextColor(UI.col(a, R.color.text));
        body.setHintTextColor(UI.col(a, R.color.text2));
        if (existing != null) body.setText(existing.body);
        TextView date = UI.tv(a, "Date · " + formatDay(noteDay), 14, UI.col(a, R.color.primary), true);
        date.setPadding(0, UI.dp(12), 0, UI.dp(8));
        date.setOnClickListener(v -> {
            java.util.Calendar selected = java.util.Calendar.getInstance();
            try { selected.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day[0])); }
            catch (Exception ignored) { }
            new DatePickerDialog(a, (picker, year, month, dayOfMonth) -> {
                selected.set(year, month, dayOfMonth);
                day[0] = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selected.getTime());
                date.setText("Date · " + formatDay(day[0]));
            }, selected.get(java.util.Calendar.YEAR), selected.get(java.util.Calendar.MONTH),
                    selected.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(VERTICAL);
        content.setPadding(UI.dp(20), 0, UI.dp(20), 0);
        content.addView(date);
        content.addView(title);
        content.addView(body);
        new android.app.AlertDialog.Builder(a).setTitle(existing == null ? "New note" : "Edit note").setView(content)
                .setNegativeButton("Cancel", null).setPositiveButton("Save", (dialog, which) -> {
                    String value = body.getText().toString().trim();
                    String heading = title.getText().toString().trim();
                    if (value.isEmpty()) { a.toast("Write something first"); return; }
                    Notebook.Note note = existing == null ? new Notebook.Note() : existing;
                    note.day = day[0];
                    note.title = heading;
                    note.body = value;
                    Notebook.save(a, note);
                    refresh();
                }).show();
    }

    private void filter(String query) {
        String normalized = query.trim().toLowerCase(Locale.getDefault());
        ArrayList<Entry> found = new ArrayList<>();
        for (Entry entry : entries) {
            if (normalized.isEmpty() || entry.content.toLowerCase(Locale.getDefault()).contains(normalized)
                || entry.title.toLowerCase(Locale.getDefault()).contains(normalized)
                || formatDay(entry.day).toLowerCase(Locale.getDefault()).contains(normalized)) found.add(entry);
        }
        rows.set(found);
    }

    private String formatDay(String day) {
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day);
            return new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(date);
        } catch (Exception ignored) { return day; }
    }

    @Override public void refresh() {
        entries.clear();
        for (String day : Notes.days(a)) {
            String content = Notes.read(a, day).trim();
            if (content.isEmpty()) continue;
            Entry entry = new Entry();
            entry.day = day;
            entry.content = content;
            entry.title = "Daily notes";
            entry.daily = true;
            entries.add(entry);
        }
        for (Notebook.Note note : Notebook.all(a)) {
            Entry entry = new Entry();
            entry.id = note.id;
            entry.day = note.day;
            entry.title = note.title == null ? "" : note.title;
            entry.content = note.body == null ? "" : note.body;
            entries.add(entry);
        }
        Collections.sort(entries, (left, right) -> right.day.compareTo(left.day));
        filter(search.getText().toString());
    }

}
