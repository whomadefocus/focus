package dev.focus.pomodoro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.content.pm.ServiceInfo;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** User-enabled system-wide Pomodoro controller that floats over other apps. */
public final class OverlayService extends Service {
    private static final int SHOW_EXAM = 0, SHOW_POMODORO = 1;
    private static final String CHANNEL = "floating_controls";
    private static final int NOTIFICATION_ID = 404;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private WindowManager.LayoutParams params;
    private LinearLayout overlay;
    private TextView phase, timer, play;
    private TextView resetControl;
    private int shownTimer = SHOW_EXAM;
    private boolean manualTimerChoice, timerStatesKnown, previousExamRunning, previousPomoRunning;
    private boolean minimized;
    private long minimizedAt;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            update();
            handler.postDelayed(this, 500);
        }
    };
    private final Runnable stateChanged = new Runnable() { @Override public void run() { update(); } };

    public static boolean enabled(Context context) {
        return context.getSharedPreferences("floating_controls", Context.MODE_PRIVATE).getBoolean("enabled", false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences("floating_controls", Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply();
        Intent service = new Intent(context, OverlayService.class);
        if (enabled) {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
        } else {
            context.stopService(service);
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        Pomo.init(this);
        UI.init(this);
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        Pomo.listeners.add(stateChanged);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!enabled(this) || !Pomo.canOverlay()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification());
        }
        if (overlay == null) attachOverlay();
        update();
        handler.removeCallbacks(tick);
        handler.post(tick);
        return START_STICKY;
    }

    private void attachOverlay() {
        overlay = new LinearLayout(this);
        overlay.setGravity(Gravity.CENTER_VERTICAL);
        overlay.setPadding(UI.dp(8), UI.dp(5), UI.dp(8), UI.dp(5));
        overlay.setBackground(UI.ripple(UI.rect(UI.dark() ? 0xF01B2225 : 0xF0283034, 18), false, 18));
        overlay.setElevation(UI.dp(8));

        TextView drag = UI.symbol(this, "drag_indicator", 20, UI.col(this, R.color.text2));
        drag.setContentDescription("Move floating timer controls");
        drag.setOnClickListener(v -> drag.announceForAccessibility("Drag to move floating timer controls"));
        overlay.addView(drag, new LinearLayout.LayoutParams(UI.dp(28), UI.dp(40)));
        phase = UI.tv(this, "Focus", 12, UI.col(this, R.color.text2), true);
        phase.setSingleLine(true);
        phase.setContentDescription("Switch between timer and Pomodoro");
        phase.setOnClickListener(v -> {
            shownTimer = shownTimer == SHOW_EXAM ? SHOW_POMODORO : SHOW_EXAM;
            manualTimerChoice = true;
            update();
        });
        overlay.addView(phase, new LinearLayout.LayoutParams(UI.dp(58), UI.W));
        timer = UI.tv(this, "25:00", 16, UI.col(this, R.color.text), true);
        timer.setGravity(Gravity.CENTER);
        overlay.addView(timer, new LinearLayout.LayoutParams(UI.dp(62), UI.dp(40)));

        resetControl = UI.symbol(this, "restart_alt", 20, UI.col(this, R.color.text2));
        resetControl.setContentDescription("Reset timer");
        resetControl.setBackground(UI.ripple(null, true, 0));
        resetControl.setOnClickListener(v -> {
            if (shownTimer == SHOW_EXAM) ExamTimer.reset(this); else Pomo.reset();
        });
        overlay.addView(resetControl, new LinearLayout.LayoutParams(UI.dp(40), UI.dp(40)));

        play = UI.symbol(this, "play_arrow", 22, UI.col(this, R.color.primary));
        play.setContentDescription("Start or pause Pomodoro");
        play.setBackground(UI.ripple(null, true, 0));
        play.setOnClickListener(v -> toggleActiveTimer());
        overlay.addView(play, new LinearLayout.LayoutParams(UI.dp(42), UI.dp(42)));

        TextView close = UI.symbol(this, "close", 20, UI.col(this, R.color.text2));
        close.setContentDescription("Minimize floating controls");
        close.setBackground(UI.ripple(null, true, 0));
        close.setOnClickListener(v -> minimize());
        overlay.addView(close, new LinearLayout.LayoutParams(UI.dp(36), UI.dp(40)));

        params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = UI.dp(16);
        params.y = UI.dp(140);
        windows.addView(overlay, params);

        attachDrag(drag);
    }

    private void attachDrag(View target) {
        target.setOnTouchListener(new View.OnTouchListener() {
            float downRawX, downRawY;
            int startX, startY;
            boolean moved;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    startX = params.x;
                    startY = params.y;
                    moved = false;
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (Math.abs(event.getRawX() - downRawX) > UI.dp(4)
                            || Math.abs(event.getRawY() - downRawY) > UI.dp(4)) moved = true;
                    params.x = startX + Math.round(event.getRawX() - downRawX);
                    params.y = startY + Math.round(event.getRawY() - downRawY);
                    try { windows.updateViewLayout(overlay, params); } catch (Exception ignored) { }
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (!moved) view.performClick();
                    return true;
                }
                return event.getAction() == MotionEvent.ACTION_CANCEL;
            }
        });
    }

    private void update() {
        if (overlay == null || minimized) {
            if (minimized && Pomo.visible == 0 && System.currentTimeMillis() - minimizedAt >= 1500) restoreControls();
            return;
        }
        boolean examRunning = ExamTimer.running(this);
        boolean pomoRunning = Pomo.running();
        if (!timerStatesKnown || examRunning != previousExamRunning || pomoRunning != previousPomoRunning) {
            if (examRunning && pomoRunning) shownTimer = SHOW_EXAM;
            else if (examRunning) shownTimer = SHOW_EXAM;
            else if (pomoRunning) shownTimer = SHOW_POMODORO;
            else if (ExamTimer.active(this)) shownTimer = SHOW_EXAM;
            manualTimerChoice = false;
            timerStatesKnown = true;
            previousExamRunning = examRunning;
            previousPomoRunning = pomoRunning;
        }
        if (examRunning && pomoRunning && !manualTimerChoice) shownTimer = SHOW_EXAM;

        if (shownTimer == SHOW_EXAM) {
            long examLeft = ExamTimer.left(this);
            if (examRunning && examLeft <= 0) { ExamReceiver.finish(this); return; }
            phase.setText("Timer");
            phase.setContentDescription("Timer selected. Tap to switch to Pomodoro");
            timer.setText(UI.mmss(examLeft));
            timer.setTextColor(Color.WHITE);
            play.setText(examRunning ? "pause" : "play_arrow");
            play.setTextColor(UI.col(this, R.color.primary));
            play.setContentDescription(examRunning ? "Pause timer" : "Start timer");
            resetControl.setContentDescription("Reset timer");
            return;
        }
        boolean running = pomoRunning;
        long left = Pomo.left();
        if (running && left <= 0) {
            Pomo.finish();
            return;
        }
        phase.setText(Pomo.phaseName(Pomo.phase()));
        phase.setContentDescription("Pomodoro selected. Tap to switch to timer");
        timer.setText(UI.mmss(left));
        timer.setTextColor(Color.WHITE);
        play.setText(running ? "pause" : "play_arrow");
        play.setTextColor(UI.col(this, R.color.primary));
        play.setContentDescription(running ? "Pause Pomodoro" : "Start Pomodoro");
        resetControl.setContentDescription("Reset Pomodoro");
    }

    private void toggleActiveTimer() {
        if (shownTimer == SHOW_EXAM) ExamTimer.toggle(this); else Pomo.toggle();
        manualTimerChoice = false;
        update();
    }

    private void minimize() {
        if (overlay == null || minimized) return;
        minimized = true;
        minimizedAt = System.currentTimeMillis();
        overlay.removeAllViews();
        overlay.setPadding(0, 0, 0, 0);
        TextView bubble = UI.symbol(this, "timer", 25, Color.WHITE);
        bubble.setContentDescription("Show floating timer controls");
        bubble.setBackground(UI.ripple(UI.oval(UI.col(this, R.color.primary)), true, 0));
        bubble.setElevation(UI.dp(8));
        bubble.setOnClickListener(v -> restoreControls());
        attachDrag(bubble);
        overlay.addView(bubble, new LinearLayout.LayoutParams(UI.dp(54), UI.dp(54)));
        if (params != null) {
            params.width = UI.dp(54);
            params.height = UI.dp(54);
            try { windows.updateViewLayout(overlay, params); } catch (Exception ignored) { }
        }
    }

    private void restoreControls() {
        if (!minimized || overlay == null) return;
        int x = params == null ? UI.dp(16) : params.x;
        int y = params == null ? UI.dp(140) : params.y;
        try { windows.removeView(overlay); } catch (Exception ignored) { }
        overlay = null;
        minimized = false;
        attachOverlay();
        params.x = x;
        params.y = y;
        try { windows.updateViewLayout(overlay, params); } catch (Exception ignored) { }
        update();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                    "Floating timer controls", NotificationManager.IMPORTANCE_LOW));
        }
    }

    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 405, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setSmallIcon(R.drawable.ic_stat).setContentTitle("Pomodoro floating controls")
                .setContentText("Tap to open Focus").setOngoing(true).setContentIntent(content).build();
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(tick);
        Pomo.listeners.remove(stateChanged);
        if (overlay != null) {
            try { windows.removeView(overlay); } catch (Exception ignored) { }
            overlay = null;
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
