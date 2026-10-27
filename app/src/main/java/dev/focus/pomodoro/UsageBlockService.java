package dev.focus.pomodoro;

import android.annotation.SuppressLint;
import android.accessibilityservice.AccessibilityService;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Calendar;
import java.util.List;

/** Enforces user-configured limits after the user explicitly enables the accessibility service. */
public final class UsageBlockService extends AccessibilityService {
    private String checkedPackage;
    private long checkedAt;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getEventType() != AccessibilityEvent.TYPE_WINDOWS_CHANGED)) return;
        CharSequence name = event.getPackageName();
        if (name == null) return;
        final String pkg = name.toString();
        if (pkg.equals(getPackageName())) return;
        long now = System.currentTimeMillis();
        if (pkg.equals(checkedPackage) && now - checkedAt < 1500) return;
        checkedPackage = pkg;
        checkedAt = now;

        int limit = getSharedPreferences("screen_time", MODE_PRIVATE).getInt(pkg, 0);
        if (limit <= 0 || !overLimit(pkg, limit, now)) return;
        Intent block = new Intent(this, BlockedActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("package", pkg);
        try { startActivity(block); } catch (Exception ignored) { }
    }

    private boolean overLimit(String pkg, int limitMinutes, long now) {
        UsageStatsManager manager = usageStatsManager();
        if (manager == null) return false;
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        List<UsageStats> stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start.getTimeInMillis(), now);
        if (stats == null) return false;
        long used = 0;
        for (UsageStats stat : stats) if (pkg.equals(stat.getPackageName())) used = Math.max(used, stat.getTotalTimeInForeground());
        return used >= limitMinutes * 60000L;
    }

    @SuppressLint("WrongConstant")
    private UsageStatsManager usageStatsManager() {
        return (UsageStatsManager) getSystemService("usagestats");
    }

    @Override public void onInterrupt() { }
}