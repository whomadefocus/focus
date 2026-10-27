package dev.focus.pomodoro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores user-created task reminders after the device reboots. */
public final class TaskAlarmBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Pomo.init(context);
        for (TaskStore.Task task : TaskStore.all(context)) TaskStore.reschedule(context, task);
    }
}