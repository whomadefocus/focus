package dev.focus.pomodoro;

import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Searchable music library with artist, album and track navigation. */
final class MusicLibraryPage extends LinearLayout implements MainActivity.Refresh {
    private static final int ARTISTS = 0, ALBUMS = 1, TRACKS = 2, PLAYLISTS = 3;

    private static final class Item {
        String title, subtitle, artistKey, albumKey;
        Player.Track sample, track;
        long albumId = -1;
        int count;
        String playlistName;
    }

    private final MainActivity a;
    private final ArrayList<Player.Track> library = new ArrayList<>();
    private final TextView permission, summary;
    private final EditText search;
    private final TabLayout tabs;
    private final Rows<Item> rows;
    private final RecyclerView list;
    private final AlphabetRail rail;
    private final Runnable playerChanged = new Runnable() { @Override public void run() { render(); } };
    private int mode = TRACKS;
    private int loadToken;
    private boolean changingTab;
    private String artistFilter, albumFilter, playlistOpen;

    MusicLibraryPage(MainActivity activity) {
        super(activity);
        a = activity;
        setOrientation(VERTICAL);
        setBackgroundColor(UI.col(a, R.color.bg));

        TextView rescan = UI.symbolButton(a, "refresh", UI.col(a, R.color.text), 48, 24);
        rescan.setContentDescription("Rescan music library");
        rescan.setOnClickListener(v -> { Player.clearScanCache(); load(); });
        TextView playlistAdd = UI.symbolButton(a, "playlist_add", UI.col(a, R.color.text), 48, 24);
        playlistAdd.setContentDescription("Create playlist");
        playlistAdd.setOnClickListener(v -> { selectMode(PLAYLISTS); createPlaylist(); });
        addView(UI.topBar(a, "Music", v -> navigateUp(), playlistAdd, rescan),
                new LinearLayout.LayoutParams(UI.M, UI.W));

        permission = UI.tv(a, "Allow music access to scan audio files", 14, UI.col(a, R.color.primary), true);
        permission.setPadding(UI.dp(16), UI.dp(12), UI.dp(16), UI.dp(12));
        permission.setOnClickListener(v -> a.askAudio());
        addView(permission, new LinearLayout.LayoutParams(UI.M, UI.W));

        tabs = new TabLayout(a);
        tabs.setTabMode(TabLayout.MODE_FIXED);
        tabs.setTabGravity(TabLayout.GRAVITY_FILL);
        tabs.setBackgroundColor(UI.col(a, R.color.surface));
        tabs.setSelectedTabIndicatorColor(UI.col(a, R.color.primary));
        tabs.setTabTextColors(UI.col(a, R.color.text2), UI.col(a, R.color.primary));
        tabs.addTab(tabs.newTab().setText("Artists"));
        tabs.addTab(tabs.newTab().setText("Albums"));
        tabs.addTab(tabs.newTab().setText("Tracks"));
        tabs.addTab(tabs.newTab().setText("Playlists"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                mode = tab.getPosition();
                if (!changingTab) { artistFilter = null; albumFilter = null; playlistOpen = null; }
                render();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
        addView(tabs, new LinearLayout.LayoutParams(UI.M, UI.W));

        search = new EditText(a);
        search.setSingleLine(true);
        search.setHint("Search artists, albums or tracks");
        search.setTextColor(UI.col(a, R.color.text));
        search.setHintTextColor(UI.col(a, R.color.text2));
        search.setPadding(UI.dp(16), 0, UI.dp(16), 0);
        search.setBackground(UI.rect(UI.col(a, R.color.surface), 8));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(UI.M, UI.dp(48));
        searchLp.setMargins(UI.dp(12), UI.dp(8), UI.dp(12), UI.dp(2));
        addView(search, searchLp);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        summary = UI.tv(a, "", 12, UI.col(a, R.color.text2), false);
        summary.setPadding(UI.dp(16), UI.dp(8), UI.dp(16), UI.dp(4));
        addView(summary, new LinearLayout.LayoutParams(UI.M, UI.W));

        list = new RecyclerView(a);
        list.setLayoutManager(new LinearLayoutManager(a));
        rows = new Rows<Item>() {
            @Override View make(Context context) { return makeRow(context); }
            @Override void bind(View view, Item item) { bindRow(view, item); }
        };
        list.setAdapter(rows);
        list.setPadding(0, 0, UI.dp(24), 0);
        list.setClipToPadding(false);
        FrameLayout listFrame = new FrameLayout(a);
        listFrame.addView(list, new FrameLayout.LayoutParams(UI.M, UI.M));
        rail = new AlphabetRail(a, list);
        listFrame.addView(rail, new FrameLayout.LayoutParams(UI.dp(22), UI.M, Gravity.END | Gravity.CENTER_VERTICAL));
        addView(listFrame, new LinearLayout.LayoutParams(UI.M, 0, 1f));

        LinearLayout.LayoutParams playerLp = new LinearLayout.LayoutParams(UI.M, UI.W);
        playerLp.setMargins(UI.dp(8), UI.dp(4), UI.dp(8), UI.dp(8));
        addView(new MiniPlayer(a, () -> { }), playerLp);

        changingTab = true;
        tabs.selectTab(tabs.getTabAt(TRACKS));
        changingTab = false;
        load();
    }

    private View makeRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UI.dp(14), UI.dp(9), UI.dp(12), UI.dp(9));
        row.setBackground(UI.ripple(null, false, 0));
        ImageView art = new ImageView(context);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackground(UI.rect(UI.col(context, R.color.divider), 6));
        row.addView(art, new LinearLayout.LayoutParams(UI.dp(50), UI.dp(50)));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        TextView title = UI.tv(context, "", 15, UI.col(context, R.color.text), true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView subtitle = UI.tv(context, "", 12, UI.col(context, R.color.text2), false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(title);
        labels.addView(subtitle);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, UI.W, 1f);
        labelsLp.leftMargin = UI.dp(12);
        row.addView(labels, labelsLp);
        MaterialIconView action = new MaterialIconView(context);
        action.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        row.addView(action, new LinearLayout.LayoutParams(UI.dp(36), UI.dp(40)));
        MaterialIconView like = new MaterialIconView(context);
        like.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        row.addView(like, new LinearLayout.LayoutParams(UI.dp(36), UI.dp(40)));
        row.setTag(new Object[]{art, title, subtitle, action, like});
        row.setLayoutParams(new RecyclerView.LayoutParams(UI.M, UI.W));
        return row;
    }

    private void bindRow(View view, final Item item) {
        Object[] holder = (Object[]) view.getTag();
        ImageView art = (ImageView) holder[0];
        ((TextView) holder[1]).setText(item.title);
        ((TextView) holder[2]).setText(item.subtitle);
        MaterialIconView action = (MaterialIconView) holder[3];
        MaterialIconView like = (MaterialIconView) holder[4];
        if (item.track == Player.NOISE) {
            art.setTag(null);
            art.setImageResource(R.drawable.ic_noise);
        } else if (item.sample != null) {
            Artwork.load(a, item.sample, art);
        } else {
            art.setTag(null);
            art.setImageResource(R.drawable.ic_music);
        }

        if (item.track != null) {
            boolean current = Player.isCurrent(item.track);
            action.setImageResource(current && Player.playing ? R.drawable.ic_pause : R.drawable.ic_play);
            action.setTint(UI.col(a, current ? R.color.primary : R.color.text2));
            like.setVisibility(View.VISIBLE);
            like.setSymbol(MusicCollections.liked(a, item.track.key()) ? "favorite" : "favorite_border");
            like.setTint(UI.col(a, MusicCollections.liked(a, item.track.key()) ? R.color.primary : R.color.text2));
            like.setContentDescription("Like track");
            like.setOnClickListener(v -> {
                MusicCollections.toggleLike(a, item.track.key());
                render();
            });
            view.setOnClickListener(v -> {
                if (Player.isCurrent(item.track) && Player.active()) Player.toggle();
                else Player.play(item.track);
            });
            view.setOnLongClickListener(v -> { addToPlaylist(item.track); return true; });
        } else {
            like.setVisibility(View.GONE);
            action.setSymbol(mode == PLAYLISTS ? "chevron_right" : "chevron_right");
            action.setTint(UI.col(a, R.color.text2));
            view.setOnClickListener(v -> {
                if (mode == ARTISTS) {
                    artistFilter = item.artistKey;
                    albumFilter = null;
                    selectMode(ALBUMS);
                } else if (mode == ALBUMS) {
                    artistFilter = item.artistKey;
                    albumFilter = item.albumKey;
                    selectMode(TRACKS);
                } else if (mode == PLAYLISTS) {
                    playlistOpen = item.playlistName;
                    render();
                }
            });
            view.setOnLongClickListener(v -> {
                if (mode != PLAYLISTS || MusicCollections.LIKED_NAME.equals(item.playlistName)) return true;
                new AlertDialog.Builder(a).setTitle("Delete playlist?").setMessage(item.playlistName)
                        .setNegativeButton("Cancel", null).setPositiveButton("Delete", (dialog, which) -> {
                            MusicCollections.deletePlaylist(a, item.playlistName);
                            if (item.playlistName.equals(playlistOpen)) playlistOpen = null;
                            render();
                        }).show();
                return true;
            });
        }
    }

    private void selectMode(int target) {
        mode = target;
        changingTab = true;
        tabs.selectTab(tabs.getTabAt(target));
        changingTab = false;
        render();
    }

    private void navigateUp() {
        if (mode == PLAYLISTS && playlistOpen != null) {
            playlistOpen = null;
            render();
            return;
        }
        if (mode == TRACKS && albumFilter != null) {
            albumFilter = null;
            selectMode(ALBUMS);
        } else if (mode == ALBUMS && artistFilter != null) {
            artistFilter = null;
            selectMode(ARTISTS);
        } else a.back();
    }

    private void load() {
        final int request = ++loadToken;
        boolean allowed = a.hasAudio();
        permission.setVisibility(allowed ? View.GONE : View.VISIBLE);
        if (!allowed) {
            library.clear();
            render();
            return;
        }
        new Thread(() -> {
            final List<Player.Track> found = Player.scan(a);
            uiPost(request, found);
        }).start();
    }

    private void uiPost(final int request, final List<Player.Track> found) {
        post(() -> {
            if (request != loadToken) return;
            library.clear();
            library.addAll(found);
            ArrayList<Player.Track> queue = new ArrayList<>();
            queue.add(Player.NOISE);
            queue.addAll(found);
            Player.queue = queue;
            render();
        });
    }

    private void render() {
        if (rows == null || summary == null) return;
        String query = search.getText().toString().trim().toLowerCase(Locale.getDefault());
        ArrayList<Item> visible = mode == ARTISTS ? artists(query) : mode == ALBUMS ? albums(query)
            : mode == PLAYLISTS && playlistOpen == null ? playlists(query)
            : tracks(query);
        Collections.sort(visible, (left, right) -> {
            if (left.track == Player.NOISE) return right.track == Player.NOISE ? 0 : -1;
            if (right.track == Player.NOISE) return 1;
            return left.title.compareToIgnoreCase(right.title);
        });
        rows.set(visible);
        ArrayList<String> labels = new ArrayList<>();
        for (Item item : visible) labels.add(item.title);
        rail.setLabels(labels);
        String title = mode == ARTISTS ? "artists" : mode == ALBUMS ? "albums"
            : mode == PLAYLISTS ? playlistOpen == null ? "playlists" : playlistOpen
            : "tracks";
        summary.setText(visible.size() + " " + title + (artistFilter != null && mode != ARTISTS ? " · " + artistFilter : ""));
    }

    private ArrayList<Item> artists(String query) {
        LinkedHashMap<String, ArrayList<Player.Track>> groups = new LinkedHashMap<>();
        for (Player.Track track : library) group(groups, key(track.artist)).add(track);
        ArrayList<Item> result = new ArrayList<>();
        for (Map.Entry<String, ArrayList<Player.Track>> group : groups.entrySet()) {
            Player.Track sample = group.getValue().get(0);
            if (!matches(query, sample.artist)) continue;
            Item item = new Item();
            item.title = sample.artist;
            item.subtitle = group.getValue().size() + (group.getValue().size() == 1 ? " track" : " tracks");
            item.artistKey = group.getKey();
            item.sample = sample;
            item.count = group.getValue().size();
            result.add(item);
        }
        return result;
    }

    private ArrayList<Item> albums(String query) {
        LinkedHashMap<String, ArrayList<Player.Track>> groups = new LinkedHashMap<>();
        for (Player.Track track : library) {
            if (artistFilter != null && !artistFilter.equals(key(track.artist))) continue;
            group(groups, albumKey(track)).add(track);
        }
        ArrayList<Item> result = new ArrayList<>();
        for (Map.Entry<String, ArrayList<Player.Track>> group : groups.entrySet()) {
            Player.Track sample = group.getValue().get(0);
            if (!matches(query, sample.album) && !matches(query, sample.artist)) continue;
            Item item = new Item();
            item.title = sample.album;
            item.subtitle = sample.artist + " · " + group.getValue().size()
                    + (group.getValue().size() == 1 ? " track" : " tracks");
            item.artistKey = key(sample.artist);
            item.albumKey = group.getKey();
            item.sample = sample;
            item.albumId = sample.albumId;
            result.add(item);
        }
        return result;
    }

    private ArrayList<Item> tracks(String query) {
        ArrayList<Item> result = new ArrayList<>();
        List<String> playlistTracks = playlistOpen == null ? Collections.<String>emptyList()
                : MusicCollections.trackKeys(a, playlistOpen);
        boolean noiseInPlaylist = playlistOpen != null && playlistTracks.contains(Player.NOISE.key());
        boolean showNoise = artistFilter == null && albumFilter == null
            && ((playlistOpen == null && mode == TRACKS) || noiseInPlaylist);
        if (showNoise && matches(query, Player.NOISE.title)) {
            result.add(trackItem(Player.NOISE));
        }
        for (Player.Track track : library) {
            if (artistFilter != null && !artistFilter.equals(key(track.artist))) continue;
            if (albumFilter != null && !albumFilter.equals(albumKey(track))) continue;
            if (playlistOpen != null && !playlistTracks.contains(track.key())) continue;
            if (!matches(query, track.title) && !matches(query, track.artist) && !matches(query, track.album)) continue;
            result.add(trackItem(track));
        }
        return result;
    }

    private ArrayList<Item> likedTracks(String query) {
        ArrayList<Item> result = new ArrayList<>();
        for (Player.Track track : library) {
            if (MusicCollections.liked(a, track.key()) && (matches(query, track.title)
                    || matches(query, track.artist) || matches(query, track.album))) result.add(trackItem(track));
        }
        if (MusicCollections.liked(a, Player.NOISE.key()) && matches(query, Player.NOISE.title)) result.add(trackItem(Player.NOISE));
        return result;
    }

    private Item trackItem(Player.Track track) {
        Item item = new Item();
        item.title = track.title;
        item.subtitle = track.noise ? track.sub : track.artist + " · " + track.album + " · "
                + track.sub.substring(track.sub.lastIndexOf('·') + 1).trim();
        item.track = track;
        item.sample = track;
        return item;
    }

    private ArrayList<Item> playlists(String query) {
        ArrayList<Item> result = new ArrayList<>();
        for (String name : MusicCollections.playlistNames(a)) {
            if (!matches(query, name)) continue;
            Item item = new Item();
            item.title = name;
            item.subtitle = MusicCollections.trackKeys(a, name).size() + " tracks";
            item.playlistName = name;
            result.add(item);
        }
        return result;
    }

    private void createPlaylist() {
        final EditText name = new EditText(a);
        name.setSingleLine(true);
        name.setHint("Playlist name");
        name.setTextColor(UI.col(a, R.color.text));
        name.setHintTextColor(UI.col(a, R.color.text2));
        new AlertDialog.Builder(a).setTitle("New playlist").setView(name)
                .setNegativeButton("Cancel", null).setPositiveButton("Create", (dialog, which) -> {
                    String value = name.getText().toString().trim();
                    if (value.isEmpty()) { a.toast("Enter a playlist name"); return; }
                    MusicCollections.createPlaylist(a, value);
                    playlistOpen = value;
                    render();
                }).show();
    }

    private void addToPlaylist(final Player.Track track) {
        final List<String> names = MusicCollections.playlistNames(a);
        if (names.isEmpty()) {
            new AlertDialog.Builder(a).setTitle("Create a playlist first")
                    .setPositiveButton("Create", (dialog, which) -> createPlaylist()).setNegativeButton("Cancel", null).show();
            return;
        }
        String[] options = new String[names.size() + 1];
        options[0] = "New playlist";
        for (int i = 0; i < names.size(); i++) options[i + 1] = names.get(i);
        new AlertDialog.Builder(a).setTitle("Add to playlist").setItems(options, (dialog, which) -> {
            if (which == 0) {
                final EditText input = new EditText(a);
                input.setSingleLine(true);
                input.setHint("Playlist name");
                input.setTextColor(UI.col(a, R.color.text));
                input.setHintTextColor(UI.col(a, R.color.text2));
                new AlertDialog.Builder(a).setTitle("New playlist").setView(input)
                        .setNegativeButton("Cancel", null).setPositiveButton("Create", (d, w) -> {
                            String value = input.getText().toString().trim();
                            if (value.isEmpty()) return;
                            MusicCollections.createPlaylist(a, value);
                            MusicCollections.addToPlaylist(a, value, track.key());
                            render();
                        }).show();
            } else {
                MusicCollections.addToPlaylist(a, names.get(which - 1), track.key());
                a.toast("Added to playlist");
            }
        }).show();
    }

    private static boolean matches(String query, String value) {
        return query.isEmpty() || (value != null && value.toLowerCase(Locale.getDefault()).contains(query));
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String albumKey(Player.Track track) {
        return track.albumId >= 0 ? "id:" + track.albumId : key(track.artist) + "|" + key(track.album);
    }

    private static ArrayList<Player.Track> group(Map<String, ArrayList<Player.Track>> groups, String key) {
        ArrayList<Player.Track> items = groups.get(key);
        if (items == null) {
            items = new ArrayList<>();
            groups.put(key, items);
        }
        return items;
    }

    @Override public void refresh() { load(); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!Player.listeners.contains(playerChanged)) Player.listeners.add(playerChanged);
    }

    @Override protected void onDetachedFromWindow() {
        Player.listeners.remove(playerChanged);
        super.onDetachedFromWindow();
    }
}
