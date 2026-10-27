package dev.focus.pomodoro;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Installed app list with launcher icons and direct launch actions. */
final class AppDrawerPage extends LinearLayout {
    private final MainActivity a;
    private final boolean selecting;
    private final List<ResolveInfo> allApps;
    private Rows<ResolveInfo> rows;
    private AlphabetRail rail;

    AppDrawerPage(MainActivity activity) {
        this(activity, false);
    }

    AppDrawerPage(MainActivity activity, boolean selecting) {
        super(activity);
        a = activity;
        this.selecting = selecting;
        setOrientation(VERTICAL);
        setBackgroundColor(UI.col(a, R.color.bg));
        addView(UI.topBar(a, selecting ? "Add shortcut" : "All apps", new View.OnClickListener() {
            @Override public void onClick(View v) { a.back(); }
        }), new LinearLayout.LayoutParams(UI.M, UI.W));

        final PackageManager pm = a.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        allApps = pm.queryIntentActivities(query, 0);
        Collections.sort(allApps, new ResolveInfo.DisplayNameComparator(pm));
        EditText search = new EditText(a);
        search.setSingleLine(true);
        search.setHint("Search apps");
        search.setPadding(UI.dp(16), 0, UI.dp(16), 0);
        search.setTextColor(UI.col(a, R.color.text));
        search.setHintTextColor(UI.col(a, R.color.text2));
        search.setBackground(UI.rect(UI.col(a, R.color.surface), 8));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(UI.M, UI.dp(48));
        searchLp.setMargins(UI.dp(12), UI.dp(8), UI.dp(12), UI.dp(2));
        addView(search, searchLp);

        RecyclerView list = new RecyclerView(a);
        list.setLayoutManager(new LinearLayoutManager(a));
        rows = new Rows<ResolveInfo>() {
            @Override View make(Context c) {
                LinearLayout row = new LinearLayout(c);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(UI.dp(16), UI.dp(10), UI.dp(16), UI.dp(10));
                ImageView icon = new ImageView(c);
                row.addView(icon, new LinearLayout.LayoutParams(UI.dp(40), UI.dp(40)));
                TextView name = UI.tv(c, "", 16, UI.col(c, R.color.text), false);
                LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, UI.W, 1f);
                labelLp.leftMargin = UI.dp(16);
                row.addView(name, labelLp);
                row.setTag(new Object[]{icon, name});
                row.setLayoutParams(new RecyclerView.LayoutParams(UI.M, UI.dp(60)));
                return row;
            }

            @Override void bind(View view, final ResolveInfo info) {
                Object[] holder = (Object[]) view.getTag();
                ((ImageView) holder[0]).setImageDrawable(info.loadIcon(pm));
                ((TextView) holder[1]).setText(info.loadLabel(pm));
                view.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (selecting) {
                            android.content.SharedPreferences prefs = a.getSharedPreferences("shortcuts", 0);
                            java.util.Set<String> packages = new java.util.HashSet<>(prefs.getStringSet(
                                    "packages", Collections.<String>emptySet()));
                            packages.add(info.activityInfo.packageName);
                            prefs.edit().putStringSet("packages", packages).apply();
                            a.toast("Shortcut added");
                            a.show(MainActivity.HOME);
                        } else {
                            Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                                    .setComponent(new android.content.ComponentName(info.activityInfo.packageName, info.activityInfo.name));
                            try { a.startActivity(launch); } catch (Exception e) { a.toast("App could not be opened"); }
                        }
                    }
                });
            }
        };
        rows.set(new ArrayList<>(allApps));
        list.setAdapter(rows);
        FrameLayout listFrame = new FrameLayout(a);
        list.setPadding(0, 0, UI.dp(24), 0);
        list.setClipToPadding(false);
        listFrame.addView(list, new FrameLayout.LayoutParams(UI.M, UI.M));
        rail = new AlphabetRail(a, list);
        listFrame.addView(rail, new FrameLayout.LayoutParams(UI.dp(22), UI.M, Gravity.END | Gravity.CENTER_VERTICAL));
        ArrayList<String> appLabels = new ArrayList<>();
        for (ResolveInfo app : allApps) appLabels.add(app.loadLabel(pm).toString());
        rail.setLabels(appLabels);
        addView(listFrame, new LinearLayout.LayoutParams(UI.M, 0, 1f));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(s.toString(), pm); }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void filter(String query, PackageManager pm) {
        String normalized = query.trim().toLowerCase(java.util.Locale.getDefault());
        ArrayList<ResolveInfo> matches = new ArrayList<>();
        for (ResolveInfo app : allApps) {
            String label = app.loadLabel(pm).toString().toLowerCase(java.util.Locale.getDefault());
            if (normalized.isEmpty() || label.contains(normalized)) matches.add(app);
        }
        rows.set(matches);
        ArrayList<String> labels = new ArrayList<>();
        for (ResolveInfo app : matches) labels.add(app.loadLabel(pm).toString());
        rail.setLabels(labels);
    }
}