package com.music.annnnn.util;

import android.content.Context;

import com.music.annnnn.R;

import java.util.Locale;

public class TimeFormatter {

    public static String formatDuration(long ms) {
        if (ms < 0) ms = 0;
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    public static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 KB";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024.0) return String.format(Locale.getDefault(), "%.1f KB", kb);
        double mb = kb / 1024.0;
        return String.format(Locale.getDefault(), "%.2f MB", mb);
    }

    public static String formatListeningTime(long totalSeconds) {
        if (totalSeconds < 60) {
            return totalSeconds + " giây";
        }
        long minutes = totalSeconds / 60;
        if (minutes < 60) {
            return minutes + " phút";
        }
        long hours = minutes / 60;
        long remMin = minutes % 60;
        return hours + " giờ " + remMin + " phút";
    }

    public static String formatListeningTime(Context context, long totalSeconds) {
        if (context == null) {
            return formatListeningTime(totalSeconds);
        }
        if (totalSeconds < 60) {
            return context.getString(R.string.time_sec_format, (int) totalSeconds);
        }
        long minutes = totalSeconds / 60;
        if (minutes < 60) {
            return context.getString(R.string.time_min_format, (int) minutes);
        }
        long hours = minutes / 60;
        long remMin = minutes % 60;
        return context.getString(R.string.time_hour_min_format, (int) hours, (int) remMin);
    }
}
