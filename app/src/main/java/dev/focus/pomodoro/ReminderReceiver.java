package dev.focus.pomodoro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "task_reminders";

    @Override public void onReceive(Context context, Intent intent) {
        long id = intent.getLongExtra("id", 0);
        TaskStore.Task task = null;
        for (TaskStore.Task item : TaskStore.all(context)) if (item.id == id) { task = item; break; }
        if (task == null || task.done) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Task reminders", NotificationManager.IMPORTANCE_HIGH));
        Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        android.app.PendingIntent content = android.app.PendingIntent.getActivity(context, 210, open,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? android.app.PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder notification = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        notification.setSmallIcon(R.drawable.ic_stat).setContentTitle("Task reminder").setContentText(task.title)
                .setAutoCancel(true).setContentIntent(content);
        manager.notify((int) (id ^ (id >>> 32)), notification.build());
    }
}