package dev.focus.pomodoro;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
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

/** Minimal PDF reader: continuous vertical scroll, landscape by default, no selection. */
public class PdfActivity extends Activity {
    private static final int MAX_W = 1400;

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
    private boolean landscape = true;
    private int shownPhase = -1;
    private PageImage zoomImage;
    private float lastTouchX, lastTouchY;
    private final LruCache<String, Bitmap> bitmaps = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    private final ExecutorService ex = Executors.newSingleThreadExecutor();
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
        setContentView(root);

        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView r, int dx, int dy) { if (dy != 0) track(); }

            @Override public void onScrollStateChanged(RecyclerView r, int s) {
                if (s == RecyclerView.SCROLL_STATE_IDLE) save();
            }
        });
        final ScaleGestureDetector scaler = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                if (zoomImage == null) return false;
                zoomImage.scale(detector.getScaleFactor(), detector.getFocusX() - zoomImage.getLeft(),
                        detector.getFocusY() - zoomImage.getTop());
                return true;
            }
        });
        rv.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override public boolean onInterceptTouchEvent(RecyclerView view, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && zoomImage != null && zoomImage.zoom <= 1f) zoomImage = null;
                if (event.getPointerCount() > 1 || (zoomImage != null && zoomImage.zoom > 1f)) {
                    if (zoomImage == null || event.getActionMasked() == MotionEvent.ACTION_DOWN
                            || event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                        View child = view.findChildViewUnder(event.getX(), event.getY());
                        if (child instanceof PageImage) zoomImage = (PageImage) child;
                    }
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                    }
                    return zoomImage != null;
                }
                return false;
            }

            @Override public void onTouchEvent(RecyclerView view, MotionEvent event) {
                scaler.onTouchEvent(event);
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE && zoomImage != null
                        && !scaler.isInProgress() && zoomImage.zoom > 1f) {
                    zoomImage.pan(event.getX() - lastTouchX, event.getY() - lastTouchY);
                }
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                if ((event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
                        && zoomImage != null && zoomImage.zoom <= 1f) zoomImage = null;
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
            final int[] w = new int[n], hh = new int[n];
            for (int i = 0; i < n; i++) {
                PdfRenderer.Page p = renderer.openPage(i);
                w[i] = Math.max(1, p.getWidth());
                hh[i] = Math.max(1, p.getHeight());
                p.close();
            }
            runOnUiThread(new Runnable() { @Override public void run() { ready(n, w, hh); } });
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
    }

    private void save() {
        if (count > 0) PdfLib.progress(uri, page, count);
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
            lp.height = (int) ((long) w * ph[position] / pw[position]);
            iv.setLayoutParams(lp);
            final int target = Math.min(w, MAX_W);
            final String key = bitmapKey(position, target);
            Bitmap cached = bitmaps.get(key);
            if (cached != null) ((PageImage) iv).show(cached);
            else ex.execute(new Runnable() { @Override public void run() { render(iv, position, target); } });
        }

        @Override public void onViewRecycled(VH vh) {
            ImageView iv = (ImageView) vh.itemView;
            iv.setTag(null);
            ((PageImage) iv).clearImage();
        }

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
        synchronized (lock) {
            if (closed) return;
            PdfRenderer.Page p = null;
            try {
                p = renderer.openPage(pos);
                int hgt = Math.max(1, (int) ((long) w * p.getHeight() / p.getWidth()));
                made = Bitmap.createBitmap(w, hgt, Bitmap.Config.ARGB_8888);
                made.eraseColor(Color.WHITE);
                p.render(made, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            } catch (Throwable e) {
                return;
            } finally {
                if (p != null) p.close();
            }
        }
        final Bitmap bmp = made;
        if (bmp == null || closed) return;
        bitmaps.put(key, bmp);
        h.post(new Runnable() {
            @Override public void run() { if (stillBound(iv, pos)) ((PageImage) iv).show(bmp); }
        });
    }

    private String bitmapKey(int page, int width) { return page + "@" + width; }

    private static final class PageImage extends AppCompatImageView {
        final Matrix transform = new Matrix();
        Bitmap bitmap;
        float zoom = 1f;

        PageImage(Context context) {
            super(context);
            setScaleType(ScaleType.MATRIX);
        }

        void show(Bitmap value) {
            bitmap = value;
            setImageBitmap(value);
            fit();
        }

        void clearImage() {
            bitmap = null;
            zoom = 1f;
            setImageDrawable(null);
            transform.reset();
            setImageMatrix(transform);
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            fit();
        }

        private void fit() {
            if (bitmap == null || getWidth() <= 0 || getHeight() <= 0) return;
            float scale = Math.min((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
            float dx = (getWidth() - bitmap.getWidth() * scale) / 2f;
            float dy = (getHeight() - bitmap.getHeight() * scale) / 2f;
            transform.setScale(scale, scale);
            transform.postTranslate(dx, dy);
            zoom = 1f;
            setImageMatrix(transform);
        }

        void scale(float factor, float x, float y) {
            float next = Math.max(1f, Math.min(5f, zoom * factor));
            if (next == 1f) { fit(); return; }
            float actual = next / zoom;
            transform.postScale(actual, actual, x, y);
            zoom = next;
            constrain();
            setImageMatrix(transform);
        }

        void pan(float dx, float dy) {
            transform.postTranslate(dx, dy);
            constrain();
            setImageMatrix(transform);
        }

        private void constrain() {
            if (bitmap == null) return;
            RectF bounds = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
            transform.mapRect(bounds);
            float dx = bounds.width() <= getWidth() ? (getWidth() - bounds.width()) / 2f - bounds.left
                    : bounds.left > 0 ? -bounds.left : bounds.right < getWidth() ? getWidth() - bounds.right : 0;
            float dy = bounds.height() <= getHeight() ? (getHeight() - bounds.height()) / 2f - bounds.top
                    : bounds.top > 0 ? -bounds.top : bounds.bottom < getHeight() ? getHeight() - bounds.bottom : 0;
            transform.postTranslate(dx, dy);
        }
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
