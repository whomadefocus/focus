package dev.focus.pomodoro;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

final class ExamTimer {
    private ExamTimer() { }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("exam", Context.MODE_PRIVATE);
    }

    static boolean running(Context context) { return prefs(context).getBoolean("running", false); }
    static boolean active(Context context) { return prefs(context).getBoolean("active", false); }

    static long left(Context context) {
        SharedPreferences state = prefs(context);
        return state.getBoolean("running", false)
                ? Math.max(0, state.getLong("end", 0) - System.currentTimeMillis())
                : state.getLong("remaining", state.getInt("minutes", 30) * 60000L);
    }

    static void toggle(Context context) {
        SharedPreferences state = prefs(context);
        if (state.getBoolean("running", false)) {
            state.edit().putLong("remaining", left(context)).putBoolean("running", false).putBoolean("active", true).apply();
            cancel(context);
            return;
        }
        int minutes = Math.max(1, state.getInt("minutes", 30));
        long remaining = left(context);
        if (remaining <= 0) remaining = minutes * 60000L;
        long end = System.currentTimeMillis() + remaining;
        state.edit().putLong("remaining", remaining).putLong("end", end)
            .putBoolean("running", true).putBoolean("active", true).apply();
        schedule(context, end);
    }

    static void reset(Context context) {
        int minutes = Math.max(1, prefs(context).getInt("minutes", 30));
        cancel(context);
        prefs(context).edit().putBoolean("running", false).putBoolean("active", true)
            .putLong("remaining", minutes * 60000L).apply();
    }

    static void schedule(Context context, long at) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        PendingIntent show = PendingIntent.getActivity(context, 302, Pomo.openIntent(false), flags());
        alarms.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), pending(context));
    }

    static void cancel(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.cancel(pending(context));
    }

    static PendingIntent pending(Context context) {
        return PendingIntent.getBroadcast(context, 301, new Intent(context, ExamReceiver.class), flags());
    }

    private static int flags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    }
}