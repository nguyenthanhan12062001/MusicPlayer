package com.music.annnnn.data.download;

import android.content.Context;
import android.text.TextUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.music.annnnn.util.TimeFormatter;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;
import com.yausername.youtubedl_android.mapper.VideoFormat;
import com.yausername.youtubedl_android.mapper.VideoInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Multi-source media analyzer powered by yt-dlp (youtubedl-android).
 * Supports YouTube, YouTube Music, SoundCloud, TikTok, Facebook, Instagram, Vimeo, Bandcamp, playlists, and more.
 */
public class MultiSourceAnalyzer {

    public static class MediaAnalysisResult {
        public String originalUrl;
        public String mediaId;
        public String sourceName;
        public String title;
        public String channelName;
        public String thumbnailUrl;
        public String durationText;
        public long durationMs;
        public boolean isPlaylist;
        public int playlistCount;
        public boolean hasVideoStreams;
        public long approxSizeBytes;
        public String bestAudioUrl;
        public String bestVideoUrl;
        public final List<String> availableFormats = new ArrayList<>();
        public final List<String> audioQualities = new ArrayList<>();
        public final List<String> videoQualities = new ArrayList<>();
    }

    public interface AnalyzeCallback {
        void onSuccess(MediaAnalysisResult result);

        void onError(String message);
    }

    private static final Pattern HTTP_URL_PATTERN = Pattern.compile(
            "^https?://[A-Za-z0-9.-]+(?:\\.[A-Za-z]{2,}|:[0-9]+)(?:/.*)?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final ExecutorService ANALYZE_EXECUTOR = Executors.newFixedThreadPool(2);

    public static boolean isValidHttpUrl(String rawUrl) {
        if (rawUrl == null) return false;
        String trimmed = rawUrl.trim();
        return !trimmed.isEmpty() && HTTP_URL_PATTERN.matcher(trimmed).matches();
    }

    public static boolean looksLikePlaylistUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return (lower.contains("list=") && !lower.contains("watch?v=") )
                || lower.contains("/playlist?")
                || lower.contains("/sets/")
                || lower.contains("/album/");
    }

    public static String detectSourceName(String urlOrExtractor) {
        if (urlOrExtractor == null) return "Web Media";
        String lower = urlOrExtractor.toLowerCase(Locale.ROOT);
        if (lower.contains("music.youtube")) return "YouTube Music";
        if (lower.contains("youtube") || lower.contains("youtu.be")) return "YouTube";
        if (lower.contains("soundcloud")) return "SoundCloud";
        if (lower.contains("tiktok")) return "TikTok";
        if (lower.contains("facebook") || lower.contains("fb.watch")) return "Facebook";
        if (lower.contains("instagram")) return "Instagram";
        if (lower.contains("vimeo")) return "Vimeo";
        if (lower.contains("bandcamp")) return "Bandcamp";
        if (lower.contains("twitch")) return "Twitch";
        if (lower.contains("dailymotion")) return "Dailymotion";
        if (lower.contains("bilibili")) return "Bilibili";
        if (!lower.startsWith("http") && !lower.trim().isEmpty()) {
            String cleaned = urlOrExtractor.replace(":playlist", "").replace(":tab", "").trim();
            if (!cleaned.isEmpty()) {
                return cleaned.substring(0, 1).toUpperCase(Locale.ROOT) + cleaned.substring(1);
            }
        }
        return "Web Media";
    }

    public void analyze(Context context, String rawUrl, AnalyzeCallback callback) {
        final Context appContext = context.getApplicationContext();
        final String trimmedUrl = rawUrl != null ? rawUrl.trim() : "";

        if (!isValidHttpUrl(trimmedUrl)) {
            callback.onError("Đường dẫn không hợp lệ. Vui lòng nhập URL bắt đầu bằng http:// hoặc https://");
            return;
        }

        ANALYZE_EXECUTOR.execute(() -> {
            if (!MediaEngineInitializer.ensureInitialized(appContext)) {
                String err = MediaEngineInitializer.getInitError();
                callback.onError(!TextUtils.isEmpty(err)
                        ? err
                        : "Chưa khởi tạo được trình phân tích yt-dlp.");
                return;
            }

            try {
                // Check playlist metadata first if URL looks like a playlist or contains list=
                if (looksLikePlaylistUrl(trimmedUrl) || trimmedUrl.toLowerCase(Locale.ROOT).contains("list=")) {
                    MediaAnalysisResult playlistResult = tryAnalyzePlaylist(trimmedUrl);
                    if (playlistResult != null) {
                        callback.onSuccess(playlistResult);
                        return;
                    }
                }

                VideoInfo info = YoutubeDL.getInstance().getInfo(trimmedUrl);
                if (info == null) {
                    callback.onError("Không lấy được thông tin media từ liên kết này.");
                    return;
                }

                MediaAnalysisResult result = mapVideoInfo(trimmedUrl, info);
                callback.onSuccess(result);
            } catch (Exception e) {
                callback.onError(mapFriendlyAnalyzeError(e));
            }
        });
    }

    private MediaAnalysisResult tryAnalyzePlaylist(String url) {
        try {
            YoutubeDLRequest req = new YoutubeDLRequest(url);
            req.addOption("--flat-playlist");
            req.addOption("--dump-single-json");
            req.addOption("--no-warnings");
            req.addOption("--skip-download");
            req.addOption("--force-ipv4");
            req.addOption("--no-check-certificates");
            req.addOption("--no-cache-dir");

            YoutubeDLResponse response = YoutubeDL.getInstance().execute(req, null, null);
            String out = response != null ? response.getOut() : null;
            if (TextUtils.isEmpty(out)) return null;

            JsonObject root = JsonParser.parseString(out).getAsJsonObject();
            if (!root.has("entries") || !root.get("entries").isJsonArray()) {
                return null;
            }
            JsonArray entries = root.getAsJsonArray("entries");
            if (entries.size() <= 1) {
                return null;
            }

            MediaAnalysisResult res = new MediaAnalysisResult();
            res.originalUrl = url;
            res.isPlaylist = true;
            res.playlistCount = entries.size();
            res.mediaId = getJsonString(root, "id", String.valueOf(Math.abs(url.hashCode())));
            String extractor = getJsonString(root, "extractor", "");
            res.sourceName = !TextUtils.isEmpty(extractor) ? detectSourceName(extractor) : detectSourceName(url);
            res.title = getJsonString(root, "title", "Danh sách phát (" + entries.size() + " mục)");
            res.channelName = getJsonString(root, "uploader", getJsonString(root, "channel", res.sourceName));

            // Try to extract thumbnail & total duration from entries
            long totalDurationSec = 0;
            String firstThumb = getJsonString(root, "thumbnail", null);
            for (JsonElement el : entries) {
                if (!el.isJsonObject()) continue;
                JsonObject item = el.getAsJsonObject();
                if (item.has("duration") && !item.get("duration").isJsonNull()) {
                    try {
                        totalDurationSec += item.get("duration").getAsLong();
                    } catch (Exception ignored) {
                    }
                }
                if (TextUtils.isEmpty(firstThumb)) {
                    firstThumb = getJsonString(item, "thumbnail", null);
                    if (TextUtils.isEmpty(firstThumb) && item.has("thumbnails") && item.get("thumbnails").isJsonArray()) {
                        JsonArray thumbs = item.getAsJsonArray("thumbnails");
                        if (thumbs.size() > 0 && thumbs.get(thumbs.size() - 1).isJsonObject()) {
                            firstThumb = getJsonString(thumbs.get(thumbs.size() - 1).getAsJsonObject(), "url", null);
                        }
                    }
                }
            }
            res.thumbnailUrl = firstThumb;
            res.durationMs = Math.max(0L, totalDurationSec) * 1000L;
            res.durationText = res.durationMs > 0
                    ? TimeFormatter.formatDuration(res.durationMs) + " (" + entries.size() + " bài)"
                    : entries.size() + " mục trong playlist";
            res.hasVideoStreams = true;
            res.approxSizeBytes = Math.max(entries.size() * 4_500_000L, 5_000_000L);

            populateDefaultFormatsAndQualities(res, null);
            return res;
        } catch (Exception ignored) {
            return null;
        }
    }

    private MediaAnalysisResult mapVideoInfo(String url, VideoInfo info) {
        MediaAnalysisResult res = new MediaAnalysisResult();
        res.originalUrl = url;
        res.mediaId = !TextUtils.isEmpty(info.getId()) ? info.getId() : String.valueOf(Math.abs(url.hashCode()));
        String extractor = info.getExtractor();
        res.sourceName = !TextUtils.isEmpty(extractor) ? detectSourceName(extractor) : detectSourceName(url);
        res.title = !TextUtils.isEmpty(info.getTitle()) ? info.getTitle() : "Media " + res.mediaId;
        res.channelName = !TextUtils.isEmpty(info.getUploader()) ? info.getUploader() : res.sourceName;
        res.thumbnailUrl = info.getThumbnail();

        long durSec = Math.max(0L, info.getDuration());
        res.durationMs = durSec * 1000L;
        res.durationText = durSec > 0 ? TimeFormatter.formatDuration(res.durationMs) : "Không rõ";
        res.isPlaylist = false;
        res.playlistCount = 1;

        List<VideoFormat> formats = info.getFormats();
        Set<Integer> videoHeights = new LinkedHashSet<>();
        int bestAudioAbr = -1;
        int bestCombinedHeight = -1;
        if (formats != null) {
            for (VideoFormat fmt : formats) {
                if (fmt == null) continue;
                String vcodec = fmt.getVcodec();
                String acodec = fmt.getAcodec();
                int h = fmt.getHeight();
                if (h > 0 && (vcodec == null || !"none".equalsIgnoreCase(vcodec))) {
                    videoHeights.add(h);
                    if (acodec != null && !"none".equalsIgnoreCase(acodec)
                            && !TextUtils.isEmpty(fmt.getUrl())
                            && h > bestCombinedHeight) {
                        bestCombinedHeight = h;
                        res.bestVideoUrl = fmt.getUrl();
                    }
                }
                if ((vcodec == null || "none".equalsIgnoreCase(vcodec))
                        && acodec != null && !"none".equalsIgnoreCase(acodec)
                        && !TextUtils.isEmpty(fmt.getUrl())) {
                    int abr = fmt.getAbr();
                    if (abr >= bestAudioAbr) {
                        bestAudioAbr = abr;
                        res.bestAudioUrl = fmt.getUrl();
                    }
                }
            }
        }
        res.hasVideoStreams = !videoHeights.isEmpty();
        res.approxSizeBytes = durSec > 0 ? durSec * 40_000L : 5_242_880L;

        populateDefaultFormatsAndQualities(res, videoHeights);
        return res;
    }

    private void populateDefaultFormatsAndQualities(MediaAnalysisResult res, Set<Integer> detectedHeights) {
        res.availableFormats.clear();
        res.availableFormats.add("MP3");
        res.availableFormats.add("M4A");
        res.availableFormats.add("OPUS");
        res.availableFormats.add("FLAC");
        if (res.hasVideoStreams) {
            res.availableFormats.add("MP4");
        }

        res.audioQualities.clear();
        res.audioQualities.add("320 kbps");
        res.audioQualities.add("192 kbps");
        res.audioQualities.add("128 kbps");

        res.videoQualities.clear();
        if (detectedHeights != null && !detectedHeights.isEmpty()) {
            List<Integer> sorted = new ArrayList<>(detectedHeights);
            Collections.sort(sorted, Collections.reverseOrder());
            int[] standardBuckets = new int[]{1080, 720, 480, 360};
            for (int bucket : standardBuckets) {
                for (int h : sorted) {
                    if (h >= bucket - 40 && h <= bucket + 40) {
                        String label = bucket + "p";
                        if (!res.videoQualities.contains(label)) {
                            res.videoQualities.add(label);
                        }
                        break;
                    }
                }
            }
        }
        if (res.videoQualities.isEmpty()) {
            res.videoQualities.add("1080p");
            res.videoQualities.add("720p");
            res.videoQualities.add("480p");
            res.videoQualities.add("360p");
        }
    }

    private String getJsonString(JsonObject obj, String key, String fallback) {
        if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                String val = obj.get(key).getAsString();
                if (!TextUtils.isEmpty(val)) return val;
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private String mapFriendlyAnalyzeError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        String lower = msg.toLowerCase(Locale.ROOT);
        if (lower.contains("unsupported url")) {
            return "Nguồn hoặc định dạng liên kết này hiện chưa được yt-dlp hỗ trợ.";
        }
        if (lower.contains("unable to resolve") || lower.contains("network") || lower.contains("timed out")
                || lower.contains("connection")) {
            return "Không có kết nối mạng hoặc máy chủ nguồn không phản hồi.";
        }
        if (lower.contains("private video") || lower.contains("sign in")) {
            return "Nội dung này ở chế độ riêng tư hoặc yêu cầu đăng nhập trên nguồn gốc.";
        }
        return "Không thể phân tích liên kết: " + (TextUtils.isEmpty(msg) ? "Lỗi không xác định" : msg);
    }
}
