package dev.focus.pomodoro;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Pomodoro engine. All state lives in SharedPreferences (absolute end time), so no service is
 * needed: an AlarmClock alarm wakes {@link TimerReceiver} when a phase ends, even in Doze.
 */
final class Pomo {
    static final int FOCUS = 0, SHORT = 1, LONG = 2;
    static final String CH_TIMER = "timer", CH_ALERT = "alert";

    private static Context app;
    private static SharedPreferences sp;
    private static final Handler H = new Handler(Looper.getMainLooper());
    static final List<Runnable> listeners = new ArrayList<>();
    /** Number of started (visible) activities of this app. */
    static int visible = 0;
    private static boolean alertOn;

    static void init(Context c) {
        if (app != null) return;
        app = c.getApplicationContext();
        sp = app.getSharedPreferences("pomo", Context.MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = nm();
            NotificationChannel t = new NotificationChannel(CH_TIMER, "Timer", NotificationManager.IMPORTANCE_LOW);
            t.setShowBadge(false);
            nm.createNotificationChannel(t);
            NotificationChannel a = new NotificationChannel(CH_ALERT, "Time is up", NotificationManager.IMPORTANCE_HIGH);
            a.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            a.enableVibration(true);
            a.setVibrationPattern(new long[]{0, 500, 250, 500, 250, 500});
            nm.createNotificationChannel(a);
        }
    }

    // ---------- settings ----------
    static int focusMin() { return sp.getInt("focus", 25); }
    static int shortMin() { return sp.getInt("short", 5); }
    static int longMin() { return sp.getInt("long", 15); }
    static int every() { return sp.getInt("every", 4); }
    static boolean auto() { return sp.getBoolean("auto", false); }
    static boolean pull() { return sp.getBoolean("pull", true); }
    static boolean awake() { return sp.getBoolean("awake", true); }

    static void saveSettings(int f, int s, int l, int e, boolean auto, boolean pull, boolean awake) {
        sp.edit().putInt("focus", f).putInt("short", s).putInt("long", l).putInt("every", e)
                .putBoolean("auto", auto).putBoolean("pull", pull).putBoolean("awake", awake).apply();
        if (!running()) reset(); else fire();
    }

    static long dur(int p) {
        return 60000L * (p == FOCUS ? focusMin() : p == SHORT ? shortMin() : longMin());
    }

    static String phaseName(int p) {
        return p == FOCUS ? "Focus" : p == SHORT ? "Short break" : "Long break";
    }

    // ---------- state ----------
    static int phase() { return sp.getInt("phase", FOCUS); }
    static int done() { return sp.getInt("done", 0); }
    static boolean running() { return sp.getBoolean("run", false); }
    static long total() { return sp.getLong("total", dur(phase())); }

    static long left() {
        if (running()) return Math.max(0, sp.getLong("end", 0) - System.currentTimeMillis());
        return sp.getLong("left", dur(phase()));
    }

    static void start() {
        if (running()) return;
        long l = left();
        if (l <= 0) l = dur(phase());
        long end = System.currentTimeMillis() + l;
        sp.edit().putBoolean("run", true).putLong("end", end).apply();
        schedule(end);
        ongoing();
        fire();
    }

    static void pause() {
        if (!running()) return;
        long l = left();
        sp.edit().putBoolean("run", false).putLong("left", l).apply();
        cancelAlarm();
        clearOngoing();
        fire();
    }

    static void toggle() { if (running()) pause(); else start(); }

    static void reset() {
        cancelAlarm();
        clearOngoing();
        long d = dur(phase());
        sp.edit().putBoolean("run", false).putLong("left", d).putLong("total", d).apply();
        fire();
    }

    static void skip() { advance(false); }

    /** Add minutes to the current phase (used by the PDF reader's +5 button). */
    static void extend(int min) {
        long add = min * 60000L;
        if (running()) {
            long end = sp.getLong("end", 0) + add;
            sp.edit().putLong("end", end).putLong("total", total() + add).apply();
            schedule(end);
            ongoing();
        } else {
            sp.edit().putLong("left", left() + add).putLong("total", total() + add).apply();
        }
        fire();
    }

    /** Called by the alarm (or by the UI if it notices time is up). Safe to call twice. */
    static void finish() {
        if (!running() || System.currentTimeMillis() < sp.getLong("end", 0) - 1500) return;
        if (phase() == FOCUS) Notes.appendPomodoro(app, (int) Math.max(1, total() / 60000));
        advance(true);
    }

    private static void advance(boolean natural) {
        int p = phase(), d = done(), next;
        if (p == FOCUS) {
            if (natural) d++;
            next = d >= every() ? LONG : SHORT;
        } else {
            if (p == LONG) d = 0;
            next = FOCUS;
        }
        boolean go = natural && auto();
        long dd = dur(next), now = System.currentTimeMillis();
        cancelAlarm();
        sp.edit().putInt("phase", next).putInt("done", d).putLong("total", dd).putLong("left", dd)
                .putBoolean("run", go).putLong("end", go ? now + dd : 0).apply();
        if (go) { schedule(now + dd); ongoing(); } else clearOngoing();
        if (natural) alert(next);
        fire();
    }

    static void fire() {
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    }

    // ---------- alarm ----------
    static int flags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    }

    private static PendingIntent endPi() {
        return PendingIntent.getBroadcast(app, 1, new Intent(app, TimerReceiver.class), flags());
    }

    private static AlarmManager am() { return (AlarmManager) app.getSystemService(Context.ALARM_SERVICE); }

    private static void schedule(long at) {
        PendingIntent show = PendingIntent.getActivity(app, 2, openIntent(false), flags());
        // setAlarmClock needs no special permission and fires exactly, even in Doze.
        am().setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), endPi());
    }

    private static void cancelAlarm() { am().cancel(endPi()); }

    // ---------- notifications ----------
    private static NotificationManager nm() {
        return (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static Notification.Builder builder(String ch) {
        return Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(app, ch) : new Notification.Builder(app);
    }

    static Intent openIntent(boolean alert) {
        Intent i = new Intent(app, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (alert) i.putExtra("alert", true);
        return i;
    }

    private static void ongoing() {
        long end = sp.getLong("end", 0);
        Notification.Builder b = builder(CH_TIMER)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(phaseName(phase()))
                .setContentText("Ends at " + DateFormat.getTimeFormat(app).format(new Date(end)))
                .setOngoing(true)
                .setShowWhen(true)
                .setWhen(end)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setPriority(Notification.PRIORITY_LOW)
                .setContentIntent(PendingIntent.getActivity(app, 4, openIntent(false), flags()));
        if (Build.VERSION.SDK_INT >= 24) {
            b.setUsesChronometer(true);
            b.setChronometerCountDown(true);
        }
        nm().notify(1, b.build());
    }

    private static void clearOngoing() { nm().cancel(1); }

    static boolean canOverlay() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(app);
    }

    /** Sound + heads-up/full-screen notification, and (optionally) pull the user out of whatever app is open. */
    private static void alert(int next) {
        String title = next == FOCUS ? "Break is over" : "Focus session complete";
        String text = next == FOCUS ? "Time to focus again"
                : (next == LONG ? "Long break" : "Short break") + " \u00B7 " + (dur(next) / 60000) + " min";
        Intent open = openIntent(true);
        PendingIntent pi = PendingIntent.getActivity(app, 3, open, flags());
        Uri alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        Notification.Builder b = builder(CH_ALERT)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setSound(alarm)
                .setVibrate(new long[]{0, 500, 250, 500, 250, 500});
        Notification n = b.build();
        if (!auto()) n.flags |= Notification.FLAG_INSISTENT; // keeps ringing until you touch the phone
        nm().notify(2, n);
        alertOn = true;
        H.postDelayed(new Runnable() { @Override public void run() { dismissAlert(); } }, 60000);
        if (pull() && (visible > 0 || canOverlay())) {
            try { app.startActivity(open); } catch (Exception ignored) { }
        }
    }

    static void dismissAlert() {
        if (alertOn) {
            alertOn = false;
            nm().cancel(2);
        }
    }
}
