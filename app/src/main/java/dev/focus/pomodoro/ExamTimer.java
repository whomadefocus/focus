package dev.focus.pomodoro;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

final class ExamTimer {
    private ExamTimer() { }

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