package dev.focus.pomodoro;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Compact player controller used on the main page and the music page. */
final class MiniPlayer extends LinearLayout {
    private final TextView title, sub;
    private final ImageView play;
    private final TextView elapsed, duration, shuffle, repeat;
    private final SeekBar seek;
    private boolean seeking;
    private final Runnable refresh = new Runnable() { @Override public void run() { update(); } };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateProgress();
            postDelayed(this, 500);
        }
    };

    MiniPlayer(Context c, final Runnable open) {
        super(c);
        setOrientation(VERTICAL);
        setPadding(UI.dp(16), UI.dp(6), UI.dp(8), UI.dp(6));
        setBackground(UI.ripple(UI.rect(UI.col(c, R.color.surface), 12), false, 12));
        int text = UI.col(c, R.color.text), muted = UI.col(c, R.color.text2);

        LinearLayout controls = new LinearLayout(c);
        controls.setGravity(Gravity.CENTER_VERTICAL);
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
        controls.addView(t, new LinearLayout.LayoutParams(0, UI.W, 1f));

        ImageView prev = UI.ib(c, R.drawable.ic_prev, text, 44, 10);
        prev.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Player.prev(); } });
        play = UI.ib(c, R.drawable.ic_play, UI.col(c, R.color.primary), 48, 10);
        play.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (!Player.toggle()) open.run(); }
        });
        ImageView next = UI.ib(c, R.drawable.ic_next, text, 44, 10);
        next.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Player.next(); } });
        controls.addView(prev, new LinearLayout.LayoutParams(UI.dp(44), UI.dp(44)));
        controls.addView(play, new LinearLayout.LayoutParams(UI.dp(48), UI.dp(48)));
        controls.addView(next, new LinearLayout.LayoutParams(UI.dp(44), UI.dp(44)));
        addView(controls, new LinearLayout.LayoutParams(UI.M, UI.W));

        LinearLayout progress = new LinearLayout(c);
        progress.setGravity(Gravity.CENTER_VERTICAL);
        elapsed = UI.tv(c, "0:00", 11, muted, false);
        duration = UI.tv(c, "0:00", 11, muted, false);
        seek = new SeekBar(c);
        seek.setMax(1000);
        progress.addView(elapsed, new LinearLayout.LayoutParams(UI.dp(38), UI.W));
        progress.addView(seek, new LinearLayout.LayoutParams(0, UI.dp(34), 1f));
        progress.addView(duration, new LinearLayout.LayoutParams(UI.dp(38), UI.W));
        addView(progress, new LinearLayout.LayoutParams(UI.M, UI.W));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser) elapsed.setText(format(Player.duration() * value / 1000));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { seeking = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                int length = Player.duration();
                if (length > 0) Player.seek(length * bar.getProgress() / 1000);
                seeking = false;
                updateProgress();
            }
        });

        LinearLayout modes = new LinearLayout(c);
        modes.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        shuffle = UI.symbolButton(c, "shuffle", muted, 36, 20);
        shuffle.setContentDescription("Shuffle");
        shuffle.setOnClickListener(v -> Player.setShuffle(!Player.shuffle));
        repeat = UI.symbolButton(c, "repeat", muted, 36, 20);
        repeat.setContentDescription("Repeat mode");
        repeat.setOnClickListener(v -> Player.cycleRepeat());
        modes.addView(shuffle);
        modes.addView(repeat);
        addView(modes, new LinearLayout.LayoutParams(UI.M, UI.dp(32)));
        update();
    }

    private void update() {
        Player.Track t = Player.cur;
        title.setText(t == null ? "Nothing playing" : t.title);
        sub.setText(t == null ? "Tap to choose music" : t.sub);
        play.setImageResource(Player.playing ? R.drawable.ic_pause : R.drawable.ic_play);
        shuffle.setAlpha(Player.shuffle ? 1f : 0.45f);
        shuffle.setContentDescription(Player.shuffle ? "Turn shuffle off" : "Turn shuffle on");
        repeat.setText(Player.repeatMode == Player.REPEAT_ONE ? "repeat_one" : "repeat");
        repeat.setAlpha(Player.repeatMode == Player.REPEAT_OFF ? 0.45f : 1f);
        repeat.setContentDescription(Player.repeatMode == Player.REPEAT_OFF ? "Repeat off"
                : Player.repeatMode == Player.REPEAT_ONE ? "Repeat one" : "Repeat all");
        updateProgress();
    }

    private void updateProgress() {
        int length = Player.duration();
        int position = Player.position();
        boolean available = length > 0;
        seek.setEnabled(available);
        seek.setAlpha(available ? 1f : 0.4f);
        duration.setText(format(length));
        if (!seeking) {
            elapsed.setText(format(position));
            if (available) seek.setProgress((int) ((long) position * 1000 / length));
        }
        if (!available && !seeking) {
            seek.setProgress(0);
        }
    }

    private String format(long milliseconds) {
        long seconds = Math.max(0, milliseconds / 1000);
        return String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Player.listeners.add(refresh);
        update();
        removeCallbacks(tick);
        post(tick);
    }

    @Override protected void onDetachedFromWindow() {
        Player.listeners.remove(refresh);
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }
}
