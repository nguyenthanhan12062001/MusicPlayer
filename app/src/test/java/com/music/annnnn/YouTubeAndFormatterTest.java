package com.music.annnnn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.music.annnnn.data.download.MultiSourceAnalyzer;
import com.music.annnnn.data.local.PreferencesManager;
import com.music.annnnn.data.network.YouTubeAnalyzer;
import com.music.annnnn.util.TimeFormatter;

import org.junit.Test;

public class YouTubeAndFormatterTest {

    @Test
    public void testExtractYouTubeVideoId() {
        assertEquals("dQw4w9WgXcQ", YouTubeAnalyzer.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertEquals("dQw4w9WgXcQ", YouTubeAnalyzer.extractVideoId("https://youtu.be/dQw4w9WgXcQ"));
        assertEquals("dQw4w9WgXcQ", YouTubeAnalyzer.extractVideoId("https://youtube.com/shorts/dQw4w9WgXcQ"));
        assertNull(YouTubeAnalyzer.extractVideoId("https://example.com/not-youtube"));
    }

    @Test
    public void testMultiSourceUrlValidationAndDetection() {
        assertTrue(MultiSourceAnalyzer.isValidHttpUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertTrue(MultiSourceAnalyzer.isValidHttpUrl("https://soundcloud.com/artist/track-name"));
        assertTrue(MultiSourceAnalyzer.isValidHttpUrl("http://vimeo.com/123456"));
        assertFalse(MultiSourceAnalyzer.isValidHttpUrl("not a url"));
        assertFalse(MultiSourceAnalyzer.isValidHttpUrl("ftp://example.com/file.mp3"));
        assertFalse(MultiSourceAnalyzer.isValidHttpUrl(""));

        assertEquals("YouTube", MultiSourceAnalyzer.detectSourceName("https://www.youtube.com/watch?v=abc"));
        assertEquals("YouTube Music", MultiSourceAnalyzer.detectSourceName("https://music.youtube.com/watch?v=abc"));
        assertEquals("SoundCloud", MultiSourceAnalyzer.detectSourceName("https://soundcloud.com/foo/bar"));
        assertEquals("TikTok", MultiSourceAnalyzer.detectSourceName("https://www.tiktok.com/@user/video/123"));
        assertEquals("Bandcamp", MultiSourceAnalyzer.detectSourceName("https://artist.bandcamp.com/track/song"));
    }

    @Test
    public void testPlaylistUrlDetection() {
        assertTrue(MultiSourceAnalyzer.looksLikePlaylistUrl("https://www.youtube.com/playlist?list=PL123456"));
        assertTrue(MultiSourceAnalyzer.looksLikePlaylistUrl("https://soundcloud.com/user/sets/chill-mix"));
        assertFalse(MultiSourceAnalyzer.looksLikePlaylistUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
    }

    @Test
    public void testTimeFormatter() {
        assertEquals("03:30", TimeFormatter.formatDuration(210_000L));
        assertEquals("00:05", TimeFormatter.formatDuration(5_000L));
        assertEquals("2 phút", TimeFormatter.formatListeningTime(125L));
    }

    @Test
    public void testLanguageTagNormalization() {
        assertEquals("", PreferencesManager.normalizeLanguageTag(null));
        assertEquals("", PreferencesManager.normalizeLanguageTag(""));
        assertEquals("", PreferencesManager.normalizeLanguageTag("   "));
        assertEquals("vi", PreferencesManager.normalizeLanguageTag("vi-VN"));
        assertEquals("en", PreferencesManager.normalizeLanguageTag("en-US"));
        assertEquals("id", PreferencesManager.normalizeLanguageTag("id"));
        assertEquals("id", PreferencesManager.normalizeLanguageTag("in"));
        assertEquals("id", PreferencesManager.normalizeLanguageTag("in_ID"));
        assertEquals("id", PreferencesManager.normalizeLanguageTag("id-ID"));
        assertEquals("ja", PreferencesManager.normalizeLanguageTag("ja-JP"));
        assertEquals("ko", PreferencesManager.normalizeLanguageTag("ko-KR"));
        assertEquals("zh-CN", PreferencesManager.normalizeLanguageTag("zh"));
        assertEquals("zh-CN", PreferencesManager.normalizeLanguageTag("zh_CN"));
        assertEquals("zh-CN", PreferencesManager.normalizeLanguageTag("zh-Hans-CN"));
        assertEquals("fr", PreferencesManager.normalizeLanguageTag("fr-FR"));
        assertEquals("es", PreferencesManager.normalizeLanguageTag("es-ES"));
    }
}
