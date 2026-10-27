package dev.focus.pomodoro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;

/** Keeps playback alive in the background and shows the media notification. */
public class MusicService extends Service {
    static final int ID = 10;
    static final String CH = "music";

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { Player.pause(); }
    };

    static void sync(Context c) {
        Intent i = new Intent(c, MusicService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    @Override public void onCreate() {
        super.onCreate();
        Player.init(this);
        Pomo.init(this);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, "Music", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
        registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }

    @Override public int onStartCommand(Intent in, int flags, int startId) {
        String a = in == null ? null : in.getAction();
        if ("prev".equals(a)) Player.prev();
        else if ("next".equals(a)) Player.next();
        else if ("toggle".equals(a)) Player.toggle();
        else if ("stop".equals(a)) Player.stop();

        if (Player.cur == null) { stopSelf(); return START_NOT_STICKY; }
        startForeground(ID, build());
        if (!Player.active()) {
            stopForeground(true);
            stopSelf();
        } else if (!Player.playing) {
            stopForeground(false); // keep the notification, let the system reclaim us if needed
        }
        return START_NOT_STICKY;
    }

    private PendingIntent act(String action, int code) {
        return PendingIntent.getService(this, code, new Intent(this, MusicService.class).setAction(action), Pomo.flags());
    }

    @SuppressWarnings("deprecation")
    private Notification build() {
        Player.Track t = Player.cur;
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(t.title)
                .setContentText(t.sub)
                .setOngoing(Player.playing)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(PendingIntent.getActivity(this, 5, Pomo.openIntent(false), Pomo.flags()))
                .addAction(android.R.drawable.ic_media_previous, "Previous", act("prev", 6))
                .addAction(Player.playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        "Play/Pause", act("toggle", 7))
                .addAction(android.R.drawable.ic_media_next, "Next", act("next", 8))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", act("stop", 9))
                .setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2));
        return b.build();
    }

    @Override public void onDestroy() {
        try { unregisterReceiver(noisy); } catch (Exception ignored) { }
        stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
