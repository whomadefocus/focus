package dev.focus.pomodoro;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/** PDF library list with per-file reading progress. */
final class PdfPage extends LinearLayout implements MainActivity.Refresh {
    private final MainActivity a;
    private Rows<PdfLib.Doc> rows;
    private TextView empty;

    PdfPage(MainActivity act) {
        super(act);
        a = act;
        setOrientation(VERTICAL);
        int text = UI.col(a, R.color.text);
        setBackgroundColor(UI.col(a, R.color.bg));

        ImageView addF = UI.ib(a, R.drawable.ic_add, text, 48, 12);
        addF.setContentDescription("Add PDF files");
        addF.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { a.pickPdfs(); } });
        ImageView addD = UI.ib(a, R.drawable.ic_folder, text, 48, 12);
        addD.setContentDescription("Add a folder of PDFs");
        addD.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { a.pickFolder(); } });
        addView(UI.topBar(a, "PDF library", new View.OnClickListener() { @Override public void onClick(View v) { a.back(); } }, addF, addD),
                new LinearLayout.LayoutParams(UI.M, UI.W));

        FrameLayout body = new FrameLayout(a);
        RecyclerView rv = new RecyclerView(a);
        rv.setLayoutManager(new LinearLayoutManager(a));
        rows = new Rows<PdfLib.Doc>() {
            @Override View make(Context c) { return makeRow(c); }

            @Override void bind(View v, final PdfLib.Doc d) {
                Object[] o = (Object[]) v.getTag();
                ((TextView) o[0]).setText(d.name);
                ((TextView) o[1]).setText(PdfLib.status(d));
                FrameLayout bar = (FrameLayout) o[2];
                bar.removeAllViews();
                bar.addView(UI.bar(a, PdfLib.pct(d)), new FrameLayout.LayoutParams(UI.M, UI.M));
                v.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) { a.openPdf(d.uri); } });
                v.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View x) {
                        new AlertDialog.Builder(a).setTitle("Remove from library?").setMessage(d.name)
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface di, int w) {
                                        PdfLib.remove(d.uri);
                                        refresh();
                                    }
                                }).show();
                        return true;
                    }
                });
            }
        };
        rv.setAdapter(rows);
        body.addView(rv, new FrameLayout.LayoutParams(UI.M, UI.M));
        empty = UI.tv(a, "No PDFs yet.\nTap + to pick files, or the folder icon to add a whole folder.", 15,
                UI.col(a, R.color.text2), false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(UI.dp(32), 0, UI.dp(32), 0);
        body.addView(empty, new FrameLayout.LayoutParams(UI.M, UI.M));
        addView(body, new LinearLayout.LayoutParams(UI.M, 0, 1f));
        refresh();
    }

    private View makeRow(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(VERTICAL);
        r.setBackground(UI.ripple(null, false, 0));
        r.setLayoutParams(new RecyclerView.LayoutParams(UI.M, UI.W));
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(UI.dp(16), UI.dp(12), UI.dp(16), UI.dp(10));
        ImageView icon = new ImageView(c);
        icon.setImageResource(R.drawable.ic_pdf);
        icon.setColorFilter(UI.col(c, R.color.primary));
        top.addView(icon, new LinearLayout.LayoutParams(UI.dp(24), UI.dp(24)));
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(VERTICAL);
        TextView name = UI.tv(c, "", 16, UI.col(c, R.color.text), false);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        TextView sub = UI.tv(c, "", 13, UI.col(c, R.color.text2), false);
        t.addView(name, new LinearLayout.LayoutParams(UI.M, UI.W));
        t.addView(sub, new LinearLayout.LayoutParams(UI.M, UI.W));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, UI.W, 1f);
        tl.leftMargin = UI.dp(16);
        top.addView(t, tl);
        r.addView(top, new LinearLayout.LayoutParams(UI.M, UI.W));
        FrameLayout bar = new FrameLayout(c);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(UI.M, UI.dp(3));
        bl.setMargins(UI.dp(16), 0, UI.dp(16), UI.dp(8));
        r.addView(bar, bl);
        r.setTag(new Object[]{name, sub, bar});
        return r;
    }

    @Override public void refresh() {
        ArrayList<PdfLib.Doc> l;
        synchronized (PdfLib.class) { l = new ArrayList<>(PdfLib.all()); }
        rows.set(l);
        empty.setVisibility(l.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
