package dev.focus.pomodoro;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Local task data shared by daily notes and reminder alarms. */
final class TaskStore {
    static final class Task {
        long id, remindAt;
        String day, title;
        boolean done;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("note_tasks", Context.MODE_PRIVATE);
    }

    static synchronized List<Task> all(Context context) {
        ArrayList<Task> tasks = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(prefs(context).getString("items", "[]"));
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                Task task = new Task();
                task.id = item.optLong("id");
                task.day = item.optString("day", Notes.today());
                task.title = item.optString("title", "");
                task.done = item.optBoolean("done", false);
                task.remindAt = item.optLong("remind", 0);
                tasks.add(task);
            }
        } catch (Exception ignored) { }
        Collections.sort(tasks, new Comparator<Task>() {
            @Override public int compare(Task left, Task right) { return Long.compare(left.id, right.id); }
        });
        return tasks;
    }

    static synchronized List<Task> forDay(Context context, String day) {
        ArrayList<Task> result = new ArrayList<>();
        for (Task task : all(context)) if (day.equals(task.day)) result.add(task);
        return result;
    }

    static synchronized List<String> days(Context context) {
        ArrayList<String> result = new ArrayList<>();
        for (Task task : all(context)) if (!result.contains(task.day)) result.add(task.day);
        return result;
    }

    static synchronized Task add(Context context, String day, String title, long remindAt) {
        ArrayList<Task> tasks = new ArrayList<>(all(context));
        Task task = new Task();
        task.id = System.currentTimeMillis();
        while (find(tasks, task.id) != null) task.id++;
        task.day = day;
        task.title = title;
        task.remindAt = remindAt;
        tasks.add(task);
        save(context, tasks);
        schedule(context, task);
        return task;
    }

    static synchronized void update(Context context, Task task) {
        ArrayList<Task> tasks = new ArrayList<>(all(context));
        for (int i = 0; i < tasks.size(); i++) if (tasks.get(i).id == task.id) { tasks.set(i, task); break; }
        save(context, tasks);
        if (task.done) cancel(context, task.id); else schedule(context, task);
    }

    static synchronized void delete(Context context, long id) {
        ArrayList<Task> tasks = new ArrayList<>(all(context));
        for (int i = tasks.size() - 1; i >= 0; i--) if (tasks.get(i).id == id) tasks.remove(i);
        save(context, tasks);
        cancel(context, id);
    }

    private static Task find(List<Task> tasks, long id) {
        for (Task task : tasks) if (task.id == id) return task;
        return null;
    }

    private static void save(Context context, List<Task> tasks) {
        JSONArray data = new JSONArray();
        for (Task task : tasks) {
            try {
                data.put(new JSONObject().put("id", task.id).put("day", task.day).put("title", task.title)
                        .put("done", task.done).put("remind", task.remindAt));
            } catch (Exception ignored) { }
        }
        prefs(context).edit().putString("items", data.toString()).apply();
    }

    private static PendingIntent intent(Context context, long id) {
        Intent alarm = new Intent(context, ReminderReceiver.class).putExtra("id", id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(context, (int) (id ^ (id >>> 32)), alarm, flags);
    }

    private static void schedule(Context context, Task task) {
        cancel(context, task.id);
        if (task.done || task.remindAt <= System.currentTimeMillis()) return;
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        if (Build.VERSION.SDK_INT >= 23) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.remindAt, intent(context, task.id));
        else alarms.set(AlarmManager.RTC_WAKEUP, task.remindAt, intent(context, task.id));
    }

    private static void cancel(Context context, long id) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.cancel(intent(context, id));
    }
}