package dev.focus.pomodoro;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.util.LruCache;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.AppCompatImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Minimal PDF reader: continuous vertical scroll, landscape by default, no selection. */
public class PdfActivity extends Activity {
    private static final int MAX_W = 1200;

    private PdfRenderer renderer;
    private ParcelFileDescriptor pfd;
    private final Object lock = new Object();
    private volatile boolean closed;

    private RecyclerView rv;
    private LinearLayoutManager lm;
    private PageAdapter ad;
    private int count, page;
    private int[] pw, ph;
    private String uri;

    private LinearLayout bar;
    private ImageView showBtn, rotBtn;
    private TextView pageTv, timeTv;
    private ProgressBar spin;
    private PageRail pageRail;
    private boolean landscape = true;
    private int shownPhase = -1;
    private float documentZoom = 1f, documentX, documentY;
    private float lastTouchX, lastTouchY;
    private final LruCache<String, Bitmap> bitmaps = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

        private final ThreadPoolExecutor ex = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>());
        private final ConcurrentHashMap<String, FutureTask<Void>> pending = new ConcurrentHashMap<>();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable onState = new Runnable() { @Override public void run() { tickUi(); } };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            tickUi();
            h.postDelayed(this, 500);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        UI.applyTheme(this);
        UI.init(this);
        Pomo.init(this);
        PdfLib.init(this);
        uri = getIntent().getStringExtra("uri");
        if (uri == null) { finish(); return; }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        if (getSharedPreferences("reader", MODE_PRIVATE).getBoolean("keep_screen", true)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF3A3A3A);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(UI.M, UI.M));

        bar = buildBar();
        col.addView(bar, new LinearLayout.LayoutParams(UI.M, UI.dp(52)));

        rv = new RecyclerView(this);
        lm = new LinearLayoutManager(this);
        rv.setLayoutManager(lm);
        rv.setItemViewCacheSize(2);
        lm.setInitialPrefetchItemCount(2);
        col.addView(rv, new LinearLayout.LayoutParams(UI.M, 0, 1f));

        spin = new ProgressBar(this);
        root.addView(spin, new FrameLayout.LayoutParams(UI.dp(48), UI.dp(48), Gravity.CENTER));

        showBtn = UI.circle(this, R.drawable.ic_eye, 40, 0x99000000, Color.WHITE, 22);
        showBtn.setContentDescription("Show toolbar");
        showBtn.setVisibility(View.GONE);
        showBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setBar(true); } });
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(UI.dp(40), UI.dp(40), Gravity.TOP | Gravity.END);
        slp.setMargins(0, UI.dp(8), UI.dp(8), 0);
        root.addView(showBtn, slp);
        pageRail = new PageRail(this);
        FrameLayout.LayoutParams railLp = new FrameLayout.LayoutParams(UI.dp(22), UI.M, Gravity.END | Gravity.CENTER_VERTICAL);
        railLp.setMargins(0, UI.dp(60), UI.dp(2), UI.dp(12));
        root.addView(pageRail, railLp);
        setContentView(root);

        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView r, int dx, int dy) { if (dy != 0) track(); }

            @Override public void onScrollStateChanged(RecyclerView r, int s) {
                if (s == RecyclerView.SCROLL_STATE_IDLE) save();
            }
        });
        final ScaleGestureDetector scaler = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                scaleDocument(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
        rv.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override public boolean onInterceptTouchEvent(RecyclerView view, MotionEvent event) {
                boolean transformed = Math.abs(documentZoom - 1f) >= 0.01f;
                if (event.getPointerCount() > 1 || transformed) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                    }
                    return true;
                }
                return false;
            }

            @Override public void onTouchEvent(RecyclerView view, MotionEvent event) {
                scaler.onTouchEvent(event);
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE && !scaler.isInProgress()
                        && Math.abs(documentZoom - 1f) >= 0.01f) {
                    documentX += event.getX() - lastTouchX;
                    documentY += event.getY() - lastTouchY;
                    applyDocumentTransform();
                }
                lastTouchX = event.getX();
                lastTouchY = event.getY();
            }
        });
        // keeps the current page when the width changes (rotation, hiding system bars)
        rv.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b2, int ol, int ot, int or2, int ob) {
                int w = r - l;
                if (ad != null && w > 0 && w != ad.width) {
                    int keep = page;
                    ad.width = w;
                    ad.notifyDataSetChanged();
                    lm.scrollToPositionWithOffset(keep, 0);
                }
            }
        });

        new Thread(new Runnable() {
            @Override public void run() { open(); }
        }).start();
    }

    private void open() {
        try {
            pfd = getContentResolver().openFileDescriptor(Uri.parse(uri), "r");
            renderer = new PdfRenderer(pfd);
            final int n = renderer.getPageCount();
            runOnUiThread(new Runnable() { @Override public void run() { ready(n, new int[n], new int[n]); } });
        } catch (Exception e) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    Toast.makeText(PdfActivity.this, "Can't open this PDF", Toast.LENGTH_LONG).show();
                    finish();
                }
            });
        }
    }

    private void ready(int n, int[] w, int[] hh) {
        if (closed) { closeAll(); return; }
        if (n == 0) {
            Toast.makeText(this, "This PDF has no pages", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        count = n;
        pw = w;
        ph = hh;
        PdfLib.Doc d = PdfLib.find(uri);
        page = d == null ? 0 : Math.max(0, Math.min(d.page, n - 1));
        spin.setVisibility(View.GONE);
        ad = new PageAdapter();
        ad.width = rv.getWidth();
        rv.setAdapter(ad);
        pageTv.setText((page + 1) + " / " + n);
        pageRail.setVisibility(n > 1 ? View.VISIBLE : View.GONE);
        pageRail.invalidate();
        PdfLib.progress(uri, page, n);
        rv.post(new Runnable() { @Override public void run() { lm.scrollToPositionWithOffset(page, 0); } });
    }

    // ---------- toolbar ----------
    private TextView chip(String s, int bg, int fg) {
        TextView t = UI.tv(this, s, 14, fg, true);
        t.setPadding(UI.dp(12), UI.dp(6), UI.dp(12), UI.dp(6));
        t.setGravity(Gravity.CENTER);
        t.setBackground(UI.ripple(UI.rect(bg, 16), false, 16));
        return t;
    }

    private LinearLayout buildBar() {
        int text = UI.col(this, R.color.text), muted = UI.col(this, R.color.text2);
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setBackgroundColor(UI.col(this, R.color.surface));
        b.setPadding(UI.dp(4), 0, UI.dp(8), 0);

        ImageView back = UI.ib(this, R.drawable.ic_back, text, 48, 12);
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        b.addView(back, new LinearLayout.LayoutParams(UI.dp(48), UI.dp(48)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        PdfLib.Doc d = PdfLib.find(getIntent().getStringExtra("uri"));
        TextView name = UI.tv(this, d == null ? "PDF" : d.name, 15, text, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        pageTv = UI.tv(this, "", 12, muted, false);
        titles.addView(name, new LinearLayout.LayoutParams(UI.M, UI.W));
        titles.addView(pageTv, new LinearLayout.LayoutParams(UI.M, UI.W));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, UI.W, 1f);
        tl.leftMargin = UI.dp(4);
        b.addView(titles, tl);

        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(UI.W, UI.W);
        cl.setMargins(UI.dp(4), 0, UI.dp(4), 0);
        // pomodoro time: tap = pause / resume
        timeTv = chip("--:--", UI.col(this, R.color.primary), Color.WHITE);
        timeTv.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Pomo.toggle(); } });
        b.addView(timeTv, cl);
        // +5 minutes
        TextView plus = chip("+5", UI.col(this, R.color.divider), text);
        plus.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Pomo.extend(5); } });
        b.addView(plus, new LinearLayout.LayoutParams(cl));

        rotBtn = UI.ib(this, R.drawable.ic_port, text, 48, 12);
        rotBtn.setContentDescription("Rotate screen");
        rotBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { rotate(); } });
        b.addView(rotBtn, new LinearLayout.LayoutParams(UI.dp(48), UI.dp(48)));

        ImageView hide = UI.ib(this, R.drawable.ic_eye_off, text, 48, 12);
        hide.setContentDescription("Hide toolbar");
        hide.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setBar(false); } });
        b.addView(hide, new LinearLayout.LayoutParams(UI.dp(48), UI.dp(48)));
        return b;
    }

    private void rotate() {
        landscape = !landscape;
        setRequestedOrientation(landscape ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        rotBtn.setImageResource(landscape ? R.drawable.ic_port : R.drawable.ic_land);
    }

    @SuppressWarnings("deprecation")
    private void setBar(boolean visible) {
        bar.setVisibility(visible ? View.VISIBLE : View.GONE);
        showBtn.setVisibility(visible ? View.GONE : View.VISIBLE);
        getWindow().getDecorView().setSystemUiVisibility(visible ? View.SYSTEM_UI_FLAG_VISIBLE
                : View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void tickUi() {
        if (timeTv == null) return;
        boolean run = Pomo.running();
        long left = Pomo.left();
        if (run && left <= 0) { Pomo.finish(); return; }
        int ph = Pomo.phase();
        if (ph != shownPhase) {
            shownPhase = ph;
            timeTv.setBackground(UI.ripple(UI.rect(UI.col(this, ph == Pomo.FOCUS ? R.color.primary : R.color.green), 16), false, 16));
        }
        timeTv.setText(UI.mmss(left));
        timeTv.setAlpha(run ? 1f : 0.55f);
    }

    // ---------- progress ----------
    private void track() {
        if (count == 0) return;
        int f = lm.findFirstVisibleItemPosition();
        if (f < 0) return;
        View v = lm.findViewByPosition(f);
        if (v != null && v.getBottom() < rv.getHeight() * 0.3f && f + 1 < count) f++;
        page = f;
        pageTv.setText((page + 1) + " / " + count);
        pageRail.invalidate();
    }

    private void save() {
        if (count > 0) PdfLib.progress(uri, page, count);
    }

    private final class PageRail extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        PageRail(Context context) {
            super(context);
            setContentDescription("PDF page position");
            setBackground(UI.rect(0x66000000, 10));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (count < 2) return;
            float center = getWidth() / 2f;
            float top = UI.dp(8), bottom = getHeight() - UI.dp(8);
            paint.setColor(0x88FFFFFF);
            paint.setStrokeWidth(UI.dp(2));
            canvas.drawLine(center, top, center, bottom, paint);
            float y = top + (bottom - top) * page / (count - 1f);
            paint.setColor(UI.col(PdfActivity.this, R.color.primary));
            canvas.drawCircle(center, y, UI.dp(6), paint);
            setContentDescription("Page " + (page + 1) + " of " + count);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (count < 2) return false;
            int action = event.getActionMasked();
            if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE
                    && action != MotionEvent.ACTION_UP) return true;
            float fraction = (event.getY() - UI.dp(8)) / Math.max(1f, getHeight() - UI.dp(16));
            int target = Math.max(0, Math.min(count - 1, Math.round(fraction * (count - 1))));
            page = target;
            lm.scrollToPositionWithOffset(target, 0);
            pageTv.setText((target + 1) + " / " + count);
            invalidate();
            if (action == MotionEvent.ACTION_UP) performClick();
            return true;
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }

    // ---------- pages ----------
    private final class PageAdapter extends RecyclerView.Adapter<PageAdapter.VH> {
        int width;

        final class VH extends RecyclerView.ViewHolder {
            VH(View v) { super(v); }
        }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            PageImage iv = new PageImage(PdfActivity.this);
            iv.setBackgroundColor(Color.WHITE);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(UI.M, 100);
            lp.bottomMargin = UI.dp(6);
            iv.setLayoutParams(lp);
            return new VH(iv);
        }

        @Override public void onBindViewHolder(VH vh, int pos) {
            final ImageView iv = (ImageView) vh.itemView;
            final int position = pos;
            ((PageImage) iv).clearImage();
            iv.setTag(Integer.valueOf(position));
            int w = Math.max(width, 1);
            ViewGroup.LayoutParams lp = iv.getLayoutParams();
            lp.height = pw[position] > 0 && ph[position] > 0
                    ? (int) ((long) w * ph[position] / pw[position]) : (int) (w * 1.414f);
            iv.setLayoutParams(lp);
            final int target = Math.min(w, MAX_W);
            final String key = bitmapKey(position, target);
            Bitmap cached = bitmaps.get(key);
            if (cached != null) ((PageImage) iv).show(cached);
            else {
                FutureTask<Void> task = new FutureTask<Void>(() -> {
                    try { render(iv, position, target); }
                    finally { pending.remove(key); }
                    return null;
                });
                if (pending.putIfAbsent(key, task) == null) ex.execute(task);
            }
        }

        @Override public void onViewRecycled(VH vh) {
            ImageView iv = (ImageView) vh.itemView;
            Object bound = iv.getTag();
            if (bound instanceof Integer) {
                int pos = ((Integer) bound).intValue();
                int width = Math.min(Math.max(widthForPage(), 1), MAX_W);
                FutureTask<Void> task = pending.remove(bitmapKey(pos, width));
                if (task != null) {
                    task.cancel(true);
                    ex.remove(task);
                }
            }
            iv.setTag(null);
            ((PageImage) iv).clearImage();
        }

        private int widthForPage() { return Math.max(width, 1); }

        @Override public int getItemCount() { return count; }
    }

    private boolean stillBound(ImageView iv, int pos) {
        Object t = iv.getTag();
        return t != null && ((Integer) t).intValue() == pos;
    }

    private void render(final ImageView iv, final int pos, int w) {
        if (!stillBound(iv, pos)) return;
        final String key = bitmapKey(pos, w);
        Bitmap cached = bitmaps.get(key);
        if (cached != null) {
            final Bitmap hit = cached;
            h.post(new Runnable() { @Override public void run() { if (stillBound(iv, pos)) ((PageImage) iv).show(hit); } });
            return;
        }
        Bitmap made = null;
        int pageWidth = 0, pageHeight = 0;
        synchronized (lock) {
            if (closed) return;
            PdfRenderer.Page p = null;
            try {
                p = renderer.openPage(pos);
            pageWidth = Math.max(1, p.getWidth());
            pageHeight = Math.max(1, p.getHeight());
                int hgt = Math.max(1, (int) ((long) w * p.getHeight() / p.getWidth()));
                made = Bitmap.createBitmap(w, hgt, Bitmap.Config.ARGB_8888);
                made.eraseColor(Color.WHITE);
                p.render(made, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            } catch (Throwable e) {
                android.util.Log.e("PdfActivity", "Failed to render page " + pos, e);
                return;
            } finally {
                if (p != null) p.close();
            }
        }
        final Bitmap bmp = made;
        final int actualWidth = pageWidth, actualHeight = pageHeight;
        if (bmp == null || closed) return;
        bitmaps.put(key, bmp);
        h.post(new Runnable() {
            @Override public void run() {
                if (!stillBound(iv, pos)) return;
                pw[pos] = actualWidth;
                ph[pos] = actualHeight;
                ViewGroup.LayoutParams lp = iv.getLayoutParams();
                int pageWidth = Math.max(rv.getWidth(), 1);
                lp.height = (int) ((long) pageWidth * actualHeight / actualWidth);
                iv.setLayoutParams(lp);
                ((PageImage) iv).show(bmp);
            }
        });
    }

    private String bitmapKey(int page, int width) { return page + "@" + width; }

    private void scaleDocument(float factor, float focusX, float focusY) {
        float next = Math.max(1f, Math.min(5f, documentZoom * factor));
        float ratio = next / documentZoom;
        documentX = focusX - (focusX - documentX) * ratio;
        documentY = focusY - (focusY - documentY) * ratio;
        documentZoom = next;
        if (Math.abs(documentZoom - 1f) < 0.01f) {
            documentZoom = 1f;
            documentX = 0;
            documentY = 0;
        }
        applyDocumentTransform();
    }

    private void applyDocumentTransform() {
        rv.setPivotX(0);
        rv.setPivotY(0);
        rv.setScaleX(documentZoom);
        rv.setScaleY(documentZoom);
        rv.setTranslationX(documentX);
        rv.setTranslationY(documentY);
    }

    private static final class PageImage extends AppCompatImageView {
        PageImage(Context context) {
            super(context);
            setScaleType(ScaleType.FIT_XY);
        }

        void show(Bitmap value) { setImageBitmap(value); }

        void clearImage() { setImageDrawable(null); }
    }

    // ---------- lifecycle ----------
    @Override protected void onStart() {
        super.onStart();
        Pomo.visible++;
        Pomo.listeners.add(onState);
        h.post(tick);
    }

    @Override protected void onPause() {
        save();
        super.onPause();
    }

    @Override protected void onStop() {
        save();
        Pomo.visible--;
        Pomo.listeners.remove(onState);
        h.removeCallbacks(tick);
        super.onStop();
    }

    @Override protected void onDestroy() {
        closed = true;
        ex.shutdownNow();
        closeAll();
        super.onDestroy();
    }

    private void closeAll() {
        synchronized (lock) {
            try { if (renderer != null) renderer.close(); } catch (Exception ignored) { }
            try { if (pfd != null) pfd.close(); } catch (Exception ignored) { }
            renderer = null;
            pfd = null;
        }
    }
}
