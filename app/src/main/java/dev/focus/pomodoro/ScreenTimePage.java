package dev.focus.pomodoro;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Daily usage overview and user-configured app limits. */
final class ScreenTimePage extends LinearLayout implements MainActivity.Refresh {
    private static final class AppUse {
        ResolveInfo info;
        String label, pkg;
        long used;
        int limit;
    }

    private final MainActivity a;
    private final ArrayList<AppUse> entries = new ArrayList<>();
    private Rows<AppUse> rows;
    private TextView accessMessage;

    ScreenTimePage(MainActivity activity) {
        super(activity);
        a = activity;
        setOrientation(VERTICAL);
        setBackgroundColor(UI.col(a, R.color.bg));
        addView(UI.topBar(a, "Screen time", new View.OnClickListener() {
            @Override public void onClick(View v) { a.back(); }
        }), new LinearLayout.LayoutParams(UI.M, UI.W));

        LinearLayout actions = new LinearLayout(a);
        actions.setGravity(Gravity.CENTER);
        actions.addView(action("Usage access", new Runnable() {
            @Override public void run() { openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS); }
        }), new LinearLayout.LayoutParams(0, UI.dp(48), 1f));
        actions.addView(action("Enable blocking", new Runnable() {
            @Override public void run() { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS); }
        }), new LinearLayout.LayoutParams(0, UI.dp(48), 1f));
        actions.addView(action("Set as launcher", new Runnable() {
            @Override public void run() { requestHomeRole(); }
        }), new LinearLayout.LayoutParams(0, UI.dp(48), 1f));
        addView(actions, new LinearLayout.LayoutParams(UI.M, UI.W));

        accessMessage = UI.tv(a, "Grant Usage access to view today's app usage. Enable the Focus accessibility service to enforce limits.",
                14, UI.col(a, R.color.text2), false);
        accessMessage.setPadding(UI.dp(16), UI.dp(12), UI.dp(16), UI.dp(12));
        addView(accessMessage);

        RecyclerView list = new RecyclerView(a);
        list.setLayoutManager(new LinearLayoutManager(a));
        rows = new Rows<AppUse>() {
            @Override View make(Context c) {
                LinearLayout line = new LinearLayout(c);
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding(UI.dp(14), UI.dp(10), UI.dp(14), UI.dp(10));
                ImageView icon = new ImageView(c);
                line.addView(icon, new LinearLayout.LayoutParams(UI.dp(36), UI.dp(36)));
                LinearLayout labels = new LinearLayout(c);
                labels.setOrientation(VERTICAL);
                TextView name = UI.tv(c, "", 15, UI.col(c, R.color.text), true);
                TextView use = UI.tv(c, "", 13, UI.col(c, R.color.text2), false);
                labels.addView(name);
                labels.addView(use);
                LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, UI.W, 1f);
                textLp.leftMargin = UI.dp(12);
                line.addView(labels, textLp);
                TextView limit = UI.tv(c, "Limit", 13, UI.col(c, R.color.primary), true);
                limit.setGravity(Gravity.CENTER);
                line.addView(limit, new LinearLayout.LayoutParams(UI.dp(56), UI.dp(44)));
                line.setTag(new Object[]{icon, name, use, limit});
                line.setLayoutParams(new RecyclerView.LayoutParams(UI.M, UI.W));
                return line;
            }

            @Override void bind(View view, final AppUse item) {
                Object[] holder = (Object[]) view.getTag();
                ((ImageView) holder[0]).setImageDrawable(item.info.loadIcon(a.getPackageManager()));
                ((TextView) holder[1]).setText(item.label);
                ((TextView) holder[2]).setText(format(item.used) + " used today" + (item.limit > 0 ? " · " + item.limit + " min limit" : ""));
                ((TextView) holder[3]).setText(item.limit > 0 ? "Edit" : "Limit");
                ((View) holder[3]).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { editLimit(item); }
                });
            }
        };
        list.setAdapter(rows);
        addView(list, new LinearLayout.LayoutParams(UI.M, 0, 1f));
        refresh();
    }

    private View action(String title, final Runnable run) {
        TextView button = UI.tv(a, title, 12, UI.col(a, R.color.primary), true);
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { run.run(); } });
        return button;
    }

    private void openSettings(String action) {
        try { a.startActivity(new Intent(action)); }
        catch (Exception e) { a.toast("This system setting is unavailable"); }
    }

    private void requestHomeRole() {
        if (Build.VERSION.SDK_INT >= 29) {
            android.app.role.RoleManager roles = (android.app.role.RoleManager) a.getSystemService(Context.ROLE_SERVICE);
            if (roles != null && roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                a.startActivityForResult(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), 74);
                return;
            }
        }
        openSettings(Settings.ACTION_HOME_SETTINGS);
    }

    private boolean hasUsageAccess() {
        try {
            if (Build.VERSION.SDK_INT < 23) {
                UsageStatsManager manager = usageStatsManager();
                List<UsageStats> stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,
                        System.currentTimeMillis() - 86400000L, System.currentTimeMillis());
                return stats != null && !stats.isEmpty();
            }
            AppOpsManager ops = (AppOpsManager) a.getSystemService(Context.APP_OPS_SERVICE);
            return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), a.getPackageName()) == AppOpsManager.MODE_ALLOWED;
        } catch (Exception ignored) { return false; }
    }

    @SuppressLint("WrongConstant")
    private UsageStatsManager usageStatsManager() {
        return (UsageStatsManager) a.getSystemService("usagestats");
    }

    private void editLimit(final AppUse item) {
        final EditText input = new EditText(a);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Minutes per day; 0 removes the limit");
        if (item.limit > 0) input.setText(String.valueOf(item.limit));
        new AlertDialog.Builder(a).setTitle("Daily limit · " + item.label).setView(input)
                .setNegativeButton("Cancel", null).setPositiveButton("Save", (dialog, which) -> {
                    int minutes = 0;
                    try { minutes = Math.max(0, Math.min(1440, Integer.parseInt(input.getText().toString().trim()))); }
                    catch (Exception ignored) { }
                    a.getSharedPreferences("screen_time", 0).edit().putInt(item.pkg, minutes).apply();
                    refresh();
                }).show();
    }

    @Override public void refresh() {
        entries.clear();
        boolean access = hasUsageAccess();
        accessMessage.setVisibility(access ? View.GONE : View.VISIBLE);
        Map<String, Long> usage = new HashMap<>();
        if (access) {
            Calendar start = Calendar.getInstance();
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            UsageStatsManager manager = usageStatsManager();
            List<UsageStats> stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,
                    start.getTimeInMillis(), System.currentTimeMillis());
            if (stats != null) for (UsageStats stat : stats) usage.put(stat.getPackageName(), stat.getTotalTimeInForeground());
        }
        PackageManager pm = a.getPackageManager();
        List<ResolveInfo> apps = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        Map<String, AppUse> unique = new HashMap<>();
        for (ResolveInfo info : apps) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(a.getPackageName()) || unique.containsKey(pkg)) continue;
            AppUse item = new AppUse();
            item.info = info;
            item.pkg = pkg;
            item.label = info.loadLabel(pm).toString();
            item.used = usage.containsKey(pkg) ? usage.get(pkg) : 0;
            item.limit = a.getSharedPreferences("screen_time", 0).getInt(pkg, 0);
            unique.put(pkg, item);
        }
        entries.addAll(unique.values());
        Collections.sort(entries, (left, right) -> Long.compare(right.used, left.used));
        rows.set(new ArrayList<>(entries));
    }

    private static String format(long ms) {
        long minutes = ms / 60000;
        return minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
    }
}