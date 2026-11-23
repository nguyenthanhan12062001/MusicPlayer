package com.music.annnnn.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.music.annnnn.R;
import com.music.annnnn.data.download.MediaEngineInitializer;
import com.music.annnnn.data.download.MediaStorageHelper;
import com.music.annnnn.data.local.AppDatabase;
import com.music.annnnn.data.local.dao.DownloadDao;
import com.music.annnnn.data.local.dao.MusicDao;
import com.music.annnnn.data.local.entity.DownloadTaskEntity;
import com.music.annnnn.ui.main.MainActivity;
import com.music.annnnn.util.TimeFormatter;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.mapper.VideoFormat;
import com.yausername.youtubedl_android.mapper.VideoInfo;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Foreground Service managing concurrent yt-dlp + FFmpeg downloads with Room persistence,
 * direct HTTP stream fallback, and SAF/MediaStore export.
 */
public class MediaDownloadService extends Service {

    public static final String CHANNEL_ID = "office_music_download_channel";
    public static final int NOTIFICATION_ID = 2002;
    public static final int COMPLETED_NOTIFICATION_ID = 2003;

    public static final String ACTION_START_TASK = "com.music.annnnn.DL_START_TASK";
    public static final String ACTION_PAUSE_TASK = "com.music.annnnn.DL_PAUSE_TASK";
    public static final String ACTION_RESUME_TASK = "com.music.annnnn.DL_RESUME_TASK";
    public static final String ACTION_CANCEL_TASK = "com.music.annnnn.DL_CANCEL_TASK";
    public static final String EXTRA_TASK_ID = "extra_task_id";

    private static final Pattern DOWNLOAD_LINE_PATTERN = Pattern.compile(
            "\\[download\\]\\s+([0-9]+(?:\\.[0-9]+)?)%\\s+of\\s+~?\\s*([0-9.]+[A-Za-z]+)(?:\\s+at\\s+([0-9.]+[A-Za-z]+/s))?(?:\\s+ETA\\s+([0-9:]+))?"
    );
    private static final Pattern PLAYLIST_ITEM_PATTERN = Pattern.compile(
            "Downloading\\s+item\\s+(\\d+)\\s+of\\s+(\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    private DownloadDao downloadDao;
    private MusicDao musicDao;
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService controlExecutor = Executors.newSingleThreadExecutor();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    private final Set<Long> runningTaskIds = ConcurrentHashMap.newKeySet();
    private final Set<Long> pausedTaskIds = ConcurrentHashMap.newKeySet();
    private final Set<Long> cancelledTaskIds = ConcurrentHashMap.newKeySet();
    private final Map<Long, String> activeProcessIds = new ConcurrentHashMap<>();

    public static void startTask(Context context, long taskId) {
        Intent intent = new Intent(context, MediaDownloadService.class);
        intent.setAction(ACTION_START_TASK);
        intent.putExtra(EXTRA_TASK_ID, taskId);
        try {
            context.startForegroundService(intent);
        } catch (Exception e) {
            context.startService(intent);
        }
    }

    public static void pauseTask(Context context, long taskId) {
        Intent intent = new Intent(context, MediaDownloadService.class);
        intent.setAction(ACTION_PAUSE_TASK);
        intent.putExtra(EXTRA_TASK_ID, taskId);
        context.startService(intent);
    }

    public static void resumeOrRetryTask(Context context, long taskId) {
        Intent intent = new Intent(context, MediaDownloadService.class);
        intent.setAction(ACTION_RESUME_TASK);
        intent.putExtra(EXTRA_TASK_ID, taskId);
        try {
            context.startForegroundService(intent);
        } catch (Exception e) {
            context.startService(intent);
        }
    }

    public static void cancelTask(Context context, long taskId) {
        Intent intent = new Intent(context, MediaDownloadService.class);
        intent.setAction(ACTION_CANCEL_TASK);
        intent.putExtra(EXTRA_TASK_ID, taskId);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppDatabase db = AppDatabase.getInstance(this);
        downloadDao = db.downloadDao();
        musicDao = db.musicDao();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            checkStopSelfIfIdle();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        long taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L);
        if (taskId <= 0L) {
            checkStopSelfIfIdle();
            return START_NOT_STICKY;
        }

        switch (action) {
            case ACTION_START_TASK:
            case ACTION_RESUME_TASK:
                pausedTaskIds.remove(taskId);
                cancelledTaskIds.remove(taskId);
                startForeground(NOTIFICATION_ID, buildProgressNotification(getString(R.string.dl_status_preparing), 0, true));
                enqueueTaskExecution(taskId);
                break;

            case ACTION_PAUSE_TASK:
                pausedTaskIds.add(taskId);
                destroyActiveProcess(taskId);
                controlExecutor.execute(() -> {
                    DownloadTaskEntity task = downloadDao.getTaskByIdSync(taskId);
                    if (task != null && !DownloadTaskEntity.STATUS_COMPLETED.equals(task.status)) {
                        task.status = DownloadTaskEntity.STATUS_PAUSED;
                        task.speedBytesPerSec = 0;
                        task.speedText = "";
                        downloadDao.updateTask(task);
                    }
                    checkStopSelfIfIdle();
                });
                break;

            case ACTION_CANCEL_TASK:
                cancelledTaskIds.add(taskId);
                pausedTaskIds.remove(taskId);
                destroyActiveProcess(taskId);
                controlExecutor.execute(() -> {
                    DownloadTaskEntity task = downloadDao.getTaskByIdSync(taskId);
                    if (task != null) {
                        if (!TextUtils.isEmpty(task.tempDirPath)) {
                            MediaStorageHelper.deleteRecursively(new File(task.tempDirPath));
                        }
                        if (!TextUtils.isEmpty(task.filePath) && task.filePath.contains("/downloads/")) {
                            MediaStorageHelper.deleteRecursively(new File(task.filePath));
                        }
                    }
                    downloadDao.deleteTaskById(taskId);
                    checkStopSelfIfIdle();
                });
                break;
        }

        return START_NOT_STICKY;
    }

    private void destroyActiveProcess(long taskId) {
        String procId = activeProcessIds.get(taskId);
        if (!TextUtils.isEmpty(procId)) {
            try {
                YoutubeDL.getInstance().destroyProcessById(procId);
            } catch (Exception ignored) {
            }
        }
    }

    private void enqueueTaskExecution(long taskId) {
        if (!runningTaskIds.add(taskId)) {
            return;
        }
        downloadExecutor.execute(() -> {
            try {
                executeDownloadTask(taskId);
            } finally {
                runningTaskIds.remove(taskId);
                activeProcessIds.remove(taskId);
                checkStopSelfIfIdle();
            }
        });
    }

    private void executeDownloadTask(long taskId) {
        DownloadTaskEntity task = downloadDao.getTaskByIdSync(taskId);
        if (task == null || cancelledTaskIds.contains(taskId) || pausedTaskIds.contains(taskId)) {
            return;
        }

        if (!MediaEngineInitializer.ensureInitialized(this)) {
            task.status = DownloadTaskEntity.STATUS_FAILED;
            task.errorMessage = !TextUtils.isEmpty(MediaEngineInitializer.getInitError())
                    ? MediaEngineInitializer.getInitError()
                    : getString(R.string.dl_status_failed);
            task.speedBytesPerSec = 0;
            downloadDao.updateTask(task);
            return;
        }

        File tempDir = !TextUtils.isEmpty(task.tempDirPath)
                ? new File(task.tempDirPath)
                : new File(getFilesDir(), "dl_temp/" + task.id);
        if (!tempDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tempDir.mkdirs();
        }

        String processId = "office_dl_" + task.id + "_" + System.nanoTime();
        activeProcessIds.put(taskId, processId);

        task.status = DownloadTaskEntity.STATUS_DOWNLOADING;
        task.processId = processId;
        task.tempDirPath = tempDir.getAbsolutePath();
        task.errorMessage = null;
        task.speedText = "";
        downloadDao.updateTask(task);

        final long[] lastDbUpdateMs = new long[]{0L};
        Function3<Float, Long, String, Unit> callback = (progress, etaInSeconds, line) -> {
            if (cancelledTaskIds.contains(taskId) || pausedTaskIds.contains(taskId)) {
                return Unit.INSTANCE;
            }
            parseProgressLine(task, progress != null ? progress : 0f, etaInSeconds != null ? etaInSeconds : -1L, line);
            long now = System.currentTimeMillis();
            if (now - lastDbUpdateMs[0] >= 500L || DownloadTaskEntity.STATUS_CONVERTING.equals(task.status)) {
                lastDbUpdateMs[0] = now;
                downloadDao.updateTask(task);
                updateForegroundNotification(task);
            }
            return Unit.INSTANCE;
        };

        try {
            runDownloadPipeline(task, tempDir, processId, callback);

            if (cancelledTaskIds.remove(taskId)) {
                MediaStorageHelper.deleteRecursively(tempDir);
                downloadDao.deleteTaskById(taskId);
                return;
            }
            if (pausedTaskIds.remove(taskId)) {
                task.status = DownloadTaskEntity.STATUS_PAUSED;
                task.speedBytesPerSec = 0;
                task.speedText = "";
                downloadDao.updateTask(task);
                return;
            }

            task.status = DownloadTaskEntity.STATUS_CONVERTING;
            task.progress = 98;
            task.speedText = getString(R.string.dl_status_converting);
            downloadDao.updateTask(task);
            updateForegroundNotification(task);

            MediaStorageHelper.StorageResult stored = MediaStorageHelper.persistDownloadedOutputs(
                    this, task, tempDir, musicDao
            );

            task.filePath = stored.primaryPlayablePath;
            task.mediaStoreUri = stored.primaryMediaStoreUri;
            if (stored.totalBytes > 0) {
                task.totalBytes = stored.totalBytes;
                task.downloadedBytes = stored.totalBytes;
                task.sizeText = TimeFormatter.formatBytes(stored.totalBytes);
            }
            task.progress = 100;
            task.etaSeconds = 0;
            task.speedBytesPerSec = 0;
            task.speedText = "";
            task.status = DownloadTaskEntity.STATUS_COMPLETED;
            task.errorMessage = null;
            downloadDao.updateTask(task);

            showCompletedNotification(task);
        } catch (Exception e) {
            if (cancelledTaskIds.remove(taskId)) {
                MediaStorageHelper.deleteRecursively(tempDir);
                downloadDao.deleteTaskById(taskId);
                return;
            }
            if (pausedTaskIds.remove(taskId)) {
                task.status = DownloadTaskEntity.STATUS_PAUSED;
                task.speedBytesPerSec = 0;
                task.speedText = "";
                downloadDao.updateTask(task);
                return;
            }
            task.status = DownloadTaskEntity.STATUS_FAILED;
            task.speedBytesPerSec = 0;
            task.speedText = "";
            task.errorMessage = mapFriendlyDownloadError(e);
            downloadDao.updateTask(task);
        }
    }

    private void runDownloadPipeline(
            DownloadTaskEntity task,
            File tempDir,
            String processId,
            Function3<Float, Long, String, Unit> callback
    ) throws Exception {
        long taskId = task.id;
        Exception lastEx = null;

        // Stage 1: Primary yt-dlp download without aria2c (avoids CDN 403 / stalled connections)
        try {
            YoutubeDLRequest primaryReq = buildYoutubeDlRequest(task, tempDir, false);
            YoutubeDL.getInstance().execute(primaryReq, processId, callback);
            if (hasAnyMediaOutput(tempDir)) {
                return;
            }
        } catch (Exception ex) {
            lastEx = ex;
        }

        if (cancelledTaskIds.contains(taskId) || pausedTaskIds.contains(taskId)) {
            return;
        }

        // Stage 2: Retry yt-dlp with Android/Web YouTube player client override
        try {
            clearTempDirectory(tempDir);
            String retryProcId = "office_dl_cli_" + taskId + "_" + System.nanoTime();
            activeProcessIds.put(taskId, retryProcId);
            YoutubeDLRequest clientRetryReq = buildYoutubeDlRequest(task, tempDir, true);
            YoutubeDL.getInstance().execute(clientRetryReq, retryProcId, callback);
            if (hasAnyMediaOutput(tempDir)) {
                return;
            }
        } catch (Exception ex) {
            lastEx = ex;
        }

        if (cancelledTaskIds.contains(taskId) || pausedTaskIds.contains(taskId)) {
            return;
        }

        // Stage 3: Direct OkHttp fallback using cached or fresh VideoInfo stream URL (single tracks)
        if (!task.isPlaylist) {
            try {
                clearTempDirectory(tempDir);
                if (downloadDirectStreamFallback(task, tempDir)) {
                    return;
                }
            } catch (Exception ex) {
                lastEx = ex;
            }
        }

        if (lastEx != null) {
            throw lastEx;
        }
        throw new IOException("No media file produced");
    }

    private boolean hasAnyMediaOutput(File dir) {
        if (dir == null || !dir.exists()) return false;
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f.isDirectory()) {
                if (hasAnyMediaOutput(f)) return true;
            } else if (f.length() > 1024 && !f.getName().endsWith(".part") && !f.getName().endsWith(".ytdl")) {
                return true;
            }
        }
        return false;
    }

    private void clearTempDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            MediaStorageHelper.deleteRecursively(f);
        }
    }

    private boolean downloadDirectStreamFallback(DownloadTaskEntity task, File tempDir) throws Exception {
        boolean wantVideo = DownloadTaskEntity.MEDIA_VIDEO.equals(task.mediaType)
                || "MP4".equalsIgnoreCase(task.format);

        String directUrl = task.streamUrl;
        String resolvedExt = wantVideo ? "mp4" : "m4a";

        // Fetch fresh stream URL if missing or resolve exact container extension
        try {
            VideoInfo info = YoutubeDL.getInstance().getInfo(task.youtubeUrl);
            if (info != null && info.getFormats() != null) {
                List<VideoFormat> formats = info.getFormats();
                int bestAbr = -1;
                int bestHeight = -1;
                String candidateUrl = null;
                String candidateExt = resolvedExt;
                for (VideoFormat fmt : formats) {
                    if (fmt == null || TextUtils.isEmpty(fmt.getUrl())) continue;
                    String vcodec = fmt.getVcodec();
                    String acodec = fmt.getAcodec();
                    if (wantVideo) {
                        int h = fmt.getHeight();
                        if (h > 0 && (vcodec == null || !"none".equalsIgnoreCase(vcodec))
                                && acodec != null && !"none".equalsIgnoreCase(acodec)
                                && h >= bestHeight) {
                            bestHeight = h;
                            candidateUrl = fmt.getUrl();
                            candidateExt = !TextUtils.isEmpty(fmt.getExt()) ? fmt.getExt() : "mp4";
                        }
                    } else {
                        if ((vcodec == null || "none".equalsIgnoreCase(vcodec))
                                && acodec != null && !"none".equalsIgnoreCase(acodec)) {
                            int abr = fmt.getAbr();
                            if (abr >= bestAbr) {
                                bestAbr = abr;
                                candidateUrl = fmt.getUrl();
                                candidateExt = !TextUtils.isEmpty(fmt.getExt()) ? fmt.getExt() : "m4a";
                            }
                        }
                    }
                }
                if (!TextUtils.isEmpty(candidateUrl)) {
                    directUrl = candidateUrl;
                    resolvedExt = candidateExt;
                }
            }
        } catch (Exception ignored) {
        }

        if (TextUtils.isEmpty(directUrl)) {
            return false;
        }

        Request req = new Request.Builder()
                .url(directUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
                .build();

        File outFile = new File(tempDir, "track." + resolvedExt);
        try (Response resp = httpClient.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code());
            }
            ResponseBody body = resp.body();
            if (body == null) {
                throw new IOException("Empty response body");
            }
            long totalLen = body.contentLength();
            long downloaded = 0L;
            long windowStartMs = System.currentTimeMillis();
            long windowBytes = 0L;
            long lastUiUpdateMs = 0L;

            try (InputStream is = body.byteStream();
                 FileOutputStream fos = new FileOutputStream(outFile)) {
                byte[] buf = new byte[16 * 1024];
                int read;
                while ((read = is.read(buf)) != -1) {
                    if (cancelledTaskIds.contains(task.id) || pausedTaskIds.contains(task.id)) {
                        return false;
                    }
                    fos.write(buf, 0, read);
                    downloaded += read;
                    windowBytes += read;

                    long now = System.currentTimeMillis();
                    if (now - lastUiUpdateMs >= 500L) {
                        long elapsed = Math.max(1L, now - windowStartMs);
                        long speed = (windowBytes * 1000L) / elapsed;
                        task.downloadedBytes = downloaded;
                        if (totalLen > 0) {
                            task.totalBytes = totalLen;
                            task.progress = Math.min(96, (int) ((downloaded * 100L) / totalLen));
                            task.sizeText = TimeFormatter.formatBytes(downloaded) + " / " + TimeFormatter.formatBytes(totalLen);
                            if (speed > 0) {
                                task.etaSeconds = Math.max(0L, (totalLen - downloaded) / speed);
                            }
                        } else {
                            task.progress = Math.min(90, task.progress + 2);
                            task.sizeText = TimeFormatter.formatBytes(downloaded);
                        }
                        task.speedBytesPerSec = speed;
                        task.speedText = TimeFormatter.formatBytes(speed) + "/s";
                        downloadDao.updateTask(task);
                        updateForegroundNotification(task);

                        lastUiUpdateMs = now;
                        if (elapsed >= 2000L) {
                            windowStartMs = now;
                            windowBytes = 0L;
                        }
                    }
                }
                fos.flush();
            }
        }
        return outFile.exists() && outFile.length() > 1024;
    }

    private YoutubeDLRequest buildYoutubeDlRequest(DownloadTaskEntity task, File tempDir, boolean useAndroidClientFallback) {
        YoutubeDLRequest req = new YoutubeDLRequest(task.youtubeUrl);
        req.addOption("--no-mtime");
        req.addOption("--newline");
        req.addOption("--force-ipv4");
        req.addOption("--no-check-certificates");
        req.addOption("--no-cache-dir");
        req.addOption("--no-part");
        req.addOption("--retries", "5");
        req.addOption("--fragment-retries", "5");
        req.addOption("--socket-timeout", "25");
        req.addOption("--concurrent-fragments", "4");

        if (useAndroidClientFallback) {
            req.addOption("--extractor-args", "youtube:player_client=android,web");
        }

        if (task.isPlaylist) {
            req.addOption("--yes-playlist");
            req.addOption("--ignore-errors");
            req.addOption("-o", tempDir.getAbsolutePath() + "/%(playlist_index)03d_track.%(ext)s");
        } else {
            req.addOption("--no-playlist");
            req.addOption("-o", tempDir.getAbsolutePath() + "/track.%(ext)s");
        }

        boolean ffmpegReady = MediaEngineInitializer.isFfmpegReady();
        String fmtUpper = task.format != null ? task.format.toUpperCase(Locale.ROOT) : "MP3";
        if (DownloadTaskEntity.MEDIA_VIDEO.equals(task.mediaType) || "MP4".equals(fmtUpper)) {
            int maxHeight = parseVideoHeight(task.quality);
            String selector = ffmpegReady
                    ? String.format(
                            Locale.ROOT,
                            "bestvideo[height<=%d][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=%d]+bestaudio/best[height<=%d]/best",
                            maxHeight, maxHeight, maxHeight
                    )
                    : String.format(
                            Locale.ROOT,
                            "best[height<=%d][ext=mp4]/best[height<=%d]/best",
                            maxHeight, maxHeight
                    );
            req.addOption("-f", selector);
            if (ffmpegReady) {
                req.addOption("--merge-output-format", "mp4");
            }
        } else {
            req.addOption("-f", "bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio/best");
            if (ffmpegReady) {
                req.addOption("-x");
                req.addOption("--audio-format", fmtUpper.toLowerCase(Locale.ROOT));
                req.addOption("--audio-quality", mapAudioQualityFlag(task.quality));
            }
        }

        return req;
    }

    private int parseVideoHeight(String quality) {
        if (quality != null) {
            if (quality.contains("1080")) return 1080;
            if (quality.contains("720")) return 720;
            if (quality.contains("480")) return 480;
            if (quality.contains("360")) return 360;
        }
        return 720;
    }

    private String mapAudioQualityFlag(String quality) {
        if (quality != null) {
            if (quality.contains("128")) return "128K";
            if (quality.contains("192")) return "192K";
            if (quality.contains("Lossless") || quality.contains("320")) return "0";
        }
        return "0";
    }

    private void parseProgressLine(DownloadTaskEntity task, float progress, long etaInSeconds, String line) {
        if (progress >= 0f && progress <= 100f) {
            task.progress = Math.min(97, Math.round(progress));
        }
        if (etaInSeconds >= 0L) {
            task.etaSeconds = etaInSeconds;
        }
        if (TextUtils.isEmpty(line)) return;

        if (line.contains("[ExtractAudio]") || line.contains("[Merger]") || line.contains("[ffmpeg]")
                || line.contains("Converting")) {
            task.status = DownloadTaskEntity.STATUS_CONVERTING;
            task.progress = Math.max(task.progress, 95);
            task.speedText = getString(R.string.dl_status_converting);
            return;
        }

        Matcher itemMatcher = PLAYLIST_ITEM_PATTERN.matcher(line);
        if (itemMatcher.find()) {
            try {
                task.currentItemIndex = Integer.parseInt(itemMatcher.group(1));
                int totalItems = Integer.parseInt(itemMatcher.group(2));
                if (totalItems > 0) task.playlistCount = totalItems;
            } catch (Exception ignored) {
            }
        }

        Matcher dlMatcher = DOWNLOAD_LINE_PATTERN.matcher(line);
        if (dlMatcher.find()) {
            task.status = DownloadTaskEntity.STATUS_DOWNLOADING;
            String pctStr = dlMatcher.group(1);
            String sizeStr = dlMatcher.group(2);
            String speedStr = dlMatcher.group(3);
            if (!TextUtils.isEmpty(pctStr)) {
                try {
                    task.progress = Math.min(96, Math.round(Float.parseFloat(pctStr)));
                } catch (Exception ignored) {
                }
            }
            if (!TextUtils.isEmpty(sizeStr)) {
                task.sizeText = sizeStr;
                long parsedTotal = parseHumanSizeBytes(sizeStr);
                if (parsedTotal > 0) {
                    task.totalBytes = parsedTotal;
                    task.downloadedBytes = (parsedTotal * task.progress) / 100L;
                }
            }
            if (!TextUtils.isEmpty(speedStr)) {
                task.speedText = speedStr;
                task.speedBytesPerSec = parseHumanSizeBytes(speedStr.replace("/s", ""));
            }
        }
    }

    private long parseHumanSizeBytes(String text) {
        if (TextUtils.isEmpty(text)) return 0L;
        try {
            String clean = text.trim().toUpperCase(Locale.ROOT);
            double multiplier = 1.0;
            if (clean.endsWith("GIB") || clean.endsWith("GB")) {
                multiplier = 1024.0 * 1024.0 * 1024.0;
                clean = clean.replace("GIB", "").replace("GB", "");
            } else if (clean.endsWith("MIB") || clean.endsWith("MB")) {
                multiplier = 1024.0 * 1024.0;
                clean = clean.replace("MIB", "").replace("MB", "");
            } else if (clean.endsWith("KIB") || clean.endsWith("KB")) {
                multiplier = 1024.0;
                clean = clean.replace("KIB", "").replace("KB", "");
            } else if (clean.endsWith("B")) {
                clean = clean.replace("B", "");
            }
            return (long) (Double.parseDouble(clean.trim()) * multiplier);
        } catch (Exception e) {
            return 0L;
        }
    }

    private String mapFriendlyDownloadError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        String lower = msg.toLowerCase(Locale.ROOT);
        if (lower.contains("network") || lower.contains("timed out") || lower.contains("resolve")
                || lower.contains("connection")) {
            return getString(R.string.dl_status_failed) + " (" + msg + ")";
        }
        return !TextUtils.isEmpty(msg) ? msg : getString(R.string.dl_status_failed);
    }

    private void updateForegroundNotification(DownloadTaskEntity task) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        int activeCount = runningTaskIds.size();
        String title = activeCount > 1
                ? "(" + activeCount + ") " + task.title
                : task.title;
        boolean indeterminate = DownloadTaskEntity.STATUS_CONVERTING.equals(task.status);
        nm.notify(NOTIFICATION_ID, buildProgressNotification(title, task.progress, indeterminate));
    }

    private Notification buildProgressNotification(String title, int progress, boolean indeterminate) {
        PendingIntent openPending = PendingIntent.getActivity(
                this, 0,
                MainActivity.createOpenDownloadIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String subText = indeterminate ? getString(R.string.dl_status_converting) : (progress + "%");
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(TextUtils.isEmpty(title) ? getString(R.string.dl_status_preparing) : title)
                .setContentText(subText)
                .setContentIntent(openPending)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, progress, indeterminate)
                .build();
    }

    private void showCompletedNotification(DownloadTaskEntity task) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        PendingIntent openPending = PendingIntent.getActivity(
                this, 1,
                MainActivity.createOpenDownloadIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String msg = DownloadTaskEntity.MEDIA_VIDEO.equals(task.mediaType)
                ? getString(R.string.dl_status_completed_video)
                : getString(R.string.dl_status_completed_audio);

        Notification done = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(task.title)
                .setContentText(msg)
                .setContentIntent(openPending)
                .setAutoCancel(true)
                .build();
        nm.notify(COMPLETED_NOTIFICATION_ID, done);
    }

    private void checkStopSelfIfIdle() {
        if (runningTaskIds.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.download_title),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.download_engine_subtitle));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        downloadExecutor.shutdownNow();
        controlExecutor.shutdownNow();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
