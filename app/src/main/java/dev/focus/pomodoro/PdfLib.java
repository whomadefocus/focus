package dev.focus.pomodoro;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** PDF library: a small JSON list in SharedPreferences (uri, name, last page, page count, last opened). */
final class PdfLib {
    static final class Doc {
        String uri, name;
        int page, count;
        long last;
        boolean pinned;
    }

    private static SharedPreferences sp;
    private static List<Doc> cache;

    static void init(Context c) {
        if (sp == null) sp = c.getApplicationContext().getSharedPreferences("pdfs", Context.MODE_PRIVATE);
    }

    static synchronized List<Doc> all() {
        if (cache == null) {
            cache = new ArrayList<>();
            try {
                JSONArray a = new JSONArray(sp.getString("list", "[]"));
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    Doc d = new Doc();
                    d.uri = o.getString("u");
                    d.name = o.optString("n", "PDF");
                    d.page = o.optInt("p", 0);
                    d.count = o.optInt("c", 0);
                    d.last = o.optLong("t", 0);
                    d.pinned = o.optBoolean("pin", false);
                    cache.add(d);
                }
            } catch (Exception ignored) { }
            sort();
        }
        return cache;
    }

    private static void save() {
        try {
            JSONArray a = new JSONArray();
            for (Doc d : all()) {
                a.put(new JSONObject().put("u", d.uri).put("n", d.name).put("p", d.page).put("c", d.count)
                    .put("t", d.last).put("pin", d.pinned));
            }
            sp.edit().putString("list", a.toString()).apply();
        } catch (Exception ignored) { }
    }

    static synchronized Doc find(String uri) {
        for (Doc d : all()) if (d.uri.equals(uri)) return d;
        return null;
    }

    static synchronized void add(String uri, String name) {
        if (find(uri) != null) return;
        Doc d = new Doc();
        d.uri = uri;
        d.name = name.replaceAll("(?i)\\.pdf$", "");
        all().add(d);
        sort();
        save();
    }

    static synchronized void remove(String uri) {
        Doc d = find(uri);
        if (d != null) { all().remove(d); save(); }
    }

    static synchronized void pin(String uri, boolean pinned) {
        Doc doc = find(uri);
        if (doc == null) return;
        doc.pinned = pinned;
        sort();
        save();
    }

    private static void sort() {
        Collections.sort(all(), new Comparator<Doc>() {
            @Override public int compare(Doc left, Doc right) {
                if (left.pinned != right.pinned) return left.pinned ? -1 : 1;
                return left.name.toLowerCase(Locale.ROOT).compareTo(right.name.toLowerCase(Locale.ROOT));
            }
        });
    }

    static synchronized void progress(String uri, int page, int count) {
        Doc d = find(uri);
        if (d == null) return;
        d.page = page;
        d.count = count;
        d.last = System.currentTimeMillis();
        save();
    }

    static synchronized Doc lastRead() {
        Doc best = null;
        for (Doc d : all()) if (d.last > 0 && (best == null || d.last > best.last)) best = d;
        return best;
    }

    static int pct(Doc d) {
        return d.count <= 0 ? 0 : Math.round(100f * (d.page + 1) / d.count);
    }

    static String status(Doc d) {
        return d.count <= 0 ? "Not opened yet" : "Page " + (d.page + 1) + " / " + d.count + " \u00B7 " + pct(d) + "%";
    }
}
