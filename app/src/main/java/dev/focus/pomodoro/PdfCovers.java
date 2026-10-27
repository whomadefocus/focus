package dev.focus.pomodoro;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.widget.ImageView;
import android.util.LruCache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small first-page previews cached in memory while browsing the PDF library. */
final class PdfCovers {
    private static final ExecutorService worker = Executors.newFixedThreadPool(2);
    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    static void load(final Context context, final String uri, final ImageView target) {
        target.setTag(uri);
        target.setImageResource(R.drawable.ic_pdf);
        Bitmap hit = cache.get(uri);
        if (hit != null) { target.setImageBitmap(hit); return; }
        worker.execute(new Runnable() {
            @Override public void run() {
                Bitmap cover = render(context, uri);
                if (cover == null) return;
                cache.put(uri, cover);
                target.post(new Runnable() {
                    @Override public void run() {
                        if (uri.equals(target.getTag())) target.setImageBitmap(cover);
                    }
                });
            }
        });
    }

    private static Bitmap render(Context context, String uri) {
        ParcelFileDescriptor descriptor = null;
        PdfRenderer renderer = null;
        PdfRenderer.Page page = null;
        try {
            descriptor = context.getContentResolver().openFileDescriptor(android.net.Uri.parse(uri), "r");
            if (descriptor == null) return null;
            renderer = new PdfRenderer(descriptor);
            if (renderer.getPageCount() == 0) return null;
            page = renderer.openPage(0);
            int width = 240;
            int height = Math.max(1, width * page.getHeight() / page.getWidth());
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bitmap;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (page != null) page.close();
            if (renderer != null) renderer.close();
            if (descriptor != null) try { descriptor.close(); } catch (Exception ignored) { }
        }
    }
}