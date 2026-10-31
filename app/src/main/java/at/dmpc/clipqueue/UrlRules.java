package at.dmpc.clipqueue;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Strict input checks: no platform scraping and no cleartext download URLs. */
public final class UrlRules {
    private static final Set<String> PLATFORM_HOSTS = Set.of(
        "youtube.com", "youtu.be", "instagram.com", "tiktok.com", "facebook.com",
        "fb.watch", "threads.net", "m.youtube.com", "music.youtube.com", "reelshort.com"
    );

    private UrlRules() {}

    public static boolean isHttpsUrl(String url) {
        try {
            URI uri = URI.create(url.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null &&
                    !uri.getHost().isBlank() && uri.getUserInfo() == null;
        } catch (Exception e) { return false; }
    }

    public static boolean isPlatformPage(String url) {
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            for (String p : PLATFORM_HOSTS) {
                if (host.equals(p) || host.endsWith("." + p)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /** Verify real ReelShort domains; reject lookalike domains such as evilreelshort.com. */
    public static boolean isReelShortLink(String url) {
        try {
            URI parsed = URI.create(url.trim());
            String host = parsed.getHost();
            if (!"https".equalsIgnoreCase(parsed.getScheme()) || host == null || parsed.getUserInfo() != null) return false;
            host = host.toLowerCase(Locale.ROOT);
            return host.equals("reelshort.com") || host.endsWith(".reelshort.com");
        } catch (Exception e) { return false; }
    }

    public static String extension(String url, String mime) {
        String type = mime == null ? "" : mime.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        if (type.equals("video/webm")) return "webm";
        if (type.equals("video/quicktime")) return "mov";
        if (type.equals("video/x-m4v")) return "m4v";
        try {
            String path = URI.create(url).getPath().toLowerCase(Locale.ROOT);
            if (path.endsWith(".webm")) return "webm";
            if (path.endsWith(".mov")) return "mov";
            if (path.endsWith(".m4v")) return "m4v";
        } catch (Exception ignored) {}
        return "mp4";
    }

    public static boolean isVideo(String url, String mime) {
        String type = mime == null ? "" : mime.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        if (type.startsWith("video/")) return true;
        if (!type.equals("application/octet-stream")) return false;
        try {
            String path = URI.create(url).getPath().toLowerCase(Locale.ROOT);
            return path.endsWith(".mp4") || path.endsWith(".webm") ||
                    path.endsWith(".mov") || path.endsWith(".m4v");
        } catch (Exception e) { return false; }
    }
}
