package at.dmpc.clipqueue;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Save an existing local playlist video as an independent copy in Downloads/ClipQueue. */
public final class VideoExporter {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static final long MAX_BYTES = 2_000_000_000L;

    private VideoExporter() {}

    public static void confirmSave(Activity activity, Clip clip) {
        new AlertDialog.Builder(activity)
                .setTitle("Video herunterladen")
                .setMessage("Das Video ist bereits offline gespeichert. Möchtest du eine zusätzliche Datei unter Downloads/ClipQueue anlegen?")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Herunterladen", (dialog, which) -> save(activity, clip))
                .show();
    }

    private static void save(Activity activity, Clip clip) {
        if (!BUSY.compareAndSet(false, true)) {
            Toast.makeText(activity, "Ein Video wird bereits gespeichert", Toast.LENGTH_SHORT).show();
            return;
        }
        Context context = activity.getApplicationContext();
        Toast.makeText(context, "Video wird unter Downloads/ClipQueue gespeichert ...", Toast.LENGTH_SHORT).show();
        WORKER.execute(() -> {
            String result;
            try {
                Uri uri = Uri.parse(clip.uri);
                if (!"content".equalsIgnoreCase(uri.getScheme())) {
                    throw new IllegalArgumentException("Nur lokal gespeicherte Videodateien lassen sich exportieren");
                }
                copyToDownloads(context, uri, clip.title);
                result = "Video gespeichert: Downloads/ClipQueue";
            } catch (Exception error) {
                String message = error.getMessage();
                result = "Speichern fehlgeschlagen: " + (message == null ? "Datei nicht verfügbar" : message);
            } finally {
                BUSY.set(false);
            }
            String message = result;
            activity.runOnUiThread(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
        });
    }

    private static void copyToDownloads(Context context, Uri source, String title) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        String extension = UrlRules.extension(title == null ? "" : title, resolver.getType(source));
        String mime = extension.equals("webm") ? "video/webm" :
                extension.equals("mov") ? "video/quicktime" :
                extension.equals("m4v") ? "video/x-m4v" : "video/mp4";
        String name = title == null ? "Video" : title.replaceAll("(?i)\\.(mp4|webm|mov|m4v)$", "");
        name = name.replaceAll("[^\\p{L}\\p{N}._ -]", "_").trim();
        if (name.isEmpty()) name = "Video";
        if (name.length() > 65) name = name.substring(0, 65);
        String filename = "ClipQueue_" + System.currentTimeMillis() + "_" + name + "." + extension;

        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
        values.put(MediaStore.Downloads.MIME_TYPE, mime);
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ClipQueue");
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (target == null) throw new Exception("Kein Speicherziel verfügbar");
        boolean complete = false;
        try {
            long total = 0;
            try (InputStream in = resolver.openInputStream(source);
                 OutputStream out = resolver.openOutputStream(target, "w")) {
                if (in == null || out == null) throw new Exception("Dateizugriff verweigert");
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_BYTES) throw new Exception("Videodatei ist größer als 2 GB");
                    out.write(buffer, 0, count);
                }
                out.flush();
            }
            if (total == 0) throw new Exception("Videodatei ist leer");
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.Downloads.IS_PENDING, 0);
            if (resolver.update(target, ready, null, null) != 1) throw new Exception("Datei konnte nicht freigegeben werden");
            complete = true;
        } finally {
            if (!complete) resolver.delete(target, null, null);
        }
    }
}
