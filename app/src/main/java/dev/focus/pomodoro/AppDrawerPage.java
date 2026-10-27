package dev.focus.pomodoro;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
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

    AppDrawerPage(MainActivity activity) {
        super(activity);
        a = activity;
        setOrientation(VERTICAL);
        setBackgroundColor(UI.col(a, R.color.bg));
        addView(UI.topBar(a, "All apps", new View.OnClickListener() {
            @Override public void onClick(View v) { a.back(); }
        }), new LinearLayout.LayoutParams(UI.M, UI.W));

        final PackageManager pm = a.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> apps = pm.queryIntentActivities(query, 0);
        Collections.sort(apps, new ResolveInfo.DisplayNameComparator(pm));
        RecyclerView list = new RecyclerView(a);
        list.setLayoutManager(new LinearLayoutManager(a));
        Rows<ResolveInfo> rows = new Rows<ResolveInfo>() {
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
                        Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                                .setComponent(new android.content.ComponentName(info.activityInfo.packageName, info.activityInfo.name));
                        try { a.startActivity(launch); } catch (Exception e) { a.toast("App could not be opened"); }
                    }
                });
            }
        };
        rows.set(new ArrayList<>(apps));
        list.setAdapter(rows);
        addView(list, new LinearLayout.LayoutParams(UI.M, 0, 1f));
    }
}