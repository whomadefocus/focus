package dev.focus.pomodoro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class ExamReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "exam_timer";

    @Override public void onReceive(Context context, Intent intent) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("exam", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("running", false) || prefs.getLong("end", 0) > System.currentTimeMillis()) return;
        prefs.edit().putBoolean("running", false).putLong("remaining", 0).apply();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Exam timer", NotificationManager.IMPORTANCE_HIGH));
        Notification.Builder notification = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        notification.setSmallIcon(R.drawable.ic_stat).setContentTitle("Exam timer finished")
                .setContentText("Your exam time is up.").setAutoCancel(true);
        manager.notify(303, notification.build());
    }
}