package at.dmpc.clipqueue;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * Opens saved ReelShort page links in the official website.
 * Never extracts embedded streams or bypasses payment, DRM or site permissions.
 * A download is offered only when the site itself initiates a video-file download.
 */
public final class ReelShortWebActivity extends Activity {
    public static final String EXTRA_URL = "at.dmpc.clipqueue.REELSHORT_URL";
    private WebView browser;
    private String originalUrl;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        originalUrl = getIntent().getStringExtra(EXTRA_URL);
        if (originalUrl == null || !UrlRules.isReelShortLink(originalUrl)) {
            Toast.makeText(this, "Ungültiger ReelShort-Link", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        getWindow().setStatusBarColor(0xFF0A0F1C);
        getWindow().setNavigationBarColor(Color.BLACK);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0A0F1C);
        setContentView(root);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setPadding(dp(8), dp(6), dp(8), dp(6));
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("‹ Zurück");
        back.setOnClickListener(v -> onBackPressed());
        toolbar.addView(back, new LinearLayout.LayoutParams(0, dp(46), 1));

        Button official = button("↗ Offiziell öffnen");
        official.setOnClickListener(v -> openOfficialApp());
        toolbar.addView(official, new LinearLayout.LayoutParams(0, dp(46), 2));
        root.addView(toolbar);

        TextView explanation = new TextView(this);
        explanation.setText("ReelShort online ansehen. Nur wenn die Website selbst eine Videodatei zum Download anbietet, kann sie hier gespeichert werden.");
        explanation.setTextColor(0xFFCAD6E4);
        explanation.setTextSize(12);
        explanation.setPadding(dp(14), dp(6), dp(14), dp(10));
        root.addView(explanation);

        browser = new WebView(this);
        browser.setBackgroundColor(Color.BLACK);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        browser.setWebChromeClient(new WebChromeClient());
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri destination = request.getUrl();
                String link = destination.toString();
                if (UrlRules.isReelShortLink(link)) return false;
                // Other websites and payment/sign-in flows open in the system browser.
                if ("https".equalsIgnoreCase(destination.getScheme())) {
                    openExternal(destination);
                }
                return true;
            }
        });
        browser.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (!UrlRules.isHttpsUrl(url) || !isDownloadableVideo(url, mimeType)) {
                new AlertDialog.Builder(this)
                        .setTitle("Kein freigegebener Videodownload")
                        .setMessage("Die Website hat keine direkt speicherbare Videodatei angeboten. Ein Serienlink oder geschützter Stream lässt sich nicht als MP4 herunterladen.")
                        .setPositiveButton("OK", null).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Videodatei offline speichern?")
                    .setMessage("Die Website hat einen Videodownload angeboten. Die Datei wird unter Downloads/ClipQueue gespeichert und kann anschließend über „Videodateien dauerhaft speichern“ importiert werden.")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Speichern", (dialog, which) -> saveOfficialDownload(url, userAgent, mimeType))
                    .show();
        });
        root.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));
        if (state == null) browser.loadUrl(originalUrl);
        else browser.restoreState(state);
    }

    static boolean isDownloadableVideo(String url, String mime) {
        if (!UrlRules.isHttpsUrl(url)) return false;
        String type = mime == null ? "" : mime.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        return (type.equals("video/mp4") || type.equals("video/webm") ||
                type.equals("video/quicktime") || type.equals("video/x-m4v") ||
                (type.equals("application/octet-stream") && UrlRules.isVideo(url, type)));
    }

    private void saveOfficialDownload(String url, String userAgent, String mime) {
        if (!isDownloadableVideo(url, mime)) return;
        try {
            String ext = UrlRules.extension(url, mime);
            String name = "ReelShort-" + System.currentTimeMillis() + "." + ext;
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(name);
            request.setDescription("Von der Website freigegebener Videodownload");
            request.setMimeType(mime == null ? "video/mp4" : mime);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "ClipQueue/" + name);
            if (userAgent != null && !userAgent.isBlank()) request.addRequestHeader("User-Agent", userAgent);
            // Reuse the user's normal browser session if the site requires authentication.
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.isBlank()) request.addRequestHeader("Cookie", cookies);
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("DownloadManager nicht verfügbar");
            manager.enqueue(request);
            Toast.makeText(this, "Download gestartet. Danach Datei in ClipQueue importieren.", Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            Toast.makeText(this, "Download konnte nicht gestartet werden: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openOfficialApp() {
        openExternal(Uri.parse(originalUrl));
    }

    private void openExternal(Uri uri) {
        try {
            Intent view = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(view);
        } catch (Exception error) {
            Toast.makeText(this, "ReelShort konnte nicht extern geöffnet werden", Toast.LENGTH_LONG).show();
        }
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public void onBackPressed() {
        if (browser != null && browser.canGoBack()) browser.goBack();
        else finish();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (browser != null) browser.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        if (browser != null) {
            browser.stopLoading();
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }
}
