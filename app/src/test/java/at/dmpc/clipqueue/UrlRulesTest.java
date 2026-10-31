package at.dmpc.clipqueue;

import org.junit.Test;
import static org.junit.Assert.*;

public class UrlRulesTest {
    @Test public void rejectPlatformPages() {
        assertTrue(UrlRules.isPlatformPage("https://www.youtube.com/shorts/abc"));
        assertTrue(UrlRules.isPlatformPage("https://www.instagram.com/reel/abc"));
        assertTrue(UrlRules.isPlatformPage("https://www.reelshort.com/episode/abc"));
        assertFalse(UrlRules.isPlatformPage("https://video.example.org/a.mp4"));
        assertFalse(UrlRules.isPlatformPage("https://fakeyoutube.com/a.mp4"));
    }
    @Test public void acceptOnlyHttps() {
        assertTrue(UrlRules.isHttpsUrl("https://video.example.org/video.mp4"));
        assertFalse(UrlRules.isHttpsUrl("http://video.example.org/video.mp4"));
        assertFalse(UrlRules.isHttpsUrl("file:///sdcard/video.mp4"));
        assertFalse(UrlRules.isHttpsUrl("https://name:secret@video.example.org/a.mp4"));
    }
    @Test public void acceptOnlyOfficialReelShortHost() {
        assertTrue(UrlRules.isReelShortLink("https://reelshort.com/show/123"));
        assertTrue(UrlRules.isReelShortLink("https://www.reelshort.com/series/123"));
        assertFalse(UrlRules.isReelShortLink("https://evilreelshort.com/show"));
        assertFalse(UrlRules.isReelShortLink("http://reelshort.com/show"));
        assertFalse(UrlRules.isReelShortLink("https://reelshort.com.evil.example/show"));
    }
    @Test public void rejectHtml() {
        assertFalse(UrlRules.isVideo("https://example.com/p", "text/html; charset=utf-8"));
        assertTrue(UrlRules.isVideo("https://example.com/test.mp4", "video/mp4"));
        assertTrue(UrlRules.isVideo("https://example.com/test.webm", "application/octet-stream"));
        assertFalse(UrlRules.isVideo("https://example.com/test.exe", "application/octet-stream"));
    }
}
