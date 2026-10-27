package dev.focus.pomodoro;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.media.MediaMetadataRetriever;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.util.LruCache;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Asynchronous album art loader with a small process cache. */
final class Artwork {
    private static final ExecutorService worker = Executors.newFixedThreadPool(2);
    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<WeakReference<ImageView>>> waiting = new ConcurrentHashMap<>();
    private static final Set<String> loading = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    static void load(final Context context, final long albumId, final ImageView target) {
        load(context, albumId, null, target);
    }

    static void load(final Context context, final Player.Track track, final ImageView target) {
        load(context, track.albumId, track.uri, target);
    }

    private static void load(final Context context, final long albumId, final Uri trackUri, final ImageView target) {
        final String key = albumId >= 0 ? "album:" + albumId : "track:" + trackUri;
        target.setTag(key);
        target.setImageResource(R.drawable.ic_music);
        if (albumId < 0 && trackUri == null) return;
        Bitmap hit = cache.get(key);
        if (hit != null) { target.setImageBitmap(hit); return; }
        CopyOnWriteArrayList<WeakReference<ImageView>> targets = waiting.get(key);
        if (targets == null) {
            CopyOnWriteArrayList<WeakReference<ImageView>> created = new CopyOnWriteArrayList<>();
            CopyOnWriteArrayList<WeakReference<ImageView>> previous = waiting.putIfAbsent(key, created);
            targets = previous == null ? created : previous;
        }
        targets.add(new WeakReference<>(target));
        if (!loading.add(key)) return;
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    final Bitmap bitmap = read(context, albumId, trackUri);
                    CopyOnWriteArrayList<WeakReference<ImageView>> targets = waiting.remove(key);
                    if (bitmap == null) return;
                    cache.put(key, bitmap);
                    if (targets == null) return;
                    for (final WeakReference<ImageView> reference : targets) {
                        final ImageView view = reference.get();
                        if (view == null) continue;
                        view.post(new Runnable() {
                            @Override public void run() {
                                if (key.equals(view.getTag())) view.setImageBitmap(bitmap);
                            }
                        });
                    }
                } finally {
                    loading.remove(key);
                }
            }
        });
    }

    private static Bitmap read(Context context, long id, Uri trackUri) {
        Bitmap bitmap = id < 0 ? null : readAlbumArt(context, id);
        if (bitmap != null || trackUri == null) return bitmap;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, trackUri);
            byte[] embedded = retriever.getEmbeddedPicture();
            if (embedded == null) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(embedded, 0, embedded.length, bounds);
            BitmapFactory.Options options = sample(bounds);
            return BitmapFactory.decodeByteArray(embedded, 0, embedded.length, options);
        } catch (Exception ignored) {
            return null;
        } finally {
            try { retriever.release(); } catch (Exception ignored) { }
        }
    }

    private static Bitmap readAlbumArt(Context context, long id) {
        Uri art = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), id);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(art)) {
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, bounds);
        } catch (Exception ignored) { return null; }
        BitmapFactory.Options opts = sample(bounds);
        try (InputStream in = context.getContentResolver().openInputStream(art)) {
            return in == null ? null : BitmapFactory.decodeStream(in, null, opts);
        } catch (Exception ignored) { return null; }
    }

    private static BitmapFactory.Options sample(BitmapFactory.Options bounds) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, Math.max(bounds.outWidth, bounds.outHeight) / 180);
        return options;
    }
}