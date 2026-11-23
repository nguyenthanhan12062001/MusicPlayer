package com.music.annnnn.data.download;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import com.yausername.aria2c.Aria2c;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thread-safe initializer for yt-dlp (youtubedl-android), FFmpeg, and aria2c native binaries.
 */
public final class MediaEngineInitializer {

    private static final Object INIT_LOCK = new Object();
    private static final ExecutorService INIT_EXECUTOR = Executors.newSingleThreadExecutor();

    private static volatile boolean initialized = false;
    private static volatile boolean ytdlpReady = false;
    private static volatile boolean ffmpegReady = false;
    private static volatile boolean aria2cReady = false;
    private static volatile String initError = null;

    private MediaEngineInitializer() {
    }

    public static void initAsync(Context context) {
        final Context appContext = context.getApplicationContext();
        INIT_EXECUTOR.execute(() -> {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            } catch (Exception ignored) {
            }
            ensureInitialized(appContext);
        });
    }

    public static boolean ensureInitialized(Context context) {
        if (initialized && ytdlpReady) {
            return true;
        }
        synchronized (INIT_LOCK) {
            if (initialized && ytdlpReady) {
                return true;
            }
            Context appContext = context.getApplicationContext();
            initError = null;

            try {
                YoutubeDL.getInstance().init(appContext);
                ytdlpReady = true;
            } catch (Throwable t) {
                ytdlpReady = false;
                initError = "Không thể khởi tạo yt-dlp: " + t.getMessage();
            }

            try {
                FFmpeg.getInstance().init(appContext);
                ffmpegReady = true;
            } catch (Throwable t) {
                ffmpegReady = false;
            }

            try {
                if (isAria2cAbiCompatible()) {
                    Aria2c.getInstance().init(appContext);
                    aria2cReady = true;
                } else {
                    aria2cReady = false;
                }
            } catch (Throwable t) {
                aria2cReady = false;
            }

            initialized = true;
            return ytdlpReady;
        }
    }

    public static boolean isAria2cAbiCompatible() {
        String[] abis = Build.SUPPORTED_ABIS;
        if (abis == null || abis.length == 0) return false;
        for (String abi : abis) {
            if ("arm64-v8a".equals(abi)
                    || "armeabi-v7a".equals(abi)
                    || "x86_64".equals(abi)
                    || "x86".equals(abi)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isYtdlpReady() {
        return ytdlpReady;
    }

    public static boolean isFfmpegReady() {
        return ffmpegReady;
    }

    public static boolean isAria2cReady() {
        return aria2cReady;
    }

    public static String getInitError() {
        return initError;
    }

    public static String getEngineSummary() {
        String abi = Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0
                ? Build.SUPPORTED_ABIS[0] : "unknown";
        return "yt-dlp " + (ytdlpReady ? "OK" : "Lỗi")
                + " • FFmpeg " + (ffmpegReady ? "OK" : "N/A")
                + " • aria2c " + (aria2cReady ? "OK" : "Tắt")
                + " (" + abi + ")";
    }
}
