package dev.focus.pomodoro;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends Activity {
    interface Refresh { void refresh(); }

    static final int HOME = 0, NOTES = 1, MUSIC = 2, PDFS = 3, SETTINGS = 4, APP_DRAWER = 5, SCREEN_TIME = 6;
    private static final int RQ_PDF = 11, RQ_DIR = 12, RQ_AUDIO = 21, RQ_NOTIF = 22;
    private static final int LOCK_FLAGS = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;

    private FrameLayout root;
    private View page;
    private int cur = -1;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        UI.applyTheme(this);
        UI.init(this);
        Pomo.init(this);
        Player.init(this);
        PdfLib.init(this);
        root = new FrameLayout(this);
        setContentView(root);
        show(HOME);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, RQ_NOTIF);
        }
        handle(getIntent());
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handle(i);
    }

    /** The timer ended and we were launched on top of whatever was open: wake the screen and show the timer. */
    private void handle(Intent i) {
        if (i != null && i.getBooleanExtra("alert", false)) {
            getWindow().addFlags(LOCK_FLAGS);
            if (cur != HOME) show(HOME);
        }
    }

    void show(int id) {
        if (page != null) root.removeView(page);
        cur = id;
        switch (id) {
            case NOTES: page = new NotesTasksPage(this); break;
            case MUSIC: page = new MusicPage(this); break;
            case PDFS: page = new PdfPage(this); break;
            case SETTINGS: page = new SettingsPage(this); break;
            case APP_DRAWER: page = new AppDrawerPage(this); break;
            case SCREEN_TIME: page = new ScreenTimePage(this); break;
            default: page = new HomePage(this);
        }
        root.addView(page, new FrameLayout.LayoutParams(UI.M, UI.M));
    }

    void back() { show(HOME); }

    @Override public void onBackPressed() {
        if (cur != HOME) show(HOME); else super.onBackPressed();
    }

    @Override protected void onStart() {
        super.onStart();
        Pomo.visible++;
    }

    @Override protected void onResume() {
        super.onResume();
        if (page instanceof Refresh) ((Refresh) page).refresh();
    }

    @Override protected void onPause() {
        if (Notes.flusher != null) Notes.flusher.run();
        super.onPause();
    }

    @Override protected void onStop() {
        Pomo.visible--;
        getWindow().clearFlags(LOCK_FLAGS);
        super.onStop();
    }

    /** Any touch silences the "time is up" alert. */
    @Override public void onUserInteraction() {
        Pomo.dismissAlert();
    }

    // ---------- PDFs ----------
    void openPdf(String uri) {
        startActivity(new Intent(this, PdfActivity.class).putExtra("uri", uri));
    }

    void pickPdfs() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/pdf")
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(i, RQ_PDF); } catch (Exception e) { toast("No file picker found"); }
    }

    void pickFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(i, RQ_DIR); } catch (Exception e) { toast("No file picker found"); }
    }

    @Override protected void onActivityResult(int rq, int rs, Intent d) {
        super.onActivityResult(rq, rs, d);
        if (rs != RESULT_OK || d == null) return;
        if (rq == RQ_PDF) {
            ArrayList<Uri> l = new ArrayList<>();
            ClipData cd = d.getClipData();
            if (cd != null) for (int i = 0; i < cd.getItemCount(); i++) l.add(cd.getItemAt(i).getUri());
            else if (d.getData() != null) l.add(d.getData());
            for (Uri u : l) {
                try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
                PdfLib.add(u.toString(), nameOf(u));
            }
            refreshPage();
        } else if (rq == RQ_DIR && d.getData() != null) {
            final Uri tree = d.getData();
            try { getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
            toast("Scanning folder\u2026");
            new Thread(new Runnable() {
                @Override public void run() {
                    final ArrayList<String[]> found = new ArrayList<>();
                    scan(tree, DocumentsContract.getTreeDocumentId(tree), 0, found);
                    for (String[] f : found) PdfLib.add(f[0], f[1]);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            toast(found.size() + " PDF(s) found");
                            refreshPage();
                        }
                    });
                }
            }).start();
        }
    }

    private void scan(Uri tree, String docId, int depth, ArrayList<String[]> out) {
        Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        String[] proj = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME};
        try (Cursor q = getContentResolver().query(kids, proj, null, null, null)) {
            if (q == null) return;
            while (q.moveToNext()) {
                String id = q.getString(0), mime = q.getString(1), name = q.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if (depth < 8) scan(tree, id, depth + 1, out);
                } else if ("application/pdf".equals(mime) || (name != null && name.toLowerCase().endsWith(".pdf"))) {
                    out.add(new String[]{DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(), name == null ? "PDF" : name});
                }
            }
        } catch (Exception ignored) { }
    }

    private String nameOf(Uri u) {
        String n = null;
        try (Cursor q = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (q != null && q.moveToFirst()) n = q.getString(0);
        } catch (Exception ignored) { }
        return n != null ? n : "PDF";
    }

    private void refreshPage() {
        if (page instanceof Refresh) ((Refresh) page).refresh();
    }

    // ---------- permissions ----------
    private String audioPerm() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    boolean hasAudio() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(audioPerm()) == PackageManager.PERMISSION_GRANTED;
    }

    void askAudio() {
        if (Build.VERSION.SDK_INT >= 23) requestPermissions(new String[]{audioPerm()}, RQ_AUDIO);
    }

    @Override public void onRequestPermissionsResult(int rq, String[] perms, int[] res) {
        super.onRequestPermissionsResult(rq, perms, res);
        if (rq == RQ_AUDIO) refreshPage();
    }

    /** "Pull me out of other apps" works best with the "display over other apps" permission. */
    void ensureOverlay() {
        if (Pomo.canOverlay()) return;
        new AlertDialog.Builder(this)
                .setTitle("Allow display over other apps")
                .setMessage("So Focus can open itself on top of other apps when a session ends, allow \u201CDisplay over other apps\u201D for Focus on the next screen.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Open settings", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        try {
                            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
                        } catch (Exception ignored) { }
                    }
                }).show();
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
