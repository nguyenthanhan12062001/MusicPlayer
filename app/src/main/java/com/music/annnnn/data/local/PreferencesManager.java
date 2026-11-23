package com.music.annnnn.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public class PreferencesManager {
    private static final String PREF_NAME = "office_music_prefs";
    private static final String KEY_THEME_MODE = "theme_mode"; // 0=system, 1=light, 2=dark
    private static final String KEY_THEME_PRESET = "theme_studio_preset";
    private static final String KEY_GLASS_INTENSITY = "theme_glass_intensity";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_PLAYBACK_SPEED = "playback_speed";
    private static final String KEY_BASS_BOOST = "bass_boost";
    private static final String KEY_EQ_PRESET = "eq_preset";
    private static final String KEY_CROSSFADE = "crossfade";
    private static final String KEY_GAPLESS = "gapless";
    private static final String KEY_DEFAULT_QUALITY = "default_quality";
    private static final String KEY_TOTAL_LISTEN_SEC = "total_listen_sec";
    private static final String KEY_SHUFFLE = "shuffle_enabled";
    private static final String KEY_REPEAT_MODE = "repeat_mode"; // 0=none, 1=all, 2=one
    private static final String KEY_LANGUAGE_TAG = "language_tag"; // ""=system or language tag
    private static final String KEY_DOWNLOAD_TREE_URI = "download_tree_uri";
    private static final String KEY_DOWNLOAD_FOLDER_LABEL = "download_folder_label";
    private static final String KEY_SAMPLE_DATA_PURGED = "sample_data_purged_v1";

    private final SharedPreferences prefs;

    public PreferencesManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public int getThemeMode() {
        return prefs.getInt(KEY_THEME_MODE, 0);
    }

    public void setThemeMode(int mode) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
        applyThemeMode(mode);
    }

    public void applySavedTheme() {
        applyThemeMode(getThemeMode());
    }

    public static void applyThemeMode(int mode) {
        if (mode == 1) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (mode == 2) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    public String getThemePresetId() {
        return prefs.getString(KEY_THEME_PRESET, "neumorphic_classic");
    }

    public void setThemePresetId(String presetId) {
        prefs.edit().putString(KEY_THEME_PRESET, presetId != null ? presetId : "neumorphic_classic").apply();
    }

    public int getGlassIntensity() {
        return prefs.getInt(KEY_GLASS_INTENSITY, 55);
    }

    public void setGlassIntensity(int intensity) {
        int clamped = Math.max(15, Math.min(95, intensity));
        prefs.edit().putInt(KEY_GLASS_INTENSITY, clamped).apply();
    }

    public boolean hasCustomUserName() {
        return prefs.contains(KEY_USER_NAME);
    }

    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, "");
    }

    public void setUserName(String name) {
        prefs.edit().putString(KEY_USER_NAME, name).apply();
    }

    public float getPlaybackSpeed() {
        return prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f);
    }

    public void setPlaybackSpeed(float speed) {
        prefs.edit().putFloat(KEY_PLAYBACK_SPEED, speed).apply();
    }

    public boolean isBassBoostEnabled() {
        return prefs.getBoolean(KEY_BASS_BOOST, false);
    }

    public void setBassBoostEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BASS_BOOST, enabled).apply();
    }

    public String getEqPreset() {
        return prefs.getString(KEY_EQ_PRESET, "balanced");
    }

    public void setEqPreset(String preset) {
        prefs.edit().putString(KEY_EQ_PRESET, preset).apply();
    }

    public boolean isCrossfadeEnabled() {
        return prefs.getBoolean(KEY_CROSSFADE, true);
    }

    public void setCrossfadeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CROSSFADE, enabled).apply();
    }

    public boolean isGaplessEnabled() {
        return prefs.getBoolean(KEY_GAPLESS, true);
    }

    public void setGaplessEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_GAPLESS, enabled).apply();
    }

    public String getDefaultQuality() {
        return prefs.getString(KEY_DEFAULT_QUALITY, "320 kbps");
    }

    public void setDefaultQuality(String quality) {
        prefs.edit().putString(KEY_DEFAULT_QUALITY, quality).apply();
    }

    public long getTotalListenSeconds() {
        return prefs.getLong(KEY_TOTAL_LISTEN_SEC, 0L);
    }

    public void addListenSeconds(long delta) {
        long current = getTotalListenSeconds();
        prefs.edit().putLong(KEY_TOTAL_LISTEN_SEC, current + delta).apply();
    }

    public boolean isShuffleEnabled() {
        return prefs.getBoolean(KEY_SHUFFLE, false);
    }

    public void setShuffleEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHUFFLE, enabled).apply();
    }

    public int getRepeatMode() {
        return prefs.getInt(KEY_REPEAT_MODE, 0);
    }

    public void setRepeatMode(int mode) {
        prefs.edit().putInt(KEY_REPEAT_MODE, mode).apply();
    }

    public String getDownloadTreeUri() {
        return prefs.getString(KEY_DOWNLOAD_TREE_URI, "");
    }

    public String getDownloadFolderLabel() {
        return prefs.getString(KEY_DOWNLOAD_FOLDER_LABEL, "");
    }

    public void setDownloadFolder(String treeUri, String label) {
        prefs.edit()
                .putString(KEY_DOWNLOAD_TREE_URI, treeUri != null ? treeUri : "")
                .putString(KEY_DOWNLOAD_FOLDER_LABEL, label != null ? label : "")
                .apply();
    }

    public void clearDownloadFolder() {
        prefs.edit()
                .remove(KEY_DOWNLOAD_TREE_URI)
                .remove(KEY_DOWNLOAD_FOLDER_LABEL)
                .apply();
    }

    public boolean isSampleDataPurged() {
        return prefs.getBoolean(KEY_SAMPLE_DATA_PURGED, false);
    }

    public void markSampleDataPurged() {
        prefs.edit().putBoolean(KEY_SAMPLE_DATA_PURGED, true).apply();
    }

    public String getLanguageTag() {
        return normalizeLanguageTag(prefs.getString(KEY_LANGUAGE_TAG, ""));
    }

    public String getEffectiveLanguageTag() {
        LocaleListCompat appLocales = AppCompatDelegate.getApplicationLocales();
        if (!appLocales.isEmpty()) {
            return normalizeLanguageTag(appLocales.toLanguageTags());
        }
        return getLanguageTag();
    }

    public void setLanguageTag(String tag) {
        String clean = normalizeLanguageTag(tag);
        prefs.edit().putString(KEY_LANGUAGE_TAG, clean).apply();
        applyLanguageTag(clean);
    }

    public void applySavedLanguage() {
        String tag = getLanguageTag();
        if (!tag.isEmpty()) {
            applyLanguageTag(tag);
        }
    }

    public static void applyLanguageTag(String tag) {
        String clean = normalizeLanguageTag(tag);
        LocaleListCompat locales = clean.isEmpty()
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(clean);
        AppCompatDelegate.setApplicationLocales(locales);
    }

    public static String normalizeLanguageTag(String rawTag) {
        if (rawTag == null) return "";
        String trimmed = rawTag.trim();
        if (trimmed.isEmpty()) return "";

        int commaIndex = trimmed.indexOf(',');
        if (commaIndex > 0) {
            trimmed = trimmed.substring(0, commaIndex).trim();
        }
        String lower = trimmed.replace('_', '-').toLowerCase(java.util.Locale.ROOT);
        if (lower.equals("vi") || lower.startsWith("vi-")) return "vi";
        if (lower.equals("en") || lower.startsWith("en-")) return "en";
        if (lower.equals("id") || lower.startsWith("id-") || lower.equals("in") || lower.startsWith("in-")) {
            return "id";
        }
        if (lower.equals("ja") || lower.startsWith("ja-")) return "ja";
        if (lower.equals("ko") || lower.startsWith("ko-")) return "ko";
        if (lower.equals("zh") || lower.equals("zh-cn") || lower.equals("zh-hans") || lower.startsWith("zh-hans-") || lower.startsWith("zh-cn")) {
            return "zh-CN";
        }
        if (lower.equals("fr") || lower.startsWith("fr-")) return "fr";
        if (lower.equals("es") || lower.startsWith("es-")) return "es";
        return trimmed;
    }
}
