package dev.focus.pomodoro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

public final class ReminderReceiver extends BroadcastReceiver {
        private static final String CHANNEL = "task_reminders";
        private static final String ALARM_CHANNEL = "task_alarm_reminders";

    @Override public void onReceive(Context context, Intent intent) {
        long id = intent.getLongExtra("id", 0);
        TaskStore.Task task = null;
        for (TaskStore.Task item : TaskStore.all(context)) if (item.id == id) { task = item; break; }
        if (task == null || task.done) return;
        TaskStore.reminderDelivered(context, task);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        String channelId = task.ringAlarm ? ALARM_CHANNEL : CHANNEL;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    task.ringAlarm ? "Alarm reminders" : "Task reminders", NotificationManager.IMPORTANCE_HIGH);
            if (task.ringAlarm) {
                channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[]{0, 700, 350, 700});
            }
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        android.app.PendingIntent content = android.app.PendingIntent.getActivity(context, 210, open,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? android.app.PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder notification = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, channelId) : new Notification.Builder(context);
        notification.setSmallIcon(R.drawable.ic_stat).setContentTitle("Task reminder").setContentText(task.title)
                .setAutoCancel(true).setContentIntent(content);
        if (task.ringAlarm && Build.VERSION.SDK_INT < 26) {
            notification.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            notification.setVibrate(new long[]{0, 700, 350, 700});
        }
        manager.notify((int) (id ^ (id >>> 32)), notification.build());
    }
}