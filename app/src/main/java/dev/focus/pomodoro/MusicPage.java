package dev.focus.pomodoro;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Plain list of the device's music; first entry is the built-in white noise. */
final class MusicPage extends LinearLayout implements MainActivity.Refresh {
    private final MainActivity a;
    private Rows<Player.Track> rows;
    private TextView perm;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable redraw = new Runnable() {
        @Override public void run() { rows.notifyDataSetChanged(); }
    };

    MusicPage(MainActivity act) {
        super(act);
        a = act;
        setOrientation(VERTICAL);
        setBackgroundColor(UI.col(a, R.color.bg));
        ImageView refresh = UI.ib(a, R.drawable.ic_reset, UI.col(a, R.color.text), 48, 12);
        refresh.setContentDescription("Rescan music library");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Player.clearScanCache(); load(); }
        });
        addView(UI.topBar(a, "Music", new View.OnClickListener() { @Override public void onClick(View v) { a.back(); } }, refresh),
                new LinearLayout.LayoutParams(UI.M, UI.W));

        perm = UI.tv(a, "Tap to allow access to your music library", 14, UI.col(a, R.color.primary), true);
        perm.setPadding(UI.dp(16), UI.dp(14), UI.dp(16), UI.dp(14));
        perm.setBackground(UI.ripple(null, false, 0));
        perm.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { a.askAudio(); } });
        addView(perm, new LinearLayout.LayoutParams(UI.M, UI.W));

        RecyclerView rv = new RecyclerView(a);
        rv.setLayoutManager(new LinearLayoutManager(a));
        rows = new Rows<Player.Track>() {
            @Override View make(Context c) { return makeRow(c); }

            @Override void bind(View v, final Player.Track t) {
                Object[] o = (Object[]) v.getTag();
                TextView title = (TextView) o[0], sub = (TextView) o[1];
                ImageView icon = (ImageView) o[2], art = (ImageView) o[3];
                boolean cur = Player.isCurrent(t);
                int color = UI.col(a, cur ? R.color.primary : R.color.text);
                title.setText(t.title);
                title.setTextColor(color);
                sub.setText(t.sub);
                icon.setImageResource(t.noise ? R.drawable.ic_noise : (cur && Player.playing ? R.drawable.ic_pause : R.drawable.ic_play));
                icon.setColorFilter(cur ? UI.col(a, R.color.primary) : UI.col(a, R.color.text2));
                if (t.noise) {
                    art.setImageResource(R.drawable.ic_noise);
                    art.setTag(null);
                } else Artwork.load(a, t.albumId, art);
                v.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View x) {
                        if (Player.isCurrent(t) && Player.active()) Player.toggle(); else Player.play(t);
                    }
                });
            }
        };
        rv.setAdapter(rows);
        addView(rv, new LinearLayout.LayoutParams(UI.M, 0, 1f));

        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(UI.M, UI.W);
        mp.setMargins(UI.dp(8), UI.dp(4), UI.dp(8), UI.dp(8));
        addView(new MiniPlayer(a, new Runnable() { @Override public void run() { } }), mp);
        load();
    }

    private View makeRow(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(UI.dp(16), UI.dp(10), UI.dp(16), UI.dp(10));
        r.setBackground(UI.ripple(null, false, 0));
        r.setLayoutParams(new RecyclerView.LayoutParams(UI.M, UI.W));
        ImageView icon = new ImageView(c);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        ImageView art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackground(UI.rect(UI.col(c, R.color.divider), 4));
        r.addView(art, new LinearLayout.LayoutParams(UI.dp(48), UI.dp(48)));
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(UI.dp(24), UI.dp(24));
        il.leftMargin = UI.dp(10);
        r.addView(icon, il);
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(VERTICAL);
        TextView title = UI.tv(c, "", 16, UI.col(c, R.color.text), false);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView sub = UI.tv(c, "", 13, UI.col(c, R.color.text2), false);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(title, new LinearLayout.LayoutParams(UI.M, UI.W));
        t.addView(sub, new LinearLayout.LayoutParams(UI.M, UI.W));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, UI.W, 1f);
        tl.leftMargin = UI.dp(16);
        r.addView(t, tl);
        r.setTag(new Object[]{title, sub, icon, art});
        return r;
    }

    private void setList(List<Player.Track> found) {
        ArrayList<Player.Track> l = new ArrayList<>();
        l.add(Player.NOISE);
        l.addAll(found);
        Player.queue = l;
        rows.set(l);
    }

    /** (Re)loads the library; also called after the permission dialog. */
    @Override public void refresh() { load(); }

    private void load() {
        boolean ok = a.hasAudio();
        perm.setVisibility(ok ? View.GONE : View.VISIBLE);
        if (!ok) { setList(new ArrayList<Player.Track>()); return; }
        new Thread(new Runnable() {
            @Override public void run() {
                final List<Player.Track> found = Player.scan(a);
                ui.post(new Runnable() { @Override public void run() { setList(found); } });
            }
        }).start();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Player.listeners.add(redraw);
    }

    @Override protected void onDetachedFromWindow() {
        Player.listeners.remove(redraw);
        super.onDetachedFromWindow();
    }
}
