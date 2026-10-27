package dev.focus.pomodoro;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Independent user notes stored separately from task-page daily notes. */
final class Notebook {
    static final class Note {
        long id;
        String day, title, body;
    }

    private Notebook() { }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("notebook", Context.MODE_PRIVATE);
    }

    static synchronized List<Note> all(Context context) {
        ArrayList<Note> notes = new ArrayList<>();
        try {
            JSONArray values = new JSONArray(prefs(context).getString("items", "[]"));
            for (int i = 0; i < values.length(); i++) {
                JSONObject item = values.getJSONObject(i);
                Note note = new Note();
                note.id = item.optLong("id");
                note.day = item.optString("day", Notes.today());
                note.title = item.optString("title", "");
                note.body = item.optString("body", "");
                notes.add(note);
            }
        } catch (Exception ignored) { }
        return notes;
    }

    static synchronized Note save(Context context, Note note) {
        ArrayList<Note> notes = new ArrayList<>(all(context));
        if (note.id == 0) note.id = System.currentTimeMillis();
        boolean replaced = false;
        for (int i = 0; i < notes.size(); i++) {
            if (notes.get(i).id == note.id) { notes.set(i, note); replaced = true; break; }
        }
        if (!replaced) notes.add(note);
        write(context, notes);
        return note;
    }

    static synchronized void delete(Context context, long id) {
        ArrayList<Note> notes = new ArrayList<>(all(context));
        for (int i = notes.size() - 1; i >= 0; i--) if (notes.get(i).id == id) notes.remove(i);
        write(context, notes);
    }

    private static void write(Context context, List<Note> notes) {
        JSONArray values = new JSONArray();
        for (Note note : notes) {
            try {
                values.put(new JSONObject().put("id", note.id).put("day", note.day)
                        .put("title", note.title).put("body", note.body));
            } catch (Exception ignored) { }
        }
        prefs(context).edit().putString("items", values.toString()).apply();
    }
}
