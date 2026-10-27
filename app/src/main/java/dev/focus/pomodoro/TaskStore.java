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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Local task data shared by daily notes and reminder alarms. */
final class TaskStore {
    static final class Task {
        long id, remindAt;
        String day, title;
        boolean done;
        boolean ringAlarm;
        int repeatDays;
        final Set<String> completedDays = new HashSet<>();
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
                task.ringAlarm = item.optBoolean("ring", false);
                task.repeatDays = item.optInt("repeat", 0);
                JSONArray completed = item.optJSONArray("done_days");
                if (completed != null) for (int j = 0; j < completed.length(); j++) task.completedDays.add(completed.optString(j));
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
        for (Task task : all(context)) if (appliesOn(task, day)) result.add(task);
        return result;
    }

    static boolean appliesOn(Task task, String day) {
        if (day.equals(task.day)) return true;
        if (task.repeatDays == 0) return false;
        try {
            java.util.Date date = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(day);
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.setTime(date);
            return (task.repeatDays & (1 << (calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1))) != 0;
        } catch (Exception ignored) { return false; }
    }

    static boolean doneOn(Task task, String day) {
        return task.repeatDays == 0 ? task.done : task.completedDays.contains(day);
    }

    static String repeatLabel(int days) {
        String[] names = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        ArrayList<String> selected = new ArrayList<>();
        for (int i = 0; i < names.length; i++) if ((days & (1 << i)) != 0) selected.add(names[i]);
        return selected.isEmpty() ? "" : "Repeats " + android.text.TextUtils.join(", ", selected);
    }

    static synchronized List<String> days(Context context) {
        ArrayList<String> result = new ArrayList<>();
        for (Task task : all(context)) if (!result.contains(task.day)) result.add(task.day);
        return result;
    }

    static synchronized Task add(Context context, String day, String title, long remindAt) {
        return add(context, day, title, remindAt, 0, false);
    }

    static synchronized Task add(Context context, String day, String title, long remindAt, int repeatDays, boolean ringAlarm) {
        ArrayList<Task> tasks = new ArrayList<>(all(context));
        Task task = new Task();
        task.id = System.currentTimeMillis();
        while (find(tasks, task.id) != null) task.id++;
        task.day = day;
        task.title = title;
        task.remindAt = remindAt;
        task.repeatDays = repeatDays;
        task.ringAlarm = ringAlarm;
        tasks.add(task);
        save(context, tasks);
        schedule(context, task);
        return task;
    }

    static synchronized void update(Context context, Task task) {
        ArrayList<Task> tasks = new ArrayList<>(all(context));
        for (int i = 0; i < tasks.size(); i++) if (tasks.get(i).id == task.id) { tasks.set(i, task); break; }
        save(context, tasks);
        if (task.done && task.repeatDays == 0) cancel(context, task.id); else schedule(context, task);
    }

    static synchronized void setDoneForDay(Context context, Task task, String day, boolean done) {
        ArrayList<Task> tasks = new ArrayList<>(all(context));
        Task stored = find(tasks, task.id);
        if (stored == null) return;
        if (stored.repeatDays == 0) stored.done = done;
        else if (done) stored.completedDays.add(day);
        else stored.completedDays.remove(day);
        save(context, tasks);
        if (stored.repeatDays == 0 && done) cancel(context, stored.id); else schedule(context, stored);
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
                    .put("done", task.done).put("remind", task.remindAt).put("ring", task.ringAlarm)
                    .put("repeat", task.repeatDays).put("done_days", new JSONArray(task.completedDays)));
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
        if ((task.done && task.repeatDays == 0) || task.remindAt <= 0) return;
        long at = task.repeatDays == 0 ? task.remindAt : nextOccurrence(task, System.currentTimeMillis());
        if (at <= System.currentTimeMillis()) return;
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        if (task.ringAlarm) {
            PendingIntent show = PendingIntent.getActivity(context, 211, Pomo.openIntent(false), flags());
            alarms.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), intent(context, task.id));
        } else if (Build.VERSION.SDK_INT >= 23) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(context, task.id));
        else alarms.set(AlarmManager.RTC_WAKEUP, at, intent(context, task.id));
    }

    static synchronized void reminderDelivered(Context context, Task task) {
        if (task.repeatDays != 0) schedule(context, task);
    }

    static synchronized void reschedule(Context context, Task task) { schedule(context, task); }

    private static long nextOccurrence(Task task, long now) {
        java.util.Calendar base = java.util.Calendar.getInstance();
        base.setTimeInMillis(task.remindAt);
        long firstAllowed = Math.max(now + 1000, task.remindAt - 1000);
        java.util.Calendar candidate = java.util.Calendar.getInstance();
        for (int offset = 0; offset <= 7; offset++) {
            candidate.setTimeInMillis(now);
            candidate.add(java.util.Calendar.DAY_OF_YEAR, offset);
            String candidateDay = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(candidate.getTime());
            if (doneOn(task, candidateDay)) continue;
            int bit = 1 << (candidate.get(java.util.Calendar.DAY_OF_WEEK) - 1);
            if ((task.repeatDays & bit) == 0) continue;
            candidate.set(java.util.Calendar.HOUR_OF_DAY, base.get(java.util.Calendar.HOUR_OF_DAY));
            candidate.set(java.util.Calendar.MINUTE, base.get(java.util.Calendar.MINUTE));
            candidate.set(java.util.Calendar.SECOND, 0);
            candidate.set(java.util.Calendar.MILLISECOND, 0);
            if (candidate.getTimeInMillis() >= firstAllowed) return candidate.getTimeInMillis();
        }
        return 0;
    }

    private static int flags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    }

    private static void cancel(Context context, long id) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.cancel(intent(context, id));
    }
}