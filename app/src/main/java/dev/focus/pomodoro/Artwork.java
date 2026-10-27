package dev.focus.pomodoro;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.util.LruCache;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Asynchronous album art loader with a small process cache. */
final class Artwork {
    private static final ExecutorService worker = Executors.newFixedThreadPool(2);
    private static final LruCache<Long, Bitmap> cache = new LruCache<Long, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(Long key, Bitmap value) { return value.getByteCount(); }
    };

    static void load(final Context context, final long albumId, final ImageView target) {
        target.setTag(Long.valueOf(albumId));
        target.setImageResource(R.drawable.ic_music);
        if (albumId < 0) return;
        Bitmap hit = cache.get(albumId);
        if (hit != null) { target.setImageBitmap(hit); return; }
        worker.execute(new Runnable() {
            @Override public void run() {
                final Bitmap bitmap = read(context, albumId);
                if (bitmap == null) return;
                cache.put(albumId, bitmap);
                target.post(new Runnable() {
                    @Override public void run() {
                        Object tag = target.getTag();
                        if (tag instanceof Long && ((Long) tag).longValue() == albumId) target.setImageBitmap(bitmap);
                    }
                });
            }
        });
    }

    private static Bitmap read(Context context, long id) {
        Uri art = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), id);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(art)) {
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, bounds);
        } catch (Exception ignored) { return null; }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = Math.max(1, Math.min(bounds.outWidth, bounds.outHeight) / 160);
        try (InputStream in = context.getContentResolver().openInputStream(art)) {
            return in == null ? null : BitmapFactory.decodeStream(in, null, opts);
        } catch (Exception ignored) { return null; }
    }
}