package com.music.annnnn.data.network;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.music.annnnn.data.local.dao.DownloadDao;
import com.music.annnnn.data.local.dao.MusicDao;
import com.music.annnnn.data.local.entity.DownloadTaskEntity;
import com.music.annnnn.data.local.entity.SongEntity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Analyzes YouTube URLs via oEmbed + Innertube Player API and downloads audio streams
 * with real byte progress, pause/resume/cancel/retry support.
 */
public class YouTubeAnalyzer {

    public static class VideoInfo {
        public String videoId;
        public String originalUrl;
        public String title;
        public String channelName;
        public String thumbnailUrl;
        public String durationText;
        public long durationMs;
        public String audioStreamUrl;
        public long approxFileSizeBytes;
    }

    public interface AnalyzeCallback {
        void onSuccess(VideoInfo info);

        void onError(String message);
    }

    private static final Pattern YT_ID_PATTERN = Pattern.compile(
            "(?:youtube\\.com/(?:watch\\?v=|embed/|shorts/|v/)|youtu\\.be/)([A-Za-z0-9_-]{11})"
    );

    private final Context appContext;
    private final OkHttpClient client;
    private final MusicDao musicDao;
    private final DownloadDao downloadDao;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Map<Long, Boolean> pauseFlags = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> cancelFlags = new ConcurrentHashMap<>();

    public YouTubeAnalyzer(Context context, MusicDao musicDao, DownloadDao downloadDao) {
        this.appContext = context.getApplicationContext();
        this.musicDao = musicDao;
        this.downloadDao = downloadDao;
        this.client = new OkHttpClient.Builder()
                .followRedirects(true)
                .build();
    }

    public static String extractVideoId(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        String trimmed = url.trim();
        Matcher matcher = YT_ID_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1);
        }
        Matcher queryMatcher = Pattern.compile("[?&]v=([A-Za-z0-9_-]{11})").matcher(trimmed);
        if (queryMatcher.find()) {
            return queryMatcher.group(1);
        }
        return null;
    }

    public void analyzeUrl(String rawUrl, AnalyzeCallback callback) {
        executor.execute(() -> {
            String videoId = extractVideoId(rawUrl);
            if (videoId == null) {
                callback.onError("Đường dẫn YouTube không hợp lệ. Vui lòng kiểm tra lại liên kết.");
                return;
            }

            try {
                String canonicalUrl = "https://www.youtube.com/watch?v=" + videoId;
                String oembedUrl = "https://www.youtube.com/oembed?url="
                        + URLEncoder.encode(canonicalUrl, StandardCharsets.UTF_8.name())
                        + "&format=json";

                Request oembedReq = new Request.Builder()
                        .url(oembedUrl)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                        .build();

                VideoInfo info = new VideoInfo();
                info.videoId = videoId;
                info.originalUrl = canonicalUrl;
                info.thumbnailUrl = "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg";
                info.durationText = "03:30";
                info.durationMs = 210_000L;
                info.approxFileSizeBytes = 5_242_880L;

                try (Response resp = client.newCall(oembedReq).execute()) {
                    if (!resp.isSuccessful() || resp.body() == null) {
                        callback.onError("Không thể phân tích video từ YouTube (HTTP " + resp.code() + ").");
                        return;
                    }
                    String jsonStr = resp.body().string();
                    JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
                    info.title = obj.has("title") ? obj.get("title").getAsString() : "YouTube Audio " + videoId;
                    info.channelName = obj.has("author_name") ? obj.get("author_name").getAsString() : "YouTube Channel";
                    if (obj.has("thumbnail_url")) {
                        info.thumbnailUrl = obj.get("thumbnail_url").getAsString();
                    }
                }

                // Query Innertube Android client for duration and audio stream URL
                fetchPlayerStreamDetails(info);
                callback.onSuccess(info);
            } catch (Exception e) {
                callback.onError("Lỗi kết nối khi phân tích liên kết: " + e.getMessage());
            }
        });
    }

    private void fetchPlayerStreamDetails(VideoInfo info) {
        try {
            JsonObject clientObj = new JsonObject();
            clientObj.addProperty("clientName", "ANDROID");
            clientObj.addProperty("clientVersion", "19.09.37");
            clientObj.addProperty("androidSdkVersion", 30);

            JsonObject contextObj = new JsonObject();
            contextObj.add("client", clientObj);

            JsonObject bodyObj = new JsonObject();
            bodyObj.add("context", contextObj);
            bodyObj.addProperty("videoId", info.videoId);
            bodyObj.addProperty("contentCheckOk", true);
            bodyObj.addProperty("racyCheckOk", true);

            RequestBody reqBody = RequestBody.create(
                    bodyObj.toString(),
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request req = new Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                    .post(reqBody)
                    .header("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip")
                    .build();

            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return;
                String json = resp.body().string();
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();

                if (root.has("videoDetails")) {
                    JsonObject details = root.getAsJsonObject("videoDetails");
                    if (details.has("lengthSeconds")) {
                        long secs = Long.parseLong(details.get("lengthSeconds").getAsString());
                        if (secs > 0) {
                            info.durationMs = secs * 1000L;
                            long min = secs / 60;
                            long rem = secs % 60;
                            info.durationText = String.format("%02d:%02d", min, rem);
                        }
                    }
                }

                if (root.has("streamingData")) {
                    JsonObject streaming = root.getAsJsonObject("streamingData");
                    if (streaming.has("adaptiveFormats")) {
                        JsonArray formats = streaming.getAsJsonArray("adaptiveFormats");
                        long bestBitrate = 0;
                        for (JsonElement el : formats) {
                            JsonObject f = el.getAsJsonObject();
                            String mime = f.has("mimeType") ? f.get("mimeType").getAsString() : "";
                            if (mime.startsWith("audio/") && f.has("url")) {
                                long bitrate = f.has("bitrate") ? f.get("bitrate").getAsLong() : 128000;
                                if (bitrate >= bestBitrate) {
                                    bestBitrate = bitrate;
                                    info.audioStreamUrl = f.get("url").getAsString();
                                    if (f.has("contentLength")) {
                                        info.approxFileSizeBytes = Long.parseLong(f.get("contentLength").getAsString());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void startDownload(VideoInfo info, String quality) {
        executor.execute(() -> {
            DownloadTaskEntity task = new DownloadTaskEntity();
            task.youtubeUrl = info.originalUrl;
            task.videoId = info.videoId;
            task.title = info.title != null ? info.title : "Audio " + info.videoId;
            task.channelName = info.channelName != null ? info.channelName : "YouTube";
            task.thumbnailUrl = info.thumbnailUrl;
            task.durationText = info.durationText;
            task.durationMs = info.durationMs;
            task.quality = quality;
            task.format = "MP3";
            task.status = DownloadTaskEntity.STATUS_DOWNLOADING;
            task.totalBytes = info.approxFileSizeBytes;
            task.streamUrl = info.audioStreamUrl;

            long id = downloadDao.insertTask(task);
            task.id = id;
            executeDownloadTask(task);
        });
    }

    public void pauseDownload(long taskId) {
        pauseFlags.put(taskId, true);
        executor.execute(() -> {
            DownloadTaskEntity task = downloadDao.getTaskByIdSync(taskId);
            if (task != null && DownloadTaskEntity.STATUS_DOWNLOADING.equals(task.status)) {
                task.status = DownloadTaskEntity.STATUS_PAUSED;
                task.speedBytesPerSec = 0;
                downloadDao.updateTask(task);
            }
        });
    }

    public void resumeOrRetryDownload(long taskId) {
        pauseFlags.remove(taskId);
        cancelFlags.remove(taskId);
        executor.execute(() -> {
            DownloadTaskEntity task = downloadDao.getTaskByIdSync(taskId);
            if (task != null) {
                task.status = DownloadTaskEntity.STATUS_DOWNLOADING;
                task.errorMessage = null;
                downloadDao.updateTask(task);
                executeDownloadTask(task);
            }
        });
    }

    public void cancelDownload(long taskId) {
        cancelFlags.put(taskId, true);
        pauseFlags.remove(taskId);
        executor.execute(() -> {
            DownloadTaskEntity task = downloadDao.getTaskByIdSync(taskId);
            if (task != null && !TextUtils.isEmpty(task.filePath)) {
                File f = new File(task.filePath);
                if (f.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
            downloadDao.deleteTaskById(taskId);
        });
    }

    private void executeDownloadTask(DownloadTaskEntity task) {
        pauseFlags.remove(task.id);
        cancelFlags.remove(task.id);

        if (TextUtils.isEmpty(task.streamUrl)) {
            // Re-attempt resolving stream URL once
            VideoInfo refreshInfo = new VideoInfo();
            refreshInfo.videoId = task.videoId;
            refreshInfo.originalUrl = task.youtubeUrl;
            fetchPlayerStreamDetails(refreshInfo);
            task.streamUrl = refreshInfo.audioStreamUrl;
        }

        if (TextUtils.isEmpty(task.streamUrl)) {
            task.status = DownloadTaskEntity.STATUS_FAILED;
            task.speedBytesPerSec = 0;
            task.errorMessage = "YouTube bảo vệ luồng âm thanh hoặc không có kết nối tải xuống trực tiếp.";
            downloadDao.updateTask(task);
            return;
        }

        File downloadDir = new File(appContext.getFilesDir(), "downloads");
        if (!downloadDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            downloadDir.mkdirs();
        }
        String safeName = task.videoId + "_" + task.id + ".m4a";
        File destFile = new File(downloadDir, safeName);
        task.filePath = destFile.getAbsolutePath();
        downloadDao.updateTask(task);

        long existingBytes = destFile.exists() ? destFile.length() : 0L;
        Request.Builder reqBuilder = new Request.Builder()
                .url(task.streamUrl)
                .header("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 11)");
        if (existingBytes > 0) {
            reqBuilder.header("Range", "bytes=" + existingBytes + "-");
        }

        try (Response response = client.newCall(reqBuilder.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                task.status = DownloadTaskEntity.STATUS_FAILED;
                task.speedBytesPerSec = 0;
                task.errorMessage = "Tải xuống thất bại (HTTP " + response.code() + ")";
                downloadDao.updateTask(task);
                return;
            }

            ResponseBody body = response.body();
            long contentLength = body.contentLength();
            boolean append = response.code() == 206 && existingBytes > 0;
            long totalBytes = append
                    ? existingBytes + Math.max(0, contentLength)
                    : (contentLength > 0 ? contentLength : Math.max(task.totalBytes, 1));
            task.totalBytes = totalBytes;

            byte[] buffer = new byte[16 * 1024];
            long downloaded = append ? existingBytes : 0L;
            long lastUpdateNs = System.nanoTime();
            long bytesSinceLastUpdate = 0L;

            try (InputStream is = new BufferedInputStream(body.byteStream());
                 FileOutputStream fos = new FileOutputStream(destFile, append)) {
                int read;
                while ((read = is.read(buffer)) != -1) {
                    if (Boolean.TRUE.equals(cancelFlags.get(task.id))) {
                        return;
                    }
                    if (Boolean.TRUE.equals(pauseFlags.get(task.id))) {
                        fos.flush();
                        task.status = DownloadTaskEntity.STATUS_PAUSED;
                        task.downloadedBytes = downloaded;
                        task.speedBytesPerSec = 0;
                        downloadDao.updateTask(task);
                        return;
                    }

                    fos.write(buffer, 0, read);
                    downloaded += read;
                    bytesSinceLastUpdate += read;

                    long nowNs = System.nanoTime();
                    if (nowNs - lastUpdateNs >= 350_000_000L) {
                        double sec = (nowNs - lastUpdateNs) / 1_000_000_000.0;
                        task.downloadedBytes = downloaded;
                        task.progress = (int) Math.min(99, (downloaded * 100L) / Math.max(1L, totalBytes));
                        task.speedBytesPerSec = sec > 0 ? (long) (bytesSinceLastUpdate / sec) : 0;
                        downloadDao.updateTask(task);
                        lastUpdateNs = nowNs;
                        bytesSinceLastUpdate = 0L;
                    }
                }
                fos.flush();
            }

            task.downloadedBytes = downloaded;
            task.totalBytes = Math.max(downloaded, totalBytes);
            task.progress = 100;
            task.speedBytesPerSec = 0;
            task.status = DownloadTaskEntity.STATUS_COMPLETED;
            task.errorMessage = null;
            downloadDao.updateTask(task);

            // Insert downloaded track into Library -> Nhạc đã tải xuống
            SongEntity downloadedSong = new SongEntity(
                    task.title,
                    task.channelName,
                    0L,
                    "Nhạc YouTube đã tải",
                    task.durationMs > 0 ? task.durationMs : 210_000L,
                    destFile.getAbsolutePath(),
                    "img_cover_radio",
                    task.thumbnailUrl,
                    "Tệp âm thanh đã tải xuống từ YouTube để nghe offline.",
                    "YouTube MP3",
                    "Năng lượng",
                    false,
                    true,
                    0,
                    null,
                    0
            );
            musicDao.insertSong(downloadedSong);

        } catch (IOException e) {
            task.status = DownloadTaskEntity.STATUS_FAILED;
            task.speedBytesPerSec = 0;
            task.errorMessage = "Lỗi tải xuống: " + e.getMessage();
            downloadDao.updateTask(task);
        }
    }
}
