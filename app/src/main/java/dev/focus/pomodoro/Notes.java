package dev.focus.pomodoro;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** One plain-text file per day: files/notes/yyyy-MM-dd.txt */
final class Notes {
    /** Set by the notes page while it is open. */
    static Runnable flusher, changed;

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "notes");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    static String read(Context c, String day) {
        File f = new File(dir(c), day + ".txt");
        if (!f.exists()) return "";
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            return o.toString("UTF-8");
        } catch (IOException e) {
            return "";
        }
    }

    static void write(Context c, String day, String text) {
        File f = new File(dir(c), day + ".txt");
        if (text.trim().isEmpty()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            return;
        }
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes("UTF-8"));
        } catch (IOException ignored) {
        }
    }

    /** Newest first; today is always present. */
    static List<String> days(Context c) {
        ArrayList<String> l = new ArrayList<>();
        String[] names = dir(c).list();
        if (names != null) {
            for (String n : names) if (n.endsWith(".txt")) l.add(n.substring(0, n.length() - 4));
        }
        String t = today();
        if (!l.contains(t)) l.add(t);
        Collections.sort(l, Collections.reverseOrder());
        return l;
    }

    /** Called when a focus session completes: logs it into today's note. */
    static void appendPomodoro(Context c, int minutes) {
        if (flusher != null) flusher.run();
        String d = today();
        String t = read(c, d);
        String time = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        String line = "\uD83C\uDF45 " + time + " \u00B7 " + minutes + " min focus";
        if (t.length() > 0 && !t.endsWith("\n")) t += "\n";
        write(c, d, t + line + "\n");
        if (changed != null) changed.run();
    }
}
