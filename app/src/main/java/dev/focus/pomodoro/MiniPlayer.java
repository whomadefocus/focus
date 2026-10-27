package dev.focus.pomodoro;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Compact player controller used on the main page and the music page. */
final class MiniPlayer extends LinearLayout {
    private final TextView title, sub;
    private final ImageView play;
    private final Runnable refresh = new Runnable() { @Override public void run() { update(); } };

    MiniPlayer(Context c, final Runnable open) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(UI.dp(16), UI.dp(6), UI.dp(8), UI.dp(6));
        setBackground(UI.ripple(UI.rect(UI.col(c, R.color.surface), 12), false, 12));
        setElevation(UI.dp(2));
        int text = UI.col(c, R.color.text), muted = UI.col(c, R.color.text2);

        LinearLayout t = new LinearLayout(c);
        t.setOrientation(VERTICAL);
        title = UI.tv(c, "", 15, text, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        sub = UI.tv(c, "", 12, muted, false);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(title, new LinearLayout.LayoutParams(UI.M, UI.W));
        t.addView(sub, new LinearLayout.LayoutParams(UI.M, UI.W));
        t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { open.run(); } });
        addView(t, new LinearLayout.LayoutParams(0, UI.W, 1f));

        ImageView prev = UI.ib(c, R.drawable.ic_prev, text, 44, 10);
        prev.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Player.prev(); } });
        play = UI.ib(c, R.drawable.ic_play, UI.col(c, R.color.primary), 48, 10);
        play.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (!Player.toggle()) open.run(); }
        });
        ImageView next = UI.ib(c, R.drawable.ic_next, text, 44, 10);
        next.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Player.next(); } });
        addView(prev, new LinearLayout.LayoutParams(UI.dp(44), UI.dp(44)));
        addView(play, new LinearLayout.LayoutParams(UI.dp(48), UI.dp(48)));
        addView(next, new LinearLayout.LayoutParams(UI.dp(44), UI.dp(44)));
        update();
    }

    private void update() {
        Player.Track t = Player.cur;
        title.setText(t == null ? "Nothing playing" : t.title);
        sub.setText(t == null ? "Tap to choose music" : t.sub);
        play.setImageResource(Player.playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Player.listeners.add(refresh);
        update();
    }

    @Override protected void onDetachedFromWindow() {
        Player.listeners.remove(refresh);
        super.onDetachedFromWindow();
    }
}
