package dev.focus.pomodoro;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shown when an app's user-configured daily usage limit has been reached. */
public final class BlockedActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        UI.init(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(UI.dp(32), UI.dp(32), UI.dp(32), UI.dp(32));
        content.setBackgroundColor(UI.col(this, R.color.bg));
        TextView title = UI.tv(this, "Daily limit reached", 28, UI.col(this, R.color.text), true);
        title.setGravity(Gravity.CENTER);
        content.addView(title);
        TextView detail = UI.tv(this, "Your time limit for this app is up for today.", 16, UI.col(this, R.color.text2), false);
        detail.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(UI.M, UI.W);
        detailLp.topMargin = UI.dp(12);
        content.addView(detail, detailLp);
        TextView done = UI.tv(this, "Back to Focus", 16, UI.col(this, R.color.primary), true);
        done.setGravity(Gravity.CENTER);
        done.setPadding(UI.dp(20), UI.dp(14), UI.dp(20), UI.dp(14));
        done.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(UI.W, UI.W);
        buttonLp.topMargin = UI.dp(24);
        content.addView(done, buttonLp);
        setContentView(content);
    }
}