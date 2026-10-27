package dev.focus.pomodoro;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Persistent likes and named playlists for the local music library. */
final class MusicCollections {
    static final String LIKED_NAME = "Liked songs";
    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("music_collections", Context.MODE_PRIVATE);
    }

    static synchronized boolean liked(Context context, String trackKey) {
        return favorites(context).contains(trackKey);
    }

    static synchronized void toggleLike(Context context, String trackKey) {
        Set<String> liked = favorites(context);
        if (!liked.add(trackKey)) liked.remove(trackKey);
        JSONArray data = new JSONArray();
        for (String key : liked) data.put(key);
        prefs(context).edit().putString("liked", data.toString()).apply();
    }

    private static Set<String> favorites(Context context) {
        HashSet<String> result = new HashSet<>();
        try {
            JSONArray data = new JSONArray(prefs(context).getString("liked", "[]"));
            for (int i = 0; i < data.length(); i++) result.add(data.getString(i));
        } catch (Exception ignored) { }
        return result;
    }

    static synchronized List<String> playlistNames(Context context) {
        ArrayList<String> result = new ArrayList<>();
        result.add(LIKED_NAME);
        try {
            JSONArray data = new JSONArray(prefs(context).getString("playlists", "[]"));
            for (int i = 0; i < data.length(); i++) {
                String name = data.getJSONObject(i).optString("name", "");
                if (!LIKED_NAME.equalsIgnoreCase(name)) result.add(name);
            }
        } catch (Exception ignored) { }
        return result;
    }

    static synchronized void createPlaylist(Context context, String name) {
        String clean = name.trim();
        if (clean.isEmpty() || LIKED_NAME.equalsIgnoreCase(clean)) return;
        JSONArray data = playlists(context);
        for (int i = 0; i < data.length(); i++) {
            if (clean.equalsIgnoreCase(data.optJSONObject(i).optString("name"))) return;
        }
        try { data.put(new JSONObject().put("name", clean).put("tracks", new JSONArray())); }
        catch (Exception ignored) { }
        savePlaylists(context, data);
    }

    static synchronized void addToPlaylist(Context context, String name, String trackKey) {
        if (LIKED_NAME.equals(name)) { toggleLike(context, trackKey); return; }
        JSONArray data = playlists(context);
        for (int i = 0; i < data.length(); i++) {
            JSONObject playlist = data.optJSONObject(i);
            if (playlist == null || !name.equals(playlist.optString("name"))) continue;
            JSONArray tracks = playlist.optJSONArray("tracks");
            if (tracks == null) tracks = new JSONArray();
            for (int j = 0; j < tracks.length(); j++) if (trackKey.equals(tracks.optString(j))) return;
            tracks.put(trackKey);
            try { playlist.put("tracks", tracks); } catch (Exception ignored) { }
            savePlaylists(context, data);
            return;
        }
    }

    static synchronized List<String> trackKeys(Context context, String name) {
        if (LIKED_NAME.equals(name)) return new ArrayList<>(favorites(context));
        ArrayList<String> result = new ArrayList<>();
        JSONArray data = playlists(context);
        for (int i = 0; i < data.length(); i++) {
            JSONObject playlist = data.optJSONObject(i);
            if (playlist == null || !name.equals(playlist.optString("name"))) continue;
            JSONArray tracks = playlist.optJSONArray("tracks");
            if (tracks != null) for (int j = 0; j < tracks.length(); j++) result.add(tracks.optString(j));
            break;
        }
        return result;
    }

    static synchronized void deletePlaylist(Context context, String name) {
        if (LIKED_NAME.equals(name)) return;
        JSONArray original = playlists(context), filtered = new JSONArray();
        for (int i = 0; i < original.length(); i++) {
            JSONObject item = original.optJSONObject(i);
            if (item != null && !name.equals(item.optString("name"))) filtered.put(item);
        }
        savePlaylists(context, filtered);
    }

    private static JSONArray playlists(Context context) {
        try { return new JSONArray(prefs(context).getString("playlists", "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    private static void savePlaylists(Context context, JSONArray data) {
        prefs(context).edit().putString("playlists", data.toString()).apply();
    }
}
