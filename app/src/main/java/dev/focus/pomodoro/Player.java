package dev.focus.pomodoro;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Process-wide player: MediaPlayer for tracks, a looping generated AudioTrack for white noise. */
final class Player {
    static final class Track {
        final Uri uri;
        final String title, sub;
        final boolean noise;
        final long albumId;

        Track(Uri u, String t, String s, boolean n) { this(u, t, s, n, -1); }
        Track(Uri u, String t, String s, boolean n, long album) { uri = u; title = t; sub = s; noise = n; albumId = album; }

        String key() { return noise ? "noise" : String.valueOf(uri); }

        @Override public boolean equals(Object o) { return o instanceof Track && ((Track) o).key().equals(key()); }

        @Override public int hashCode() { return key().hashCode(); }
    }

    static final Track NOISE = new Track(null, "White noise", "Endless, for studying", true);

    private static Context app;
    private static SharedPreferences sp;
    static Track cur;
    static boolean playing;
    private static boolean prepared, resumeOnGain;
    private static MediaPlayer mp;
    private static AudioTrack noiseTrack;
    /** Index 0 is always the white-noise entry. */
    static List<Track> queue = new ArrayList<>();
    private static List<Track> scanCache;
    static final List<Runnable> listeners = new ArrayList<>();

    private static final AudioManager.OnAudioFocusChangeListener FOCUS = new AudioManager.OnAudioFocusChangeListener() {
        @Override public void onAudioFocusChange(int c) {
            if (c == AudioManager.AUDIOFOCUS_LOSS) {
                resumeOnGain = false;
                pause();
            } else if (c == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || c == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                if (playing) { pause(); resumeOnGain = true; }
            } else if (c == AudioManager.AUDIOFOCUS_GAIN && resumeOnGain) {
                resumeOnGain = false;
                resume();
            }
        }
    };

    static void init(Context c) {
        if (app != null) return;
        app = c.getApplicationContext();
        sp = app.getSharedPreferences("player", Context.MODE_PRIVATE);
        String k = sp.getString("key", null);
        if ("noise".equals(k)) cur = NOISE;
        else if (k != null) cur = new Track(Uri.parse(k), sp.getString("title", "?"), sp.getString("sub", ""), false);
    }

    static boolean active() { return mp != null || noiseTrack != null; }

    static boolean isCurrent(Track t) { return cur != null && cur.equals(t); }

    private static AudioManager audio() { return (AudioManager) app.getSystemService(Context.AUDIO_SERVICE); }

    @SuppressWarnings("deprecation")
    private static void focus() { audio().requestAudioFocus(FOCUS, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN); }

    static void play(Track t) {
        release();
        cur = t;
        playing = false;
        sp.edit().putString("key", t.key()).putString("title", t.title).putString("sub", t.sub).apply();
        focus();
        try {
            if (t.noise) {
                startNoise();
                playing = true;
            } else {
                mp = new MediaPlayer();
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                mp.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK);
                mp.setDataSource(app, t.uri);
                mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override public void onPrepared(MediaPlayer m) {
                        if (m != mp) return;
                        prepared = true;
                        if (playing) m.start();
                    }
                });
                mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override public void onCompletion(MediaPlayer m) { advance(); }
                });
                mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override public boolean onError(MediaPlayer m, int what, int extra) {
                        fail();
                        return true;
                    }
                });
                playing = true;
                mp.prepareAsync();
            }
        } catch (Exception e) {
            fail();
            return;
        }
        changed();
    }

    private static void fail() {
        release();
        playing = false;
        Toast.makeText(app, "Can't play this track", Toast.LENGTH_SHORT).show();
        changed();
    }

    private static void startNoise() {
        int rate = 22050, n = rate * 4;
        short[] buf = new short[n];
        Random r = new Random();
        for (int i = 0; i < n; i++) buf[i] = (short) ((r.nextInt(65536) - 32768) * 0.4f);
        AudioTrack t = new AudioTrack(AudioManager.STREAM_MUSIC, rate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, n * 2, AudioTrack.MODE_STATIC);
        t.write(buf, 0, n);
        t.setLoopPoints(0, n, -1);
        t.play();
        noiseTrack = t;
    }

    private static void release() {
        prepared = false;
        if (mp != null) {
            try { mp.reset(); mp.release(); } catch (Exception ignored) { }
            mp = null;
        }
        if (noiseTrack != null) {
            try { noiseTrack.stop(); } catch (Exception ignored) { }
            noiseTrack.release();
            noiseTrack = null;
        }
    }

    /** Play/pause. Returns false if there is nothing to play yet. */
    static boolean toggle() {
        if (cur == null) return false;
        if (!active()) { play(cur); return true; }
        if (playing) pause(); else resume();
        return true;
    }

    static void pause() {
        if (!playing) return;
        playing = false;
        try {
            if (noiseTrack != null) noiseTrack.pause();
            else if (mp != null && prepared) mp.pause();
        } catch (Exception ignored) { }
        changed();
    }

    static void resume() {
        if (playing || !active()) return;
        focus();
        playing = true;
        try {
            if (noiseTrack != null) noiseTrack.play();
            else if (mp != null && prepared) mp.start();
        } catch (Exception ignored) { }
        changed();
    }

    static void stop() {
        release();
        playing = false;
        changed();
    }

    static void next() { step(1); }

    static void prev() { step(-1); }

    private static void step(int dir) {
        int n = queue.size();
        if (n < 2) return;
        int i = queue.indexOf(cur);
        if (i < 0) i = 0;
        i += dir;
        if (i >= n) i = 1;
        if (i < 1) i = n - 1;
        play(queue.get(i));
    }

    private static void advance() {
        int n = queue.size();
        if (n < 2) { playing = false; changed(); return; }
        int i = queue.indexOf(cur);
        i = i < 0 ? 1 : i + 1;
        if (i >= n) i = 1;
        play(queue.get(i));
    }

    static void changed() {
        for (Runnable r : new ArrayList<>(listeners)) r.run();
        if (active()) MusicService.sync(app);
        else app.stopService(new Intent(app, MusicService.class));
    }

    /** Reads the device's music library (needs the audio permission). */
    static synchronized List<Track> scan(Context c) {
        if (scanCache != null) return new ArrayList<>(scanCache);
        ArrayList<Track> out = new ArrayList<>();
        String[] proj = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID};
        try (Cursor q = c.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
                MediaStore.Audio.Media.IS_MUSIC + "!=0", null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE")) {
            if (q != null) {
                while (q.moveToNext()) {
                    String artist = q.getString(2);
                    if (artist == null || artist.equals("<unknown>")) artist = "Unknown artist";
                    String sub = artist + " \u00B7 " + UI.mmss(q.getLong(3) - 999);
                    out.add(new Track(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, q.getLong(0)),
                            q.getString(1), sub, false, q.getLong(4)));
                }
            }
        } catch (Exception ignored) { }
        scanCache = new ArrayList<>(out);
        return new ArrayList<>(scanCache);
    }

    static synchronized void clearScanCache() { scanCache = null; }
}
