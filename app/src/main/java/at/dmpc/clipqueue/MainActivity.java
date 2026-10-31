package at.dmpc.clipqueue;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Offline-first playlist manager. Downloads only direct, authorized video URLs. */
public class MainActivity extends Activity {
    private static final int PICK_VIDEO = 22;
    public static final String EXTRA_DOWNLOAD_DIRECT_VIDEO = "at.dmpc.clipqueue.DOWNLOAD_DIRECT_VIDEO";
    private static final int BG = 0xFF0A0F1C;
    private static final int PANEL = 0xFF151D2C;
    private static final int MINT = 0xFF70E7C6;
    private static final int MUTED = 0xFF9AABC2;
    private final ExecutorService downloader = Executors.newSingleThreadExecutor();
    private final AtomicBoolean downloading = new AtomicBoolean(false);
    private final ArrayList<Clip> clips = new ArrayList<>();
    private final ArrayList<ReelShortLinks.Link> links = new ArrayList<>();
    private final AtomicBoolean importing = new AtomicBoolean(false);
    private LinearLayout mainColumn;
    private EditText urlField;
    private TextView progressLabel;
    private ProgressBar downloadProgress;
    private Button downloadButton;
    private String draftUrl = "";
    private String status = "Bereit";
    private boolean showProgress = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        clips.addAll(ClipStore.load(this));
        links.addAll(ReelShortLinks.load(this));
        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null) draftUrl = findUrl(shared);
        }
        render();
        if (state == null && intent.hasExtra(EXTRA_DOWNLOAD_DIRECT_VIDEO)) {
            String direct = intent.getStringExtra(EXTRA_DOWNLOAD_DIRECT_VIDEO);
            if (direct != null) startDownload(direct.trim());
        }
        // A device rotation must not queue a second copy of the same shared file.
        if (state == null && Intent.ACTION_SEND.equals(intent.getAction()) && intent.getType() != null && intent.getType().startsWith("video/")) {
            importSharedVideo(intent);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.hasExtra(EXTRA_DOWNLOAD_DIRECT_VIDEO)) {
            String direct = intent.getStringExtra(EXTRA_DOWNLOAD_DIRECT_VIDEO);
            if (direct != null) {
                draftUrl = direct.trim();
                urlField = null;
                render();
                startDownload(draftUrl);
            }
            return;
        }
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null) {
                draftUrl = findUrl(shared);
                urlField = null; // Shared URL must not be overwritten by the previous draft.
                render();
            }
        } else if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.getType() != null && intent.getType().startsWith("video/")) {
            importSharedVideo(intent);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        // Reload persisted edits after return from player. Never lose an ongoing draft.
        ArrayList<Clip> fromDisk = ClipStore.load(this);
        if (fromDisk.size() != clips.size()) { clips.clear(); clips.addAll(fromDisk); render(); }
    }

    private void render() {
        if (urlField != null) draftUrl = urlField.getText().toString();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        mainColumn = new LinearLayout(this);
        mainColumn.setOrientation(LinearLayout.VERTICAL);
        mainColumn.setPadding(dp(18), dp(22), dp(18), dp(28));
        scroll.addView(mainColumn);
        setContentView(scroll);

        TextView brand = text("CLIPQUEUE", 14, MINT, true);
        brand.setLetterSpacing(0.19f);
        mainColumn.addView(brand);
        TextView heading = text("ReelShort sammeln.\nOffline ansehen.", 27, Color.WHITE, true);
        heading.setLineSpacing(dp(2), 1f);
        mainColumn.addView(heading);
        TextView description = text("Freigegebene Videodateien dauerhaft am Handy speichern und Folgen automatisch hintereinander abspielen.", 14, MUTED, false);
        description.setPadding(0, dp(7), 0, dp(24));
        mainColumn.addView(description);

        LinearLayout actions = panel();
        actions.addView(text("FOLGE AUF DEM HANDY SPEICHERN", 12, MINT, true));
        TextView hint = text("Bereits erlaubterweise erhaltene ReelShort-Videodateien auswählen. ClipQueue erstellt eine eigene Kopie in Filme/ClipQueue, auch wenn die Ursprungsdatei später gelöscht wird.", 14, MUTED, false);
        hint.setPadding(0, dp(10), 0, dp(14));
        actions.addView(hint);
        Button importButton = action("+ Videodateien dauerhaft speichern", true);
        importButton.setEnabled(!importing.get() && !downloading.get());
        importButton.setOnClickListener(v -> openPicker());
        actions.addView(importButton);
        mainColumn.addView(actions);

        LinearLayout urlPanel = panel();
        LinearLayout.LayoutParams urlParams = (LinearLayout.LayoutParams) urlPanel.getLayoutParams();
        urlParams.topMargin = dp(14);
        urlPanel.setLayoutParams(urlParams);
        urlPanel.addView(text("REELSHORT-LINK MERKEN", 12, MINT, true));
        TextView urlHint = text("Aus ReelShort über Teilen einen Serienlink hier einfügen. Das merkt die Serie, speichert aber NICHT das Video offline.", 13, MUTED, false);
        urlHint.setPadding(0, dp(10), 0, dp(12));
        urlPanel.addView(urlHint);
        urlField = new EditText(this);
        urlField.setTextColor(Color.WHITE);
        urlField.setHintTextColor(MUTED);
        urlField.setTextSize(14);
        urlField.setSingleLine(true);
        urlField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlField.setHint("https://www.reelshort.com/...");
        urlField.setText(draftUrl);
        urlField.setPadding(dp(14), dp(12), dp(14), dp(12));
        urlField.setBackground(round(0xFF222B3B, 12));
        urlPanel.addView(urlField);
        Button bookmarkButton = action("★ ReelShort-Serie merken (nur Link)", true);
        LinearLayout.LayoutParams bmp = new LinearLayout.LayoutParams(-1, dp(48));
        bmp.topMargin = dp(10);
        urlPanel.addView(bookmarkButton, bmp);
        bookmarkButton.setOnClickListener(v -> addReelShortLink(urlField.getText().toString().trim()));

        TextView onlineHint = text("Direkte freigegebene Video-URL (z. B. MP4): online ansehen und zum Herunterladen lange auf das Video drücken. ReelShort-Serienlinks sind keine Videodateien.", 12, MUTED, false);
        onlineHint.setPadding(0, dp(14), 0, dp(7));
        urlPanel.addView(onlineHint);
        Button onlinePreview = action("▶ Online-Videodatei ansehen", false);
        LinearLayout.LayoutParams onlineParams = new LinearLayout.LayoutParams(-1, dp(48));
        onlineParams.topMargin = dp(6);
        urlPanel.addView(onlinePreview, onlineParams);
        onlinePreview.setOnClickListener(v -> previewOnlineVideo(urlField.getText().toString().trim()));
        downloadButton = action("↓ Freigegebene Videodatei speichern", false);
        LinearLayout.LayoutParams dbp = new LinearLayout.LayoutParams(-1, dp(48));
        dbp.topMargin = dp(10);
        urlPanel.addView(downloadButton, dbp);
        downloadButton.setEnabled(!downloading.get() && !importing.get());
        downloadButton.setOnClickListener(v -> startDownload(urlField.getText().toString().trim()));
        progressLabel = text(status, 12, MUTED, false);
        progressLabel.setPadding(0, dp(10), 0, 0);
        urlPanel.addView(progressLabel);
        downloadProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        downloadProgress.setIndeterminate(true);
        downloadProgress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        urlPanel.addView(downloadProgress, new LinearLayout.LayoutParams(-1, dp(7)));
        mainColumn.addView(urlPanel);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(25);
        mainColumn.addView(header, hp);
        TextView listHeading = text("OFFLINE-PLAYLIST  ·  " + clips.size() + " VIDEOS", 13, MINT, true);
        header.addView(listHeading, new LinearLayout.LayoutParams(0, -2, 1));
        if (!clips.isEmpty()) {
            Button all = action("▶ Alle abspielen", true);
            all.setTextSize(13);
            all.setOnClickListener(v -> play(0));
            header.addView(all);
        }

        if (clips.isEmpty()) {
            TextView empty = text("Noch keine Videodateien gespeichert.\nReelShort-Links allein lassen sich hier nicht offline abspielen. Importiere erlaubte Videodateien, um sie dauerhaft zu sichern.", 15, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(18), dp(30), dp(18), dp(30));
            mainColumn.addView(empty);
        } else {
            for (int i = 0; i < clips.size(); i++) {
                final int index = i;
                Clip clip = clips.get(index);
                LinearLayout item = panel();
                LinearLayout.LayoutParams ip = (LinearLayout.LayoutParams) item.getLayoutParams();
                ip.topMargin = dp(10);
                item.setLayoutParams(ip);
                TextView title = text((i + 1) + "   " + clip.title, 15, Color.WHITE, true);
                title.setMaxLines(2);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                item.addView(title);
                TextView origin = text(clip.source, 12, MUTED, false);
                origin.setPadding(0, dp(3), 0, dp(7));
                origin.setText(clip.source + " · Lange drücken: Download");
                item.addView(origin);
                item.setContentDescription("Video " + clip.title + ". Lange drücken zum Herunterladen.");
                item.setOnLongClickListener(v -> {
                    VideoExporter.confirmSave(this, clip);
                    return true;
                });
                title.setOnLongClickListener(v -> {
                    VideoExporter.confirmSave(this, clip);
                    return true;
                });
                origin.setOnLongClickListener(v -> {
                    VideoExporter.confirmSave(this, clip);
                    return true;
                });
                LinearLayout controls = new LinearLayout(this);
                controls.setOrientation(LinearLayout.HORIZONTAL);
                Button play = small("▶", () -> play(index));
                Button up = small("↑", () -> move(index, -1));
                Button down = small("↓", () -> move(index, +1));
                Button remove = small("✕", () -> confirmRemove(index));
                up.setEnabled(index > 0);
                down.setEnabled(index < clips.size() - 1);
                for (Button button : new Button[]{play, up, down, remove}) {
                    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(40), 1);
                    bp.setMargins(dp(3), 0, dp(3), 0);
                    controls.addView(button, bp);
                }
                item.addView(controls);
                mainColumn.addView(item);
            }
        }
        if (!links.isEmpty()) {
            TextView linkHeading = text("GEMERKTE REELSHORT-SERIEN  ·  " + links.size(), 13, MINT, true);
            linkHeading.setPadding(0, dp(25), 0, dp(5));
            mainColumn.addView(linkHeading);
            for (int i = 0; i < links.size(); i++) {
                final int index = i;
                ReelShortLinks.Link link = links.get(i);
                LinearLayout card = panel();
                LinearLayout.LayoutParams cp = (LinearLayout.LayoutParams) card.getLayoutParams();
                cp.topMargin = dp(10);
                card.setLayoutParams(cp);
                card.addView(text(link.title, 15, Color.WHITE, true));
                card.addView(text("Link gespeichert · Nur online in ReelShort ansehen", 12, MUTED, false));
                LinearLayout controls = new LinearLayout(this);
                Button open = small("▶ ReelShort ansehen", () -> openReelShortLink(link.url));
                Button remove = small("✕ Entfernen", () -> removeReelShortLink(index));
                controls.addView(open, new LinearLayout.LayoutParams(0, dp(42), 2));
                controls.addView(remove, new LinearLayout.LayoutParams(0, dp(42), 1));
                card.addView(controls);
                mainColumn.addView(card);
            }
        }
        TextView foot = text("Die Offline-Videos bleiben in Filme/ClipQueue gespeichert, auch wenn du sie aus der Playlist entfernst. Gemerkte ReelShort-Links sind keine Videodateien.", 12, MUTED, false);
        foot.setPadding(0, dp(23), 0, dp(8));
        mainColumn.addView(foot);
    }

    private String findUrl(String input) {
        int p = input.indexOf("https://");
        if (p < 0) return input.trim();
        String s = input.substring(p).split("\\s", 2)[0];
        return s.replaceAll("[),.]+$", "");
    }

    private void openPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_VIDEO);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_VIDEO || resultCode != RESULT_OK || data == null) return;
        ArrayList<Uri> selected = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                Uri uri = data.getClipData().getItemAt(i).getUri();
                if (!selected.contains(uri)) selected.add(uri);
            }
        } else if (data.getData() != null) selected.add(data.getData());
        importVideoFiles(selected);
    }

    private void importSharedVideo(Intent incoming) {
        try {
            @SuppressWarnings("deprecation")
            Uri uri = incoming.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) {
                ArrayList<Uri> one = new ArrayList<>();
                one.add(uri);
                importVideoFiles(one);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Geteilte Videodatei konnte nicht geöffnet werden", Toast.LENGTH_LONG).show();
        }
    }

    /** Copy the bytes into our own MediaStore entries: importing never relies on temporary URI grants. */
    private void importVideoFiles(ArrayList<Uri> items) {
        if (items.isEmpty()) return;
        if (downloading.get() || !importing.compareAndSet(false, true)) {
            Toast.makeText(this, "Ein Speichervorgang läuft bereits", Toast.LENGTH_SHORT).show();
            return;
        }
        showProgress = true;
        status = "Videodateien werden dauerhaft gespeichert ...";
        render();
        downloader.execute(() -> {
            int successes = 0;
            int failures = 0;
            for (int i = 0; i < items.size(); i++) {
                Uri src = items.get(i);
                final int current = i + 1;
                runOnUiThread(() -> updateStatus("Speichere Folge " + current + " / " + items.size() + " ..."));
                try {
                    String sourceType = getContentResolver().getType(src);
                    String name = queryDisplayName(src);
                    String lowerName = name.toLowerCase(Locale.ROOT);
                    boolean knownExtension = lowerName.endsWith(".mp4") || lowerName.endsWith(".webm") ||
                            lowerName.endsWith(".mov") || lowerName.endsWith(".m4v");
                    if (sourceType != null && !sourceType.toLowerCase(Locale.ROOT).startsWith("video/") &&
                            !(sourceType.equalsIgnoreCase("application/octet-stream") && knownExtension)) {
                        throw new Exception("Keine Videodatei");
                    }
                    String suffix = UrlRules.extension(name, sourceType);
                    String safeName = "ReelShort_" + System.currentTimeMillis() + "_" + i + "." + suffix;
                    Uri savedUri = writeVideoToMovies(src, safeName, suffix);
                    Clip saved = new Clip(UUID.randomUUID().toString(), name, savedUri.toString(), "Offline gespeichert · Filme/ClipQueue");
                    runOnUiThread(() -> {
                        clips.add(saved);
                        if (!ClipStore.save(this, clips)) {
                            Toast.makeText(this, "Playlist-Speicherung fehlgeschlagen", Toast.LENGTH_LONG).show();
                        }
                    });
                    successes++;
                } catch (Exception e) {
                    failures++;
                }
            }
            final int ok = successes, failed = failures;
            runOnUiThread(() -> {
                importing.set(false);
                showProgress = false;
                status = ok + " Video(s) gespeichert" + (failed > 0 ? " · " + failed + " fehlgeschlagen" : "");
                render();
                Toast.makeText(this, status, failed > 0 ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
            });
        });
    }

    private Uri writeVideoToMovies(Uri source, String filename, String extension) throws Exception {
        ContentResolver resolver = getContentResolver();
        String mime = extension.equals("webm") ? "video/webm" : extension.equals("mov") ? "video/quicktime" :
                extension.equals("m4v") ? "video/x-m4v" : "video/mp4";
        ContentValues data = new ContentValues();
        data.put(MediaStore.Video.Media.DISPLAY_NAME, filename);
        data.put(MediaStore.Video.Media.MIME_TYPE, mime);
        data.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ClipQueue");
        data.put(MediaStore.Video.Media.IS_PENDING, 1);
        Uri target = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, data);
        if (target == null) throw new Exception("Kein Speicherplatz verfügbar");
        boolean completed = false;
        try {
            long total = 0;
            try (InputStream in = resolver.openInputStream(source); OutputStream out = resolver.openOutputStream(target, "w")) {
                if (in == null || out == null) throw new Exception("Dateizugriff fehlt");
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > 2_000_000_000L) throw new Exception("Datei überschreitet 2 GB");
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
            if (total == 0) throw new Exception("Videodatei ist leer");
            ContentValues done = new ContentValues();
            done.put(MediaStore.Video.Media.IS_PENDING, 0);
            if (resolver.update(target, done, null, null) != 1) throw new Exception("Datei nicht freigegeben");
            completed = true;
            return target;
        } finally {
            if (!completed) resolver.delete(target, null, null);
        }
    }

    private void addReelShortLink(String rawUrl) {
        if (!UrlRules.isReelShortLink(rawUrl)) {
            new AlertDialog.Builder(this).setTitle("Kein ReelShort-Serienlink")
                    .setMessage("Hier kannst du nur https-Links zu reelshort.com merken. Das speichert keine Videodatei für die Offline-Wiedergabe.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        for (ReelShortLinks.Link link : links) {
            if (link.url.equals(rawUrl)) {
                Toast.makeText(this, "Serie ist bereits gemerkt", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        EditText titleInput = new EditText(this);
        titleInput.setSingleLine(true);
        titleInput.setHint("Serienname");
        titleInput.setText("ReelShort-Serie " + (links.size() + 1));
        new AlertDialog.Builder(this).setTitle("ReelShort-Link merken")
                .setMessage("Nur der Link wird gespeichert. Eine Offline-Videodatei entsteht dadurch nicht.")
                .setView(titleInput)
                .setPositiveButton("Link merken", (dialog, which) -> {
                    String name = titleInput.getText().toString().trim();
                    links.add(new ReelShortLinks.Link(rawUrl, name.isEmpty() ? "ReelShort-Serie" : name));
                    if (!ReelShortLinks.save(this, links)) Toast.makeText(this, "Link konnte nicht gespeichert werden", Toast.LENGTH_LONG).show();
                    render();
                })
                .setNegativeButton("Abbrechen", null).show();
    }

    private void openReelShortLink(String url) {
        if (!UrlRules.isReelShortLink(url)) return;
        try {
            Intent view = new Intent(this, ReelShortWebActivity.class);
            view.putExtra(ReelShortWebActivity.EXTRA_URL, url);
            startActivity(view);
        } catch (Exception e) {
            Toast.makeText(this, "ReelShort-Link kann nicht geöffnet werden", Toast.LENGTH_LONG).show();
        }
    }

    private void removeReelShortLink(int index) {
        if (index < 0 || index >= links.size()) return;
        links.remove(index);
        if (!ReelShortLinks.save(this, links)) Toast.makeText(this, "Entfernen fehlgeschlagen", Toast.LENGTH_SHORT).show();
        render();
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isBlank()) return name;
            }
        } catch (Exception ignored) {}
        return "Video";
    }

    private void previewOnlineVideo(String rawUrl) {
        draftUrl = rawUrl;
        if (!UrlRules.isDirectVideoCandidate(rawUrl)) {
            new AlertDialog.Builder(this)
                    .setTitle("Kein direkter Video-Link")
                    .setMessage("ClipQueue kann nur direkte HTTPS-Videodateien ansehen. ReelShort-Serienlinks, Streams mit Kopierschutz und Web-Seiten lassen sich hier nicht als Videodatei öffnen.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        Intent preview = new Intent(this, OnlineVideoActivity.class);
        preview.putExtra(OnlineVideoActivity.EXTRA_URL, rawUrl);
        startActivity(preview);
    }

    private void startDownload(String rawUrl) {
        draftUrl = rawUrl;
        if (urlField != null && !rawUrl.equals(urlField.getText().toString().trim())) {
            urlField.setText(rawUrl);
        }
        if (UrlRules.isPlatformPage(rawUrl)) {
            new AlertDialog.Builder(this).setTitle("Keine herunterladbare Videodatei")
                .setMessage("Ein ReelShort-Serienlink ist keine Videodatei. Du kannst ihn oben merken, aber nicht offline herunterladen. Verwende ausschließlich offiziell freigegebene Downloads als Datei.")
                .setPositiveButton("Verstanden", null).show();
            return;
        }
        if (!UrlRules.isHttpsUrl(rawUrl)) {
            Toast.makeText(this, "Bitte einen gültigen HTTPS-Link zu einer Videodatei eingeben", Toast.LENGTH_LONG).show();
            return;
        }
        if (importing.get() || !downloading.compareAndSet(false, true)) return;
        showProgress = true;
        updateStatus("Download wird vorbereitet …");
        downloadProgress.setIndeterminate(true);
        downloadProgress.setVisibility(View.VISIBLE);
        downloadButton.setEnabled(false);
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(urlField.getWindowToken(), 0);

        downloader.execute(() -> {
            Uri createdUri = null;
            HttpURLConnection connection = null;
            try {
                String current = rawUrl;
                for (int redirects = 0; redirects <= 5; redirects++) {
                    connection = (HttpURLConnection) new URL(current).openConnection();
                    connection.setInstanceFollowRedirects(false);
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(120000);
                    connection.setRequestProperty("User-Agent", "ClipQueue/1.0 (Android)");
                    int statusCode = connection.getResponseCode();
                    if (statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308) {
                        String location = connection.getHeaderField("Location");
                        if (location == null) throw new Exception("Umleitung ohne Zieladresse");
                        String next = URI.create(current).resolve(location).toString();
                        connection.disconnect(); connection = null;
                        if (!UrlRules.isHttpsUrl(next) || UrlRules.isPlatformPage(next))
                            throw new Exception("Unsichere oder nicht unterstützte Umleitung");
                        current = next;
                        if (redirects == 5) throw new Exception("Zu viele Umleitungen");
                        continue;
                    }
                    if (statusCode != 200) throw new Exception("HTTP-Fehler " + statusCode);
                    String rawMime = connection.getContentType();
                    if (!UrlRules.isVideo(current, rawMime))
                        throw new Exception("Link liefert keine unterstützte Videodatei (" + rawMime + ")");
                    long contentLength = connection.getContentLengthLong();
                    if (contentLength > 2_000_000_000L) throw new Exception("Datei größer als 2 GB");
                    String ext = UrlRules.extension(current, rawMime);
                    String mime = ext.equals("webm") ? "video/webm" : ext.equals("mov") ? "video/quicktime" :
                            ext.equals("m4v") ? "video/x-m4v" : "video/mp4";
                    String fileName = "Clip_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date()) + "." + ext;
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Video.Media.MIME_TYPE, mime);
                    values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ClipQueue");
                    values.put(MediaStore.Video.Media.IS_PENDING, 1);
                    ContentResolver resolver = getContentResolver();
                    createdUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                    if (createdUri == null) throw new Exception("Videospeicher nicht verfügbar");
                    long done = 0L;
                    long lastUpdate = 0L;
                    try (InputStream input = connection.getInputStream(); OutputStream output = resolver.openOutputStream(createdUri, "w")) {
                        if (output == null) throw new Exception("Zieldatei kann nicht geöffnet werden");
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            done += read;
                            if (done > 2_000_000_000L) throw new Exception("Download überschreitet 2 GB");
                            output.write(buffer, 0, read);
                            long now = SystemClock.elapsedRealtime();
                            if (now - lastUpdate > 250) {
                                lastUpdate = now;
                                String progressText = contentLength > 0 ?
                                        Math.min(100, done * 100 / contentLength) + " % geladen" :
                                        String.format(Locale.GERMAN, "%.1f MB geladen", done / 1048576.0);
                                runOnUiThread(() -> updateStatus(progressText));
                            }
                        }
                        output.flush();
                    }
                    if (done == 0) throw new Exception("Videodatei ist leer");
                    values.clear();
                    values.put(MediaStore.Video.Media.IS_PENDING, 0);
                    if (resolver.update(createdUri, values, null, null) != 1) throw new Exception("Datei konnte nicht freigegeben werden");
                    Clip saved = new Clip(UUID.randomUUID().toString(), fileName, createdUri.toString(), "Heruntergeladen · Filme/ClipQueue");
                    runOnUiThread(() -> {
                        clips.add(saved);
                        if (!ClipStore.save(this, clips)) Toast.makeText(this, "Speichern der Playlist fehlgeschlagen", Toast.LENGTH_LONG).show();
                        finishDownload("Gespeichert in Filme/ClipQueue", true);
                    });
                    return;
                }
            } catch (Exception error) {
                final String message = error.getMessage() == null ? "Unbekannter Fehler" : error.getMessage();
                if (createdUri != null) {
                    try { getContentResolver().delete(createdUri, null, null); } catch (Exception ignored) {}
                }
                runOnUiThread(() -> finishDownload("Download fehlgeschlagen: " + message, false));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void finishDownload(String message, boolean success) {
        downloading.set(false);
        showProgress = false;
        status = message;
        render();
        Toast.makeText(this, message, success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
    }

    private void updateStatus(String text) {
        status = text;
        if (progressLabel != null) progressLabel.setText(text);
    }

    private void play(int index) {
        if (index >= clips.size()) return;
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_INDEX, index);
        startActivity(intent);
    }

    private void move(int index, int delta) {
        int target = index + delta;
        if (index < 0 || target < 0 || target >= clips.size()) return;
        Collections.swap(clips, index, target);
        if (!ClipStore.save(this, clips)) Toast.makeText(this, "Reihenfolge nicht gespeichert", Toast.LENGTH_SHORT).show();
        render();
    }

    private void confirmRemove(int index) {
        if (index < 0 || index >= clips.size()) return;
        new AlertDialog.Builder(this).setTitle("Aus Playlist entfernen?")
                .setMessage("Die Videodatei selbst wird nicht gelöscht.")
                .setPositiveButton("Entfernen", (dialog, which) -> {
                    clips.remove(index);
                    if (!ClipStore.save(this, clips)) Toast.makeText(this, "Änderung konnte nicht gespeichert werden", Toast.LENGTH_LONG).show();
                    render();
                }).setNegativeButton("Abbrechen", null).show();
    }

    private Button small(String title, Runnable action) {
        Button button = action(title, false);
        button.setTextSize(18);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private Button action(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(primary ? BG : Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextSize(14);
        button.setBackground(round(primary ? MINT : 0xFF273247, 12));
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        return button;
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        panel.setBackground(round(PANEL, 16));
        panel.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return panel;
    }

    private TextView text(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int dp) { return Math.round(dp * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        downloader.shutdown();
        super.onDestroy();
    }
}
